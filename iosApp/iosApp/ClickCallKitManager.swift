import Foundation

#if canImport(CallKit) && canImport(AVFAudio)
import UIKit
import CallKit
import AVFAudio
import ComposeApp

private enum ClickNativeCallNotifications {
    static let incoming = Notification.Name("ClickNativeIncomingCall")
    static let end = Notification.Name("ClickNativeEndCall")
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

    private static func string(_ userInfo: [AnyHashable: Any], _ camel: String, _ snake: String) -> String? {
        (userInfo[camel] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            ?? (userInfo[snake] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
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
            let callerName = Self.string(info, "callerName", "caller_name"),
            let calleeId = Self.string(info, "calleeId", "callee_id"),
            let calleeName = Self.string(info, "calleeName", "callee_name")
        else {
            return nil
        }

        self.callId = callId
        self.connectionId = connectionId
        self.roomName = roomName
        self.callerId = callerId
        self.callerName = callerName
        self.calleeId = calleeId
        self.calleeName = calleeName
        self.videoEnabled = (info["videoEnabled"] as? Bool) ?? (info["video_enabled"] as? Bool) ?? false
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
            }
            voipPushCompletion?()
        }
    }

    func reportUnparseableIncomingCall(voipPushCompletion: (() -> Void)?) {
        let uuid = UUID()
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: "Unknown")
        update.localizedCallerName = "Unknown"
        update.hasVideo = false

        provider.reportNewIncomingCall(with: uuid, update: update) { [weak self] _ in
            self?.provider.reportCall(with: uuid, endedAt: Date(), reason: .failed)
            voipPushCompletion?()
        }
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