import SwiftUI
import UIKit
import UserNotifications
import ComposeApp

private let clickNotificationPrefsSuite = "click_auth_prefs"
private let clickRequestPushPermissionNotification = Notification.Name("ClickRequestNotificationPermission")
private let clickRuntimeMessageNotificationsKey = "runtime_message_notifications_enabled"
private let clickRuntimeCallNotificationsKey = "runtime_call_notifications_enabled"
private let clickRuntimeActiveChatIdKey = "runtime_active_chat_id"

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    private var requestPermissionObserver: NSObjectProtocol?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        ClickLiveKitBridge.shared.start()
        ClickCallKitManager.shared.start()
        ClickVoipPushManager.shared.start()

        // Incoming call from notification tap / cold start: report CallKit immediately (no async deferral).
        if let remote = launchOptions?[.remoteNotification] as? [AnyHashable: Any] {
            _ = handleIncomingCallNotification(remote)
        }

        let notificationCenter = UNUserNotificationCenter.current()
        notificationCenter.delegate = self
        requestPermissionObserver = NotificationCenter.default.addObserver(
            forName: clickRequestPushPermissionNotification,
            object: nil,
            queue: .main
        ) { _ in
            notificationCenter.requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
                if let error {
                    print("APNs auth request failed: \(error.localizedDescription)")
                    return
                }

                guard granted else {
                    print("APNs auth request denied")
                    return
                }

                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }

        notificationCenter.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral:
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            default:
                break
            }
        }

        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral:
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            default:
                break
            }
        }
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        ClickKt.savePushToken(token: token, platform: "ios", tokenType: "standard")
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("APNs registration failed: \(error.localizedDescription)")
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        if handleIncomingCallNotification(userInfo) {
            completionHandler([])
            return
        }

        guard messageNotificationsEnabled else {
            completionHandler([])
            return
        }

        if let chatId = userInfo["chat_id"] as? String, activeChatId == chatId {
            completionHandler([])
            return
        }

        completionHandler([.banner, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        if let chatId = userInfo["chat_id"] as? String, !chatId.isEmpty {
            ClickKt.setChatDeepLink(chatId: chatId)
        } else if let connectionId = userInfo["connection_id"] as? String, !connectionId.isEmpty {
            ClickKt.setChatDeepLink(chatId: connectionId)
        }
        completionHandler()
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable : Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        if handleIncomingCallNotification(userInfo) {
            completionHandler(.newData)
        } else {
            completionHandler(.noData)
        }
    }

    private var runtimeDefaults: UserDefaults {
        UserDefaults(suiteName: clickNotificationPrefsSuite) ?? .standard
    }

    private var messageNotificationsEnabled: Bool {
        if runtimeDefaults.object(forKey: clickRuntimeMessageNotificationsKey) == nil {
            return true
        }
        return runtimeDefaults.bool(forKey: clickRuntimeMessageNotificationsKey)
    }

    private var callNotificationsEnabled: Bool {
        if runtimeDefaults.object(forKey: clickRuntimeCallNotificationsKey) == nil {
            return true
        }
        return runtimeDefaults.bool(forKey: clickRuntimeCallNotificationsKey)
    }

    private var activeChatId: String? {
        runtimeDefaults.string(forKey: clickRuntimeActiveChatIdKey)
    }

    private func handleIncomingCallNotification(_ userInfo: [AnyHashable: Any]) -> Bool {
        guard callNotificationsEnabled else { return false }
        guard let type = userInfo["type"] as? String, type == "incoming_call" else { return false }
            guard let payload = ClickIncomingCallPayload(userInfo) else { return false }

        ClickCallKitManager.shared.reportIncomingCall(payload, voipPushCompletion: nil)
        return true
    }
}