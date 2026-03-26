import Foundation

#if canImport(PushKit)
import PushKit
import ComposeApp

final class ClickVoipPushManager: NSObject, PKPushRegistryDelegate {
    static let shared = ClickVoipPushManager()

    /// Main queue: PushKit requires `CXProvider.reportNewIncomingCall` to be invoked immediately from this callback (no network/async before reporting).
    private let registry = PKPushRegistry(queue: .main)
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        registry.delegate = self
        registry.desiredPushTypes = [.voIP]
    }

    func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        guard type == .voIP else { return }
        let token = pushCredentials.token.map { String(format: "%02.2hhx", $0) }.joined()
        ClickKt.savePushToken(token: token, platform: "ios", tokenType: "voip")
    }

    func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
    }

    func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType, completion: @escaping () -> Void) {
        guard type == .voIP else {
            completion()
            return
        }

        let dictionaryPayload = payload.dictionaryPayload
        // Caller UUID + name: top-level keys only, before CallKit (no async / network).
        let callerUUID = dictionaryPayload["caller_id"] as? String ?? dictionaryPayload["callerId"] as? String
        let callerName = dictionaryPayload["caller_name"] as? String ?? dictionaryPayload["callerName"] as? String

        guard let parsed = ClickIncomingCallPayload(dictionaryPayload) else {
            ClickCallKitManager.shared.reportUnparseableIncomingCall(voipPushCompletion: completion)
            return
        }

        let resolvedCallerId: String = {
            if let u = callerUUID, !u.isEmpty { return u }
            return parsed.callerId
        }()
        let resolvedCallerName: String = {
            if let n = callerName, !n.isEmpty { return n }
            return parsed.callerName
        }()

        let incoming = ClickIncomingCallPayload(
            callId: parsed.callId,
            connectionId: parsed.connectionId,
            roomName: parsed.roomName,
            callerId: resolvedCallerId,
            callerName: resolvedCallerName,
            calleeId: parsed.calleeId,
            calleeName: parsed.calleeName,
            videoEnabled: parsed.videoEnabled,
            createdAt: parsed.createdAt
        )
        ClickCallKitManager.shared.reportIncomingCall(incoming, voipPushCompletion: completion)
    }
}
#else
final class ClickVoipPushManager {
    static let shared = ClickVoipPushManager()

    func start() {
    }
}
#endif