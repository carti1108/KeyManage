import SwiftUI

/// 입력창 외곽. 컴포넌트가 아니라 시스템 TextField·SecureField에 씌우는 모양이다.
/// iPhone 목업은 테두리 없는 회색 채움(높이 48), macOS는 테두리 있는 카드색 입력창을 쓴다.
private struct KMFieldContainer: ViewModifier {
    #if os(iOS)
    static let height: CGFloat = 48
    #else
    static let height: CGFloat = 40
    #endif

    func body(content: Content) -> some View {
        content
            .padding(.horizontal, DesignToken.Spacing.space3)
            .frame(height: Self.height)
            #if os(iOS)
            .background(DesignToken.View.muted)
            .clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.lg))
            #else
            .background(DesignToken.View.card)
            .clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.sm))
            .overlay(
                RoundedRectangle(cornerRadius: DesignToken.Radius.sm)
                    .stroke(DesignToken.View.input, lineWidth: 1)
            )
            #endif
    }
}

public extension View {
    func kmFieldContainer() -> some View {
        modifier(KMFieldContainer())
    }
}
