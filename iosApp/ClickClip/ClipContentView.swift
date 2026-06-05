import SwiftUI
import ComposeApp

struct ClipContentView: View {
    let invocationURL: URL?

    var body: some View {
        ClipComposeView(invocationURL: invocationURL)
            .ignoresSafeArea()
    }
}

private struct ClipComposeView: UIViewControllerRepresentable {
    let invocationURL: URL?

    func makeUIViewController(context: Context) -> UIViewController {
        AppClipMainViewControllerKt.AppClipMainViewController(
            invocationUrl: invocationURL?.absoluteString
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
