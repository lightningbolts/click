import Foundation

#if canImport(CallKit) && canImport(AVFAudio)
import UIKit
import CallKit
import AVFAudio
import UserNotifications
import ComposeApp

/// Lock-screen / banner fallback when CallKit cannot be shown (malformed VoIP payload, CallKit errors).
enum ClickIncomingCallFallbackNotifier {
    static func present(callerName: String, videoEnabled: Bool) {
        let content = UNMutableNotificationContent()
        content.title = videoEnabled ? "Incoming video call" : "Incoming call"
        content.body = callerName.isEmpty ? "Open Click to answer" : "From \(callerName)"
        content.sound = .default
        if #available(iOS 15.0, *) {
            content.interruptionLevel = .timeSensitive
        }
        let request = UNNotificationRequest(
            identifier: "click.incoming-call.fallback.\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    static func presentGenericIncomingCall() {
        present(callerName: "", videoEnabled: false)
    }
}

private enum ClickNativeCallNotifications {
    static let incoming = Notification.Name("ClickNativeIncomingCall")
    static let end = Notification.Name("ClickNativeEndCall")
    static let answer = Notification.Name("ClickNativeAnswerCall")
}

struct ClickIncomingCallPayload {
    let callId: String
    let connectionId: String
    let roomName: String
    let callerId: String
    let callerName: String
    let calleeId: String
    let calleeName: String
    let videoEnabled: Bool
    let createdAt: Int64

    /// Coerces APNs / PushKit values (String, NSString, NSNumber) so payloads are not dropped on type mismatch.
    private static func string(_ userInfo: [AnyHashable: Any], _ camel: String, _ snake: String) -> String? {
        func coerce(_ raw: Any?) -> String? {
            guard let raw else { return nil }
            if let s = raw as? String {
                let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
                return t.nilIfEmpty
            }
            if let s = raw as? NSString {
                let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
                return (t as String).nilIfEmpty
            }
            if let n = raw as? NSNumber {
                return n.stringValue.nilIfEmpty
            }
            return nil
        }
        return coerce(userInfo[camel]) ?? coerce(userInfo[snake])
    }

    private static func displayName(_ userInfo: [AnyHashable: Any], _ camel: String, _ snake: String) -> String {
        string(userInfo, camel, snake) ?? "Someone"
    }

    private static func int64(_ userInfo: [AnyHashable: Any], _ camel: String, _ snake: String) -> Int64 {
        let v: Any? = userInfo[camel] ?? userInfo[snake]
        if let n = v as? NSNumber { return n.int64Value }
        if let s = v as? String { return Int64(s) ?? 0 }
        return 0
    }

    /// Merges nested `data` / `custom` dictionaries so APNs and VoIP layouts both work.
    static func normalizedUserInfo(_ raw: [AnyHashable: Any]) -> [AnyHashable: Any] {
        var out: [AnyHashable: Any] = [:]
        raw.forEach { out[$0.key] = $0.value }
        if let data = raw["data"] as? [AnyHashable: Any] {
            data.forEach { out[$0.key] = $0.value }
        }
        if let custom = raw["custom"] as? [AnyHashable: Any] {
            custom.forEach { out[$0.key] = $0.value }
        }
        return out
    }

    init?(_ userInfo: [AnyHashable: Any]) {
        let info = Self.normalizedUserInfo(userInfo)
        guard
            let callId = Self.string(info, "callId", "call_id"),
            let connectionId = Self.string(info, "connectionId", "connection_id"),
            let roomName = Self.string(info, "roomName", "room_name"),
            let callerId = Self.string(info, "callerId", "caller_id"),
            let calleeId = Self.string(info, "calleeId", "callee_id")
        else {
            return nil
        }

        self.callId = callId
        self.connectionId = connectionId
        self.roomName = roomName
        self.callerId = callerId
        self.callerName = Self.displayName(info, "callerName", "caller_name")
        self.calleeId = calleeId
        self.calleeName = Self.displayName(info, "calleeName", "callee_name")
        let vidRaw = info["videoEnabled"] ?? info["video_enabled"]
        if let b = vidRaw as? Bool {
            self.videoEnabled = b
        } else if let n = vidRaw as? NSNumber {
            self.videoEnabled = n.boolValue
        } else {
            self.videoEnabled = false
        }
        self.createdAt = Self.int64(info, "createdAt", "created_at")
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}

final class ClickCallKitManager: NSObject, CXProviderDelegate {
    static let shared = ClickCallKitManager()

    private let provider: CXProvider
    private let callController = CXCallController()
    private var observers: [NSObjectProtocol] = []
    private var payloadsByCallId: [String: ClickIncomingCallPayload] = [:]
    private var uuidsByCallId: [String: UUID] = [:]
    private var callIdsByUuid: [UUID: String] = [:]
    private var started = false

    override init() {
        let configuration = CXProviderConfiguration(localizedName: "Click")
        configuration.supportsVideo = true
        configuration.supportedHandleTypes = [.generic]
        configuration.maximumCallsPerCallGroup = 1
        configuration.maximumCallGroups = 1
        configuration.includesCallsInRecents = true
        if #available(iOS 14.0, *) {
            configuration.iconTemplateImageData = UIImage(systemName: "phone.circle.fill")?
                .withRenderingMode(.alwaysTemplate)
                .pngData()
        }
        provider = CXProvider(configuration: configuration)
        super.init()
        provider.setDelegate(self, queue: nil)
    }

    func start() {
        guard !started else { return }
        started = true

        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: ClickNativeCallNotifications.incoming, object: nil, queue: .main) { [weak self] notification in
            guard let payload = ClickIncomingCallPayload(notification.userInfo ?? [:]) else { return }
            self?.reportIncomingCall(payload, voipPushCompletion: nil)
        })
        observers.append(center.addObserver(forName: ClickNativeCallNotifications.end, object: nil, queue: .main) { [weak self] notification in
            guard let callId = notification.userInfo?["callId"] as? String ?? notification.userInfo?["call_id"] as? String else {
                return
            }
            self?.endCall(callId: callId)
        })
        observers.append(center.addObserver(forName: ClickNativeCallNotifications.answer, object: nil, queue: .main) { [weak self] notification in
            guard let callId = notification.userInfo?["callId"] as? String ?? notification.userInfo?["call_id"] as? String else {
                return
            }
            self?.requestAnswerForCall(callId: callId)
        })
    }

    /// In-app Accept must drive CallKit answer; otherwise the native incoming UI stays active and can send End → decline.
    func requestAnswerForCall(callId: String) {
        guard let uuid = uuidsByCallId[callId] else { return }
        let action = CXAnswerCallAction(call: uuid)
        let transaction = CXTransaction(action: action)
        callController.request(transaction) { error in
            if let error {
                print("CallKit request answer failed: \(error.localizedDescription)")
            }
        }
    }

    /// - Parameter voipPushCompletion: When non-nil (PushKit path), **must** be invoked after `reportNewIncomingCall` finishes so iOS can wake the app reliably.
    func reportIncomingCall(_ payload: ClickIncomingCallPayload, voipPushCompletion: (() -> Void)?) {
        if uuidsByCallId[payload.callId] != nil {
            payloadsByCallId[payload.callId] = payload
            voipPushCompletion?()
            return
        }

        let uuid = UUID()
        uuidsByCallId[payload.callId] = uuid
        callIdsByUuid[uuid] = payload.callId
        payloadsByCallId[payload.callId] = payload

        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: payload.callerName)
        update.localizedCallerName = payload.callerName
        update.hasVideo = payload.videoEnabled
        update.supportsHolding = false
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.supportsDTMF = false

        provider.reportNewIncomingCall(with: uuid, update: update) { [weak self] error in
            if let error {
                print("CallKit report incoming call failed: \(error.localizedDescription)")
                self?.clear(callId: payload.callId)
                ClickIncomingCallFallbackNotifier.present(callerName: payload.callerName, videoEnabled: payload.videoEnabled)
            }
            voipPushCompletion?()
        }
    }

    func reportUnparseableIncomingCall(voipPushCompletion: (() -> Void)?) {
        ClickIncomingCallFallbackNotifier.presentGenericIncomingCall()
        voipPushCompletion?()
    }

    func endCall(callId: String) {
        guard let uuid = uuidsByCallId[callId] else { return }
        provider.reportCall(with: uuid, endedAt: Date(), reason: .remoteEnded)
        clear(callId: callId)
    }

    func providerDidReset(_ provider: CXProvider) {
        payloadsByCallId.removeAll()
        uuidsByCallId.removeAll()
        callIdsByUuid.removeAll()
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        guard
            let callId = callIdsByUuid[action.callUUID],
            let payload = payloadsByCallId[callId]
        else {
            action.fail()
            return
        }

        ClickKt.handleIncomingCallPush(
            callId: payload.callId,
            connectionId: payload.connectionId,
            roomName: payload.roomName,
            callerId: payload.callerId,
            callerName: payload.callerName,
            calleeId: payload.calleeId,
            calleeName: payload.calleeName,
            videoEnabled: payload.videoEnabled,
            createdAt: payload.createdAt,
            autoAnswer: true,
            autoDecline: false
        )
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        guard
            let callId = callIdsByUuid[action.callUUID],
            let payload = payloadsByCallId[callId]
        else {
            action.fail()
            return
        }

        ClickKt.handleIncomingCallPush(
            callId: payload.callId,
            connectionId: payload.connectionId,
            roomName: payload.roomName,
            callerId: payload.callerId,
            callerName: payload.callerName,
            calleeId: payload.calleeId,
            calleeName: payload.calleeName,
            videoEnabled: payload.videoEnabled,
            createdAt: payload.createdAt,
            autoAnswer: false,
            autoDecline: true
        )
        clear(callId: callId)
        action.fulfill()
    }

    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        do {
            try audioSession.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetooth, .allowBluetoothA2DP])
            try audioSession.setActive(true)
        } catch {
            print("CallKit audio activation failed: \(error.localizedDescription)")
        }
    }

    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        do {
            try audioSession.setActive(false)
        } catch {
            print("CallKit audio deactivation failed: \(error.localizedDescription)")
        }
    }

    private func clear(callId: String) {
        guard let uuid = uuidsByCallId.removeValue(forKey: callId) else {
            payloadsByCallId.removeValue(forKey: callId)
            return
        }
        payloadsByCallId.removeValue(forKey: callId)
        callIdsByUuid.removeValue(forKey: uuid)
    }
}
#else
final class ClickCallKitManager {
    static let shared = ClickCallKitManager()

    func start() {
    }

    func reportIncomingCall(_ payload: ClickIncomingCallPayload, voipPushCompletion: (() -> Void)?) {
    }

    func reportUnparseableIncomingCall(voipPushCompletion: (() -> Void)?) {
    }
}

struct ClickIncomingCallPayload {
    init?(_ userInfo: [AnyHashable: Any]) {
        return nil
    }
}
#endif