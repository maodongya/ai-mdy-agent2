import Foundation

@Observable
final class WorkspaceViewModel {
    var thread: AnvilThread?
    var nodes: [WorkspaceNode] = []
    var selectedPath: String?
    var fileContent: String = ""
    var isLoading = false
    var errorMessage: String?

    private var client: AnvilClient { AppSettings.shared.client }

    @MainActor
    func connect(workspacePath: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            thread = try await client.createThread(root: workspacePath)
            await refreshTree()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    @MainActor
    func refreshTree() async {
        guard let thread else { return }
        do {
            let tree = try await client.workspaceTree(threadId: thread.threadId)
            nodes = tree.nodes.sorted { $0.path < $1.path }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    @MainActor
    func loadFile(path: String) async {
        guard let thread else { return }
        selectedPath = path
        do {
            let file = try await client.workspaceFile(threadId: thread.threadId, path: path)
            fileContent = file.content
        } catch {
            fileContent = "// Error loading file: \(error.localizedDescription)"
        }
    }

    @MainActor
    func reloadSelectedFileIfNeeded(writtenPaths: [String]) async {
        guard let selectedPath else { return }
        if writtenPaths.contains(where: { selectedPath.hasSuffix($0) || $0.hasSuffix(selectedPath) }) {
            await loadFile(path: selectedPath)
        }
    }

    var treeRoots: [FileTreeItem] {
        FileTreeBuilder.build(from: nodes)
    }
}

struct FileTreeItem: Identifiable, Hashable {
    let name: String
    let path: String
    let isDirectory: Bool
    var children: [FileTreeItem]

    var id: String { path }
}

enum FileTreeBuilder {
    static func build(from nodes: [WorkspaceNode]) -> [FileTreeItem] {
        var map: [String: FileTreeItem] = [:]
        for node in nodes.sorted(by: { $0.path < $1.path }) {
            let parts = node.path.split(separator: "/").map(String.init)
            var accumulated = ""
            for (index, part) in parts.enumerated() {
                accumulated = accumulated.isEmpty ? part : "\(accumulated)/\(part)"
                let isLast = index == parts.count - 1
                if map[accumulated] == nil {
                    map[accumulated] = FileTreeItem(
                        name: part,
                        path: accumulated,
                        isDirectory: isLast ? node.isDirectory : true,
                        children: []
                    )
                }
            }
        }

        var roots: [FileTreeItem] = []
        for path in map.keys.sorted() {
            guard let item = map[path] else { continue }
            if let slash = path.lastIndex(of: "/") {
                let parentPath = String(path[..<slash])
                if var parent = map[parentPath] {
                    if !parent.children.contains(where: { $0.path == path }) {
                        parent.children.append(item)
                        parent.children.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
                        map[parentPath] = parent
                    }
                }
            } else {
                roots.append(item)
            }
        }
        return roots.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }
}
