import SwiftUI

struct AgentConsoleView: View {
    @Bindable var runVM: RunViewModel
    let threadId: String?
    @FocusState private var inputFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 4) {
                        ForEach(runVM.lines) { line in
                            consoleLine(line)
                                .id(line.id)
                                .transition(.opacity)
                        }
                    }
                    .padding(10)
                }
                .onChange(of: runVM.lines.count) { _, _ in
                    if let last = runVM.lines.last {
                        withAnimation(.easeOut(duration: 0.15)) {
                            proxy.scrollTo(last.id, anchor: .bottom)
                        }
                    }
                }
            }
            .frame(maxHeight: .infinity)
            .background(AnvilTheme.background)

            Divider().overlay(AnvilTheme.border)

            HStack(spacing: 8) {
                Text(">")
                    .font(AnvilTheme.mono(14))
                    .foregroundStyle(AnvilTheme.accent)
                TextField("Ask Anvil…", text: $runVM.inputText)
                    .textFieldStyle(.plain)
                    .font(AnvilTheme.mono(13))
                    .focused($inputFocused)
                    .onSubmit { submit() }
                if runVM.isRunning {
                    ProgressView().controlSize(.small)
                }
                Button("Run") { submit() }
                    .disabled(runVM.isRunning || threadId == nil)
            }
            .padding(10)
            .background(AnvilTheme.panel)
        }
        .anvilPanel()
    }

    private func submit() {
        guard let threadId else { return }
        runVM.submit(threadId: threadId)
    }

    @ViewBuilder
    private func consoleLine(_ line: ConsoleLine) -> some View {
        let color: Color = switch line.kind {
        case .message: AnvilTheme.textPrimary
        case .tool: AnvilTheme.toolLine
        case .approval: AnvilTheme.accent
        case .error: AnvilTheme.errorLine
        case .system: AnvilTheme.textSecondary
        }
        Text(line.text)
            .font(AnvilTheme.mono(12))
            .foregroundStyle(color)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
