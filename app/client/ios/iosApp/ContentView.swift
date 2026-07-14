import SwiftUI
import UIKit
import App

/// Hosts the shared Compose UI.
///
/// `MainViewController()` is the Kotlin entry point in `:app:client:common`
/// (`iosMain/.../MainViewController.kt`). It installs Enro navigation on first call and returns a
/// `ComposeUIViewController` wrapping `App()`, so everything below this line is shared code.
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
