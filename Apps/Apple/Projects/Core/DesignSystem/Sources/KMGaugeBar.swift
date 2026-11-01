import SwiftUI

public enum KMGaugeLevel: String, CaseIterable, Sendable {
    case weak = "취약함"
    case moderate = "보통"
    case strong = "초강력"
}

public struct KMGaugeBar: View {
    public let score: Int
    public let showLabel: Bool
    public let animated: Bool

    public init(
        score: Int,
        showLabel: Bool = true,
        animated: Bool = true
    ) {
        self.score = score
        self.showLabel = showLabel
        self.animated = animated
    }

    public var clampedScore: Int {
        return min(max(score, 0), 100)
    }

    public var level: KMGaugeLevel? {
        switch clampedScore {
        case 0...59:
            return .weak
        case 60...79:
            return .moderate
        case 80...100:
            return .strong
        default:
            return .weak
        }
    }

    public var statusText: String {
        return level?.rawValue ?? ""
    }

    public var colorToken: DesignToken.ColorToken {
        switch level {
        case .weak:
            return DesignToken.Colors.destructive
        case .moderate:
            return DesignToken.Colors.primary
        case .strong:
            return DesignToken.Colors.accent
        case .none:
            return DesignToken.Colors.destructive
        }
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if showLabel {
                HStack {
                    Text(statusText)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(colorToken.color())
                    Spacer()
                    Text("\(clampedScore)%")
                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                        .foregroundColor(DesignToken.View.mutedForeground)
                }
            }

            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(DesignToken.View.muted)
                        .frame(height: 6)

                    RoundedRectangle(cornerRadius: 3)
                        .fill(colorToken.color())
                        .frame(
                            width: geometry.size.width * CGFloat(Double(clampedScore) / 100.0),
                            height: 6
                        )
                        .animation(animated ? .easeInOut(duration: 0.3) : nil, value: clampedScore)
                }
            }
            .frame(height: 6)
        }
    }
}
