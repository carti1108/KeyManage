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
        switch self {
        case .sm: return 32.0
        case .md: return 40.0
        case .lg: return 48.0
        }
    }

    public var horizontalPadding: CGFloat {
        switch self {
        case .sm: return 12.0
        case .md: return 16.0
        case .lg: return 20.0
        }
    }

    public var fontSize: CGFloat {
        switch self {
        case .sm: return 13.0
        case .md: return 14.0
        case .lg: return 16.0
        }
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
        return !isDisabled && !isLoading
    }

    public var body: some View {
        Button(action: {
            if isInteractive {
                action()
            }
        }) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .controlSize(size == .sm ? .small : .regular)
                } else if let icon = icon {
                    Image(systemName: icon)
                        .font(.system(size: size.fontSize, weight: .medium))
                }

                Text(title)
                    .font(.system(size: size.fontSize, weight: .medium))
            }
            .padding(.horizontal, size.horizontalPadding)
            .frame(height: size.height)
            .background(backgroundColor)
            .foregroundColor(foregroundColor)
            .cornerRadius(6)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(borderColor, lineWidth: variant == .outline ? 1 : 0)
            )
            .opacity(isDisabled ? 0.5 : 1.0)
        }
        .buttonStyle(.plain)
        .disabled(!isInteractive)
    }

    private var backgroundColor: Color {
        switch variant {
        case .primary: return DesignToken.View.primary
        case .outline: return .clear
        case .destructive: return DesignToken.View.destructive
        case .ghost: return .clear
        }
    }

    private var foregroundColor: Color {
        switch variant {
        case .primary: return DesignToken.View.primaryForeground
        case .outline: return DesignToken.View.foreground
        case .destructive: return DesignToken.View.destructiveForeground
        case .ghost: return DesignToken.View.foreground
        }
    }

    private var borderColor: Color {
        switch variant {
        case .outline: return DesignToken.View.border
        default: return .clear
        }
    }
}
