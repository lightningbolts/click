import Foundation
import PushKit
import ComposeApp

final class ClickVoipPushManager: NSObject, PKPushRegistryDelegate {
    static let shared = ClickVoipPushManager()

    /// Main queue: PushKit requires `CXProvider.reportNewIncomingCall` to be invoked immediately
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
        
        // 1. Cache it natively so we survive the Kotlin race condition
        UserDefaults.standard.set(token, forKey: "cached_voip_token")
        
        // 2. Try saving it immediately (may fail if Kotlin isn't awake yet)
        ClickKt.savePushToken(token: token, platform: "ios", tokenType: "voip")
    }

    func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
        UserDefaults.standard.removeObject(forKey: "cached_voip_token")
    }

    func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType, completion: @escaping () -> Void) {
        guard type == .voIP else {
            completion()
            return
        }

        let dictionaryPayload = payload.dictionaryPayload
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