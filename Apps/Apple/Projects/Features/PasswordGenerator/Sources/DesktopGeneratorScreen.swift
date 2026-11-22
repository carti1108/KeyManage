#if os(macOS)
import KeyManageDesignSystem
import KeyManageShared
import SwiftUI

/// macOS 비밀번호 생성기. 웹 GeneratorRibScreen과 같은 회색 상자 한 장이다.
/// 생성기 탭과 항목 폼 안의 생성기가 같은 화면을 쓴다
struct DesktopGeneratorScreen: View {
	let view: SwiftUIPasswordGeneratorView

	var body: some View {
		if let state = view.state {
			// 항목 폼 안(닫기·사용이 보일 때)에서는 상자만, 탭에서는 본문 폭에 맞춰 위쪽 가운데에 둔다
			if state.showsClose || state.showsUse {
				box(state)
			} else {
				ScrollView {
					box(state)
						.frame(maxWidth: 576)
						.padding(DesignToken.Spacing.space6)
						.frame(maxWidth: .infinity)
				}
				.background(DesignToken.View.background)
			}
		}
	}

	private func box(_ state: PasswordGeneratorViewState) -> some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space3) {
			if let generated = state.generated {
				Text(generated.text)
					.font(DesignToken.Typography.bodyMono)
					.textSelection(.disabled)
					.accessibilityLabel(state.announcement.isEmpty ? "생성된 비밀번호" : state.announcement)
					.accessibilityIdentifier("generator.password")
				KMGaugeBar(score: Int(generated.score))
				if PasswordStrengthKt.isWeak(score: generated.score) {
					DesktopAlert(text: PasswordGeneratorMessages.shared.WEAK_WARNING)
				}
				if generated.hasSequentialDigits {
					Text(PasswordGeneratorMessages.shared.SEQUENTIAL_DIGITS_NOTICE)
						.font(DesignToken.Typography.caption)
						.foregroundColor(DesignToken.View.mutedForeground)
				}
			}
			options(state)
			if let problem = state.problemMessage {
				DesktopAlert(text: problem)
			}
			if let copyError = state.copyError {
				DesktopAlert(text: copyError)
			}
			buttons(state)
		}
		.padding(DesignToken.Spacing.space3)
		.background(DesignToken.View.muted.opacity(0.4))
		.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.md))
		.overlay(RoundedRectangle(cornerRadius: DesignToken.Radius.md).stroke(DesignToken.View.border, lineWidth: 1))
	}

	/// 길이 숫자 칸과 문자 종류 체크박스를 한 줄에 둔다
	private func options(_ state: PasswordGeneratorViewState) -> some View {
		HStack(spacing: DesignToken.Spacing.space4) {
			HStack(spacing: DesignToken.Spacing.space2) {
				Text("길이")
				TextField("", text: Binding(get: { state.lengthText }, set: { view.send(PasswordGeneratorViewActionChangeLength(text: $0)) }))
					.textFieldStyle(.roundedBorder)
					.frame(width: 64)
					.onSubmit { view.send(PasswordGeneratorViewActionFinishEditingLength.shared) }
					.accessibilityLabel("길이")
					.accessibilityIdentifier("generator.length")
			}
			charset("대문자", isOn: state.uppercase, charset: .uppercase)
			charset("소문자", isOn: state.lowercase, charset: .lowercase)
			charset("숫자", isOn: state.digits, charset: .digits)
			charset("특수기호", isOn: state.symbols, charset: .symbols)
		}
		.font(DesignToken.Typography.label)
	}

	private func charset(_ label: String, isOn: Bool, charset: GeneratorCharset) -> some View {
		Toggle(label, isOn: Binding(get: { isOn }, set: { view.send(PasswordGeneratorViewActionSetCharset(charset: charset, enabled: $0)) }))
			.toggleStyle(.checkbox)
	}

	private func buttons(_ state: PasswordGeneratorViewState) -> some View {
		HStack(spacing: DesignToken.Spacing.space2) {
			Spacer()
			if state.showsClose {
				KMButton("닫기", variant: .ghost, size: .sm) { view.send(PasswordGeneratorViewActionClose.shared) }
					.fixedSize()
					.accessibilityIdentifier("generator.close")
			}
			KMButton("다시 생성", variant: .outline, size: .sm, icon: "arrow.clockwise") { view.send(PasswordGeneratorViewActionRegenerate.shared) }
				.fixedSize()
				.accessibilityIdentifier("generator.regenerate")
			if state.showsCopy {
				KMButton("복사", variant: .outline, size: .sm, icon: DesignToken.Icon.clipboardCopy, isDisabled: !state.canUseGenerated) {
					view.send(PasswordGeneratorViewActionCopy.shared)
				}
				.fixedSize()
				.accessibilityIdentifier("generator.copy")
			}
			if state.showsUse {
				KMButton("이 비밀번호 사용", size: .sm, isDisabled: !state.canUseGenerated) { view.send(PasswordGeneratorViewActionUse.shared) }
					.fixedSize()
					.accessibilityIdentifier("generator.use")
			}
		}
	}
}
#endif
