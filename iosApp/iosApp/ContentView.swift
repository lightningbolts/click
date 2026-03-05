import UIKit
import SwiftUI
import ComposeApp

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



