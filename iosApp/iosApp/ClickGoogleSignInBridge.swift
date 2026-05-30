import Foundation
import UIKit
import Security

private enum ClickGoogleSignInNotifications {
    static let start = Notification.Name("ClickGoogleSignInStart")
    static let didComplete = Notification.Name("ClickGoogleSignInDidComplete")
}

#if canImport(GoogleSignIn)
import GoogleSignIn
import CryptoKit

@MainActor
final class ClickGoogleSignInBridge: NSObject {
    static let shared = ClickGoogleSignInBridge()

    private var observer: NSObjectProtocol?
    private var started = false

    func start() {
        guard !started else { return }
        started = true

        let iosClientId = (Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let webClientId = (Bundle.main.object(forInfoDictionaryKey: "GIDServerClientID") as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? "530817233802-3ki7usecs885vvag9uq92ubu5hgkv2sp.apps.googleusercontent.com"
        guard !iosClientId.isEmpty else {
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(
            clientID: iosClientId,
            serverClientID: webClientId
        )

        observer = NotificationCenter.default.addObserver(
            forName: ClickGoogleSignInNotifications.start,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                await self?.handleStart()
            }
        }
    }

    private func handleStart() async {
        guard let root = Self.topPresentingViewController() else {
            postFailure("Could not present Google sign-in.")
            return
        }

        do {
            let rawNonce = Self.randomNonceString()
            let hashedNonce = Self.sha256Hex(rawNonce)
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: root,
                hint: nil,
                additionalScopes: nil,
                nonce: hashedNonce
            )
            let token = result.user.idToken?.tokenString.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if token.isEmpty {
                postFailure("Google ID token was unavailable.")
                return
            }
            let accessToken = result.user.accessToken.tokenString.trimmingCharacters(in: .whitespacesAndNewlines)
            var userInfo: [String: Any] = [
                "idToken": token,
                "nonce": rawNonce,
            ]
            if !accessToken.isEmpty {
                userInfo["accessToken"] = accessToken
            }
            NotificationCenter.default.post(
                name: ClickGoogleSignInNotifications.didComplete,
                object: nil,
                userInfo: userInfo
            )
        } catch {
            let message = error.localizedDescription
            if message.lowercased().contains("cancel") {
                postFailure("Google sign-in was canceled.")
            } else {
                postFailure(message)
            }
        }
    }

    private func postFailure(_ message: String) {
        NotificationCenter.default.post(
            name: ClickGoogleSignInNotifications.didComplete,
            object: nil,
            userInfo: ["error": message]
        )
    }

    func handleOpenURL(_ url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    private static func topPresentingViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive || $0.activationState == .foregroundInactive }
        let window = scenes.flatMap(\.windows).first(where: \.isKeyWindow)
            ?? scenes.flatMap(\.windows).first
        var controller = window?.rootViewController
        while let presented = controller?.presentedViewController {
            controller = presented
        }
        return controller
    }

    private static func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        precondition(status == errSecSuccess, "Failed to generate secure nonce.")
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    private static func sha256Hex(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
#else
@MainActor
final class ClickGoogleSignInBridge: NSObject {
    static let shared = ClickGoogleSignInBridge()

    private var observer: NSObjectProtocol?
    private var started = false

    func start() {
        guard !started else { return }
        started = true

        observer = NotificationCenter.default.addObserver(
            forName: ClickGoogleSignInNotifications.start,
            object: nil,
            queue: .main
        ) { _ in
            NotificationCenter.default.post(
                name: ClickGoogleSignInNotifications.didComplete,
                object: nil,
                userInfo: ["error": "Native Google sign-in is not configured for iOS. Use browser sign-in."]
            )
        }
    }

    func handleOpenURL(_ url: URL) -> Bool { false }
}
#endif
