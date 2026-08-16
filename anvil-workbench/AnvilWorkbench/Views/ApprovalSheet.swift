import SwiftUI

struct ApprovalSheet: View {
    let request: ApprovalRequest
    let onDecision: (ApprovalDecision) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Approval Required")
                .font(AnvilTheme.title(18))
                .foregroundStyle(AnvilTheme.textPrimary)

            Group {
                labeled("Tool", request.tool)
                labeled("Risk", request.risk)
                labeled("Summary", request.summary)
                if let paths = request.preview["paths"]?.arrayValue {
                    labeled("Paths", paths.compactMap(\.stringValue).joined(separator: ", "))
                }
                if let command = request.preview["command"]?.stringValue {
                    labeled("Command", command)
                }
            }
            .font(AnvilTheme.mono(12))

            HStack {
                Button("Deny") {
                    onDecision(.deny)
                    dismiss()
                }
                Button("Always Deny") {
                    onDecision(.alwaysDeny)
                    dismiss()
                }
                Spacer()
                Button("Allow Session") {
                    onDecision(.allowSession)
                    dismiss()
                }
                Button("Allow Once") {
                    onDecision(.allowOnce)
                    dismiss()
                }
                .keyboardShortcut(.defaultAction)
            }
            .buttonStyle(.borderedProminent)
            .tint(AnvilTheme.accent)
        }
        .padding(24)
        .frame(width: 480)
        .background(AnvilTheme.panel)
    }

    private func labeled(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title.uppercased())
                .font(AnvilTheme.mono(10))
                .foregroundStyle(AnvilTheme.textSecondary)
            Text(value)
                .foregroundStyle(AnvilTheme.textPrimary)
        }
    }
}
