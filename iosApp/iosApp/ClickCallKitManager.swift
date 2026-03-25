import Foundation

#if canImport(CallKit) && canImport(AVFAudio)
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

    init?(_ userInfo: [AnyHashable: Any]) {
        guard
            let callId = userInfo["callId"] as? String ?? userInfo["call_id"] as? String,
            let connectionId = userInfo["connectionId"] as? String ?? userInfo["connection_id"] as? String,
            let roomName = userInfo["roomName"] as? String ?? userInfo["room_name"] as? String,
            let callerId = userInfo["callerId"] as? String ?? userInfo["caller_id"] as? String,
            let callerName = userInfo["callerName"] as? String ?? userInfo["caller_name"] as? String,
            let calleeId = userInfo["calleeId"] as? String ?? userInfo["callee_id"] as? String,
            let calleeName = userInfo["calleeName"] as? String ?? userInfo["callee_name"] as? String
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
        self.videoEnabled = (userInfo["videoEnabled"] as? Bool) ?? (userInfo["video_enabled"] as? Bool) ?? false
        self.createdAt = (userInfo["createdAt"] as? NSNumber)?.int64Value
            ?? (userInfo["created_at"] as? NSNumber)?.int64Value
            ?? 0
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
            self?.reportIncomingCall(payload)
        })
        observers.append(center.addObserver(forName: ClickNativeCallNotifications.end, object: nil, queue: .main) { [weak self] notification in
            guard let callId = notification.userInfo?["callId"] as? String ?? notification.userInfo?["call_id"] as? String else {
                return
            }
            self?.endCall(callId: callId)
        })
    }

    func reportIncomingCall(_ payload: ClickIncomingCallPayload) {
        if uuidsByCallId[payload.callId] != nil {
            payloadsByCallId[payload.callId] = payload
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

        provider.reportNewIncomingCall(with: uuid, update: update) { error in
            if let error {
                print("CallKit report incoming call failed: \(error.localizedDescription)")
                self.clear(callId: payload.callId)
            }
        }
    }

    func reportUnparseableIncomingCall() {
        let uuid = UUID()
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: "Unknown")
        update.localizedCallerName = "Unknown"
        update.hasVideo = false

        provider.reportNewIncomingCall(with: uuid, update: update) { [weak self] _ in
            self?.provider.reportCall(with: uuid, endedAt: Date(), reason: .failed)
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

    func reportIncomingCall(_ payload: ClickIncomingCallPayload) {
    }
}

struct ClickIncomingCallPayload {
    init?(_ userInfo: [AnyHashable: Any]) {
        return nil
    }
}
#endif