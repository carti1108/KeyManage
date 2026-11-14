import SwiftUI

public enum KMCardPadding: String, CaseIterable, Sendable {
    case sm
    case md
    case lg

    public var value: CGFloat {
        switch self {
        case .sm: return 12.0
        case .md: return 16.0
        case .lg: return 20.0
        }
    }
}

public struct KMCard<Content: View>: View {
    public let padding: KMCardPadding
    public let isInteractive: Bool
    private let content: () -> Content

    @State private var isHovered: Bool = false
    @State private var isPressed: Bool = false

    public init(
        padding: KMCardPadding = .md,
        isInteractive: Bool = false,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.padding = padding
        self.isInteractive = isInteractive
        self.content = content
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content()
        }
        .padding(padding.value)
        .background(DesignToken.View.card)
        .clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.md))
        .overlay(
            RoundedRectangle(cornerRadius: DesignToken.Radius.md)
                .stroke(DesignToken.View.border, lineWidth: 1)
        )
        .scaleEffect(isInteractive ? (isPressed ? 0.99 : (isHovered ? 1.01 : 1.0)) : 1.0)
        .animation(.easeOut(duration: 0.15), value: isHovered)
        .animation(.easeOut(duration: 0.1), value: isPressed)
        .onHover { hovering in
            if isInteractive {
                isHovered = hovering
            }
        }
    }
}
