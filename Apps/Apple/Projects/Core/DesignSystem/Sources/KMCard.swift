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
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(DesignToken.View.border, lineWidth: 1)
        )
        .scaleEffect(isInteractive && isHovered ? 1.01 : 1.0)
        .animation(.easeOut(duration: 0.15), value: isHovered)
        .onHover { hovering in
            if isInteractive {
                isHovered = hovering
            }
        }
    }
}
