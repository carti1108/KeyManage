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
            HStack(spacing: DesignToken.Spacing.space2) {
                if isLoading {
                    ProgressView()
                        .controlSize(size == .sm ? .small : .regular)
                } else if let icon = icon {
                    Image(systemName: icon)
                        .font(.system(size: size.fontSize, weight: .medium))
                        .accessibilityHidden(true)
                }

                Text(title)
                    .font(.system(size: size.fontSize, weight: .medium))
            }
            .padding(.horizontal, size.horizontalPadding)
            .frame(height: size.height)
            .background(backgroundColor)
            .foregroundColor(foregroundColor)
            .clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.sm))
            .overlay(
                RoundedRectangle(cornerRadius: DesignToken.Radius.sm)
                    .stroke(borderColor, lineWidth: variant == .outline ? 1 : 0)
            )
            .opacity(isDisabled ? 0.5 : 1.0)
        }
        .buttonStyle(KMButtonStyle(isInteractive: isInteractive))
        .disabled(!isInteractive)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(isLoading ? "\(title), 로딩 중" : title)
        .accessibilityAddTraits(.isButton)
    }

    private struct KMButtonStyle: ButtonStyle {
        let isInteractive: Bool
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed && isInteractive ? 0.8 : 1.0)
                .scaleEffect(configuration.isPressed && isInteractive ? 0.98 : 1.0)
                .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
        }
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
