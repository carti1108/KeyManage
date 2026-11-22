#if os(macOS)
import KeyManageDesignSystem
import SwiftUI

// macOS 화면이 웹 데스크톱 화면(Apps/Web/src/ui/rib)과 같은 모양을 내도록 쓰는 조각.
// 웹 공용 컴포넌트(TextField·PasswordField·Alert·Card)의 짝이다

/// 라벨을 위에 둔 입력 칸(웹 TextField)
struct DesktopTextField: View {
	let label: String
	let text: String
	var identifier: String?
	let onChange: (String) -> Void

	var body: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
			Text(label).font(DesignToken.Typography.label.weight(.medium))
			TextField("", text: Binding(get: { text }, set: onChange))
				.textFieldStyle(.plain)
				.autocorrectionDisabled()
				.kmFieldContainer()
				.accessibilityLabel(label)
				.accessibilityIdentifier(identifier ?? label)
		}
	}
}

/// 라벨을 위에 둔 비밀번호 칸(웹 PasswordField)
struct DesktopPasswordField: View {
	let label: String
	let text: String
	var identifier: String?
	var onCopy: (() -> Void)?
	let onChange: ((String) -> Void)?

	var body: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
			Text(label).font(DesignToken.Typography.label.weight(.medium))
			if let onChange {
				KMPasswordField(text: Binding(get: { text }, set: onChange), placeholder: "", showCopyAction: false)
					.accessibilityIdentifier(identifier ?? label)
			} else {
				KMPasswordField(text: .constant(text), placeholder: "", showCopyAction: onCopy != nil, onCopy: onCopy)
					.accessibilityIdentifier(identifier ?? label)
			}
		}
	}
}

/// 안내·오류 상자(웹 Alert). 오류는 붉은 테두리, 안내는 회색 바탕이다
struct DesktopAlert: View {
	var title: String?
	let text: String
	var isError = true
	var identifier: String?

	var body: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
			if let title {
				Text(title).font(DesignToken.Typography.label.weight(.semibold))
			}
			Text(text).font(DesignToken.Typography.label)
		}
		.padding(DesignToken.Spacing.space3)
		.frame(maxWidth: .infinity, alignment: .leading)
		.foregroundColor(isError ? DesignToken.View.destructive : DesignToken.View.foreground)
		.background(isError ? DesignToken.View.destructive.opacity(0.06) : DesignToken.View.muted)
		.overlay(
			RoundedRectangle(cornerRadius: DesignToken.Radius.md)
				.stroke(isError ? DesignToken.View.destructive.opacity(0.4) : DesignToken.View.border, lineWidth: 1)
		)
		.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.md))
		.accessibilityElement(children: .combine)
		.accessibilityIdentifier(identifier ?? "alert")
	}
}

extension View {
	/// 테두리 있는 카드 면(웹 Card)
	func desktopCard(padding: CGFloat = DesignToken.Spacing.space6) -> some View {
		self.padding(padding)
			.background(DesignToken.View.card)
			.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.lg))
			.overlay(RoundedRectangle(cornerRadius: DesignToken.Radius.lg).stroke(DesignToken.View.border, lineWidth: 1))
	}

	/// 창 가운데에 폭 384의 카드로 둔다(웹 인증·잠금 화면 `max-w-sm` 카드)
	func desktopCenteredCard() -> some View {
		ScrollView {
			self.frame(width: 384)
				.desktopCard()
				.padding(DesignToken.Spacing.space4)
				.containerRelativeFrame([.horizontal, .vertical])
		}
		.background(DesignToken.View.background)
	}
}
#endif
