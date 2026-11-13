import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            // The shared Compose UI handles safe areas and the keyboard itself.
            ContentView().ignoresSafeArea()
        }
    }
}
