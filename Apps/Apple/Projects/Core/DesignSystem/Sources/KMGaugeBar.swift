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
        // RED STUB: returns 0
        return 0
    }

    public var level: KMGaugeLevel? {
        // RED STUB: returns nil
        return nil
    }

    public var statusText: String {
        // RED STUB: returns empty string
        return ""
    }

    public var colorToken: DesignToken.ColorToken {
        // RED STUB: returns empty color token
        return DesignToken.ColorToken()
    }

    public var body: some View {
        // RED STUB
        EmptyView()
    }
}
