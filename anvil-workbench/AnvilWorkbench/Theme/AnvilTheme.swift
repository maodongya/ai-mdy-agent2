import SwiftUI

enum AnvilTheme {
    static let background = Color(red: 0.07, green: 0.08, blue: 0.10)
    static let panel = Color(red: 0.10, green: 0.11, blue: 0.15)
    static let border = Color(red: 0.18, green: 0.20, blue: 0.26)
    static let accent = Color(red: 0.24, green: 0.61, blue: 0.94)
    static let textPrimary = Color(red: 0.90, green: 0.92, blue: 0.95)
    static let textSecondary = Color(red: 0.55, green: 0.60, blue: 0.68)
    static let toolLine = Color(red: 0.75, green: 0.55, blue: 0.30)
    static let errorLine = Color(red: 0.95, green: 0.35, blue: 0.35)

    static func mono(_ size: CGFloat = 13) -> Font {
        .system(size: size, weight: .regular, design: .monospaced)
    }

    static func title(_ size: CGFloat = 22) -> Font {
        .system(size: size, weight: .bold, design: .monospaced)
    }
}

struct AnvilPanelStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(AnvilTheme.panel)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(AnvilTheme.border, lineWidth: 1)
            )
    }
}

extension View {
    func anvilPanel() -> some View {
        modifier(AnvilPanelStyle())
    }
}
