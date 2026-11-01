import SwiftUI

public enum KMCardPadding: String, CaseIterable, Sendable {
    case sm
    case md
    case lg

    public var value: CGFloat {
        // RED STUB: minimal stub returns 0
        return 0
    }
}

public struct KMCard<Content: View>: View {
    public let padding: KMCardPadding
    public let isInteractive: Bool
    private let content: () -> Content

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
        // RED STUB
        EmptyView()
    }
}
