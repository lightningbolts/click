import UIKit
import SwiftUI
import ComposeApp

private func communityHubId(from url: URL) -> String? {
    if url.scheme?.lowercased() == "click", url.host?.lowercased() == "hub" {
        let path = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if path.isEmpty { return nil }
        if path.contains("/") { return nil }
        return path
    }
    if url.scheme?.lowercased() == "https" || url.scheme?.lowercased() == "http" {
        guard url.host?.lowercased() == "click-us.vercel.app" else { return nil }
        let parts = url.path.split(separator: "/").map(String.init)
        guard parts.count >= 2, parts[0].lowercased() == "hub" else { return nil }
        let id = parts[1]
        return id.isEmpty ? nil : id
    }
    return nil
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .onOpenURL { url in
                if let hubId = communityHubId(from: url) {
                    ClickKt.setCommunityHubDeepLink(hubId: hubId)
                }
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 20, coordinateSpace: .local)
                    .onEnded { value in
                        let startsNearLeftEdge = value.startLocation.x <= 24
                        let isMostlyHorizontal = abs(value.translation.height) < 60
                        let swipedRightEnough = value.translation.width > 80

                        if startsNearLeftEdge && isMostlyHorizontal && swipedRightEnough {
                            NotificationCenter.default.post(name: NSNotification.Name("ClickIOSBackSwipe"), object: nil)
                        }
                    }
            )
    }
}



