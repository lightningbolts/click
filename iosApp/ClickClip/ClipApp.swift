import SwiftUI

@main
struct ClickClipApp: App {
    @State private var invocationURL: URL?

    var body: some Scene {
        WindowGroup {
            ClipContentView(invocationURL: invocationURL)
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        invocationURL = url
                    }
                }
                .onOpenURL { url in
                    invocationURL = url
                }
        }
    }
}
