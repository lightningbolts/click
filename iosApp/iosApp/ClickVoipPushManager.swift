import Foundation

#if canImport(PushKit)
import PushKit
import ComposeApp

final class ClickVoipPushManager: NSObject, PKPushRegistryDelegate {
    static let shared = ClickVoipPushManager()

    private let registry = PKPushRegistry(queue: DispatchQueue.main)
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
        reportCallFromPush(payload.dictionaryPayload, type: type)
        completion()
    }

    func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType) {
        reportCallFromPush(payload.dictionaryPayload, type: type)
    }

    private func reportCallFromPush(_ userInfo: [AnyHashable: Any], type: PKPushType) {
        guard type == .voIP else { return }

        if let payload = ClickIncomingCallPayload(userInfo) {
            ClickCallKitManager.shared.reportIncomingCall(payload)
        } else {
            ClickCallKitManager.shared.reportUnparseableIncomingCall()
        }
    }
}
#else
final class ClickVoipPushManager {
    static let shared = ClickVoipPushManager()

    func start() {
    }
}
#endif