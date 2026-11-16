import SwiftUI

@MainActor
public final class KMBurnBadgeModel: ObservableObject {
    @Published public var remainingSeconds: Int
    public let totalSeconds: Int
    public var onBurned: (() -> Void)?
    private var hasBurned: Bool = false

    public init(
        remainingSeconds: Int = 30,
        totalSeconds: Int = 30,
        onBurned: (() -> Void)? = nil
    ) {
        self.remainingSeconds = remainingSeconds
        self.totalSeconds = totalSeconds
        self.onBurned = onBurned
    }

    public var progress: Double {
        return Double(max(0, remainingSeconds)) / Double(max(1, totalSeconds))
    }

    public func tick() {
        if remainingSeconds > 0 {
            remainingSeconds -= 1
        }
        if remainingSeconds == 0 && !hasBurned {
            hasBurned = true
            onBurned?()
        }
    }
}

public struct KMBurnBadge: View {
    public let remainingSeconds: Int
    public let totalSeconds: Int
    public let onBurned: (() -> Void)?

    public init(
        remainingSeconds: Int,
        totalSeconds: Int = 30,
        onBurned: (() -> Void)? = nil
    ) {
        self.remainingSeconds = remainingSeconds
        self.totalSeconds = totalSeconds
        self.onBurned = onBurned
    }

    public var progress: Double {
        return Double(max(0, remainingSeconds)) / Double(max(1, totalSeconds))
    }

    public var body: some View {
        HStack(spacing: 6) {
            ZStack {
                Circle()
                    .stroke(DesignToken.View.muted, lineWidth: 2)
                    .frame(width: 16, height: 16)

                Circle()
                    .trim(from: 0, to: CGFloat(min(max(progress, 0), 1)))
                    .stroke(
                        remainingSeconds <= 5 ? DesignToken.View.destructive : DesignToken.View.accent,
                        style: StrokeStyle(lineWidth: 2, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
                    .frame(width: 16, height: 16)
            }

            Text("\(max(0, remainingSeconds))s")
                .font(DesignToken.Typography.captionMono.weight(.semibold))
                .foregroundColor(remainingSeconds <= 5 ? DesignToken.View.destructive : DesignToken.View.foreground)
        }
        .padding(.horizontal, DesignToken.Spacing.space2)
        .padding(.vertical, DesignToken.Spacing.space1)
        .background(DesignToken.View.card)
        .clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.lg))
        .overlay(
            RoundedRectangle(cornerRadius: DesignToken.Radius.lg)
                .stroke(DesignToken.View.border, lineWidth: 1)
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("클립보드 자동 소각")
        .accessibilityValue("\(max(0, remainingSeconds))초 남음")
    }
}
