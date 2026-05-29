import Foundation
import UIKit

private enum ClickGoogleSignInNotifications {
    static let start = Notification.Name("ClickGoogleSignInStart")
    static let didComplete = Notification.Name("ClickGoogleSignInDidComplete")
}

#if canImport(GoogleSignIn)
import GoogleSignIn

@MainActor
final class ClickGoogleSignInBridge: NSObject {
    static let shared = ClickGoogleSignInBridge()

    private var observer: NSObjectProtocol?
    private var started = false

    func start() {
        guard !started else { return }
        started = true

        let webClientId = "113180501985-62rus3q9pfa1ksspocjvn7j9c0oqp8fo.apps.googleusercontent.com"
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: webClientId)

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
        guard let root = UIApplication.sharedApplication.keyWindow?.rootViewController else {
            postFailure("Could not present Google sign-in.")
            return
        }

        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: root)
            let token = result.user.idToken?.tokenString.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if token.isEmpty {
                postFailure("Google ID token was unavailable.")
                return
            }
            NotificationCenter.default.post(
                name: ClickGoogleSignInNotifications.didComplete,
                object: nil,
                userInfo: ["idToken": token]
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
                userInfo: ["error": "Google Sign-In SDK is not linked. Add GoogleSignIn-iOS via Swift Package Manager in Xcode."]
            )
        }
    }

    func handleOpenURL(_ url: URL) -> Bool { false }
}
#endif
