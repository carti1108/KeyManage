import SwiftUI

public enum KMButtonVariant: String, CaseIterable, Sendable {
    case primary
    case outline
    case destructive
    case ghost
}

public enum KMButtonSize: String, CaseIterable, Sendable {
    case sm
    case md
    case lg

    public var height: CGFloat {
        // RED STUB: minimal stub returns 0
        return 0
    }
}

public struct KMButton: View {
    public let title: String
    public let variant: KMButtonVariant
    public let size: KMButtonSize
    public let icon: String?
    public let isLoading: Bool
    public let isDisabled: Bool
    public let action: () -> Void

    public init(
        _ title: String,
        variant: KMButtonVariant = .primary,
        size: KMButtonSize = .md,
        icon: String? = nil,
        isLoading: Bool = false,
        isDisabled: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.variant = variant
        self.size = size
        self.icon = icon
        self.isLoading = isLoading
        self.isDisabled = isDisabled
        self.action = action
    }

    public var isInteractive: Bool {
        // RED STUB: returns true even when disabled or loading
        return true
    }

    public var body: some View {
        // RED STUB
        EmptyView()
    }
}
