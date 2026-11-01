import SwiftUI

public final class KMBurnBadgeModel: ObservableObject {
    @Published public var remainingSeconds: Int
    public let totalSeconds: Int
    public var onBurned: (() -> Void)?

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
        // RED STUB: returns 0.0
        return 0.0
    }

    public func tick() {
        // RED STUB: does nothing
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
        // RED STUB: returns 0.0
        return 0.0
    }

    public var body: some View {
        // RED STUB
        EmptyView()
    }
}
