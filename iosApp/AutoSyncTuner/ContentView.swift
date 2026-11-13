import SwiftUI
import UIKit
import Shared

/// Hosts the shared Compose Multiplatform UI (shared/src/iosMain/.../Platform.ios.kt).
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        Platform_iosKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
