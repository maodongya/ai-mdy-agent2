import SwiftUI

struct WorkbenchView: View {
    @State private var settings = AppSettings.shared
    @State private var workspaceVM = WorkspaceViewModel()
    @State private var runVM = RunViewModel()
    @State private var showSettings = false
    @State private var columnVisibility: NavigationSplitViewVisibility = .all

    var body: some View {
        NavigationSplitView(columnVisibility: $columnVisibility) {
            sidebar
        } content: {
            EditorView(path: workspaceVM.selectedPath, content: workspaceVM.fileContent)
                .padding(8)
        } detail: {
            AgentConsoleView(runVM: runVM, threadId: workspaceVM.thread?.threadId)
                .padding(8)
        }
        .navigationSplitViewStyle(.balanced)
        .background(AnvilTheme.background)
        .preferredColorScheme(.dark)
        .tint(AnvilTheme.accent)
        .toolbar {
            ToolbarItem(placement: .navigation) {
                Text("ANVIL")
                    .font(AnvilTheme.title(20))
                    .foregroundStyle(AnvilTheme.accent)
            }
            ToolbarItem(placement: .principal) {
                TextField("Workspace", text: $settings.workspacePath)
                    .font(AnvilTheme.mono(12))
                    .frame(minWidth: 280)
            }
            ToolbarItemGroup(placement: .primaryAction) {
                Picker("Mode", selection: $runVM.mode) {
                    ForEach(Mode.allCases) { m in
                        Text(m.label).tag(m)
                    }
                }
                .pickerStyle(.menu)
                .frame(width: 100)

                TextField("Model", text: $runVM.model)
                    .font(AnvilTheme.mono(11))
                    .frame(width: 160)

                Button {
                    Task { await reconnect() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }

                Button {
                    showSettings = true
                } label: {
                    Image(systemName: "gearshape")
                }
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsView(settings: settings)
        }
        .sheet(item: $runVM.pendingApproval) { approval in
            ApprovalSheet(request: approval) { decision in
                if decision == .allowOnce || decision == .allowSession {
                    runVM.noteWrittenPaths(from: approval.preview)
                }
                runVM.respondApproval(decision)
            }
        }
        .task {
            await settings.checkHealth()
            runVM.model = settings.defaultModel
            await reconnect()
        }
        .onChange(of: runVM.isRunning) { wasRunning, isRunning in
            if wasRunning && !isRunning {
                Task {
                    await workspaceVM.refreshTree()
                    await workspaceVM.reloadSelectedFileIfNeeded(writtenPaths: runVM.writtenPaths)
                    if runVM.writtenPaths.first != nil, workspaceVM.selectedPath == nil {
                        if let path = runVM.writtenPaths.first {
                            await workspaceVM.loadFile(path: path)
                        }
                    }
                }
            }
        }
        .overlay(alignment: .bottomLeading) {
            if let err = workspaceVM.errorMessage {
                Text(err)
                    .font(AnvilTheme.mono(11))
                    .foregroundStyle(AnvilTheme.errorLine)
                    .padding(8)
            }
        }
    }

    private var sidebar: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Files")
                    .font(AnvilTheme.mono(11))
                    .foregroundStyle(AnvilTheme.textSecondary)
                Spacer()
                if workspaceVM.isLoading {
                    ProgressView().controlSize(.mini)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            FileTreeView(
                items: workspaceVM.treeRoots,
                selectedPath: workspaceVM.selectedPath
            ) { path in
                Task { await workspaceVM.loadFile(path: path) }
            }
        }
        .background(AnvilTheme.panel)
    }

    @MainActor
    private func reconnect() async {
        await workspaceVM.connect(workspacePath: settings.workspacePath)
    }
}

#Preview {
    WorkbenchView()
        .frame(width: 1200, height: 800)
}
