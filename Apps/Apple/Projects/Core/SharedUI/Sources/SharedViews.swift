import KeyManageDesignSystem
import KeyManageShared
import SwiftUI

// 공유 RIB 뷰 여럿이 함께 쓰는 SwiftUI 조각. Kotlin 타입(ErrorMessage)을 써서 프레임워크로 두지 않고,
// 피처 뷰와 함께 쓰는 곳(앱, Example)이 직접 컴파일한다 (modularization-plan F단계 설계)

/// 정책 문구로 바꾼 오류. 제목은 있을 때만 보인다
struct ErrorText: View {
	let error: ErrorMessage
	var identifier = "error"

	var body: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
			if let title = error.title {
				Text(title).font(DesignToken.Typography.label.weight(.semibold))
			}
			Text(error.body).font(DesignToken.Typography.caption)
		}
		.foregroundColor(DesignToken.View.destructive)
		.frame(maxWidth: .infinity, alignment: .leading)
		.accessibilityElement(children: .combine)
		.accessibilityIdentifier(identifier)
	}
}

/// 보일지 말지는 공유 상태가 정하는 대화상자의 바인딩. 사용자가 닫으면(바깥 누름 등) `onDismiss`로 알린다
@MainActor
func sharedStateBinding(_ isPresented: Bool, onDismiss: @escaping () -> Void) -> Binding<Bool> {
	Binding(get: { isPresented }, set: { if !$0 && isPresented { onDismiss() } })
}
