import SwiftUI

struct SettingsView: View {
    @Bindable var settings: AppSettings
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Form {
            Section("App Server") {
                TextField("Base URL", text: $settings.serverURLString)
                    .font(AnvilTheme.mono(12))
                HStack {
                    Circle()
                        .fill(settings.isConnected ? Color.green : Color.red)
                        .frame(width: 8, height: 8)
                    Text(settings.isConnected ? "Connected · protocol \(settings.protocolVersion)" : "Disconnected")
                        .font(AnvilTheme.mono(11))
                    Spacer()
                    Button("Check") {
                        Task { await settings.checkHealth() }
                    }
                }
                if let err = settings.lastError {
                    Text(err).foregroundStyle(AnvilTheme.errorLine).font(AnvilTheme.mono(11))
                }
            }

            Section("Workspace") {
                TextField("Root path", text: $settings.workspacePath)
                    .font(AnvilTheme.mono(12))
            }

            Section("Defaults") {
                TextField("Model", text: $settings.defaultModel)
                    .font(AnvilTheme.mono(12))
            }
        }
        .formStyle(.grouped)
        .frame(width: 440, height: 320)
        .padding()
        .onAppear {
            Task { await settings.checkHealth() }
        }
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Done") { dismiss() }
            }
        }
    }
}
