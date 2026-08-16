import SwiftUI

struct FileTreeView: View {
    let items: [FileTreeItem]
    let selectedPath: String?
    let onSelect: (String) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 2) {
                ForEach(items) { item in
                    FileTreeRow(item: item, selectedPath: selectedPath, depth: 0, onSelect: onSelect)
                }
            }
            .padding(8)
        }
    }
}

private struct FileTreeRow: View {
    let item: FileTreeItem
    let selectedPath: String?
    let depth: Int
    let onSelect: (String) -> Void
    @State private var expanded = true

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Button {
                if item.isDirectory {
                    expanded.toggle()
                } else {
                    onSelect(item.path)
                }
            } label: {
                HStack(spacing: 6) {
                    if item.isDirectory {
                        Image(systemName: expanded ? "chevron.down" : "chevron.right")
                            .font(.system(size: 9))
                            .foregroundStyle(AnvilTheme.textSecondary)
                    } else {
                        Image(systemName: "doc.text")
                            .font(.system(size: 10))
                    }
                    Text(item.name)
                        .font(AnvilTheme.mono(12))
                        .foregroundStyle(
                            selectedPath == item.path ? AnvilTheme.accent : AnvilTheme.textPrimary
                        )
                }
                .padding(.leading, CGFloat(depth * 12))
            }
            .buttonStyle(.plain)

            if item.isDirectory && expanded {
                ForEach(item.children) { child in
                    FileTreeRow(item: child, selectedPath: selectedPath, depth: depth + 1, onSelect: onSelect)
                }
            }
        }
    }
}

struct EditorView: View {
    let path: String?
    let content: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(path ?? "No file selected")
                    .font(AnvilTheme.mono(11))
                    .foregroundStyle(AnvilTheme.textSecondary)
                Spacer()
                Text("read-only")
                    .font(AnvilTheme.mono(10))
                    .foregroundStyle(AnvilTheme.textSecondary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(AnvilTheme.panel)

            ScrollView {
                Text(content.isEmpty ? "// Select a file from the sidebar" : content)
                    .font(AnvilTheme.mono(13))
                    .foregroundStyle(AnvilTheme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .textSelection(.enabled)
            }
            .background(AnvilTheme.background)
        }
        .anvilPanel()
    }
}
