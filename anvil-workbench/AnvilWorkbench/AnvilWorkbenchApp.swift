import SwiftUI

@main
struct AnvilWorkbenchApp: App {
    var body: some Scene {
        WindowGroup {
            WorkbenchView()
                .frame(minWidth: 960, minHeight: 640)
        }
        .windowStyle(.titleBar)
        .commands {
            CommandGroup(replacing: .newItem) {}
        }
    }
}
