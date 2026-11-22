import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 비밀번호 생성기의 SwiftUI 뷰(ComposePasswordGeneratorView의 짝). 공유 PasswordGenerator RIB이 상태를 넘기고 입력을 돌려받는다.
/// 첫 상태는 RIB이 활성화될 때 넘기므로 그전에는 아무것도 그리지 않는다
@MainActor
@Observable
public final class SwiftUIPasswordGeneratorView: NSObject, @preconcurrency PasswordGeneratorViewContract, SwiftUIRenderable {
	@ObservationIgnored public var listener: (any PasswordGeneratorPresentableListener)?
	public private(set) var state: PasswordGeneratorViewState?

	override public init() {}

	public func render(passwordGeneratorState state: PasswordGeneratorViewState) {
		self.state = state
	}

	func send(_ action: PasswordGeneratorViewAction) {
		listener?.handle(passwordGeneratorAction: action)
	}

	public func makeView() -> AnyView {
		#if os(macOS)
		AnyView(DesktopGeneratorScreen(view: self))
		#else
		AnyView(PasswordGeneratorScreen(view: self))
		#endif
	}
}

private struct PasswordGeneratorScreen: View {
	let view: SwiftUIPasswordGeneratorView

	var body: some View {
		if let state = view.state {
			content(state)
		}
	}

	private func content(_ state: PasswordGeneratorViewState) -> some View {
		ScrollView {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				header(state)
				KMCard {
					VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
						// 보조 기술에는 비밀번호 원문 대신 새로 만들었다는 사실만 알린다
						Text(state.generated?.text ?? " ")
							.font(DesignToken.Typography.bodyMono)
							.textSelection(.disabled)
							.accessibilityLabel(state.announcement.isEmpty ? "생성된 비밀번호" : state.announcement)
							.accessibilityIdentifier("generator.password")
						if let generated = state.generated {
							KMGaugeBar(score: Int(generated.score))
						}
					}
					.frame(maxWidth: .infinity, alignment: .leading)
				}
				notices(state)
				lengthControl(state)
				charsetToggle("대문자", isOn: state.uppercase, charset: .uppercase)
				charsetToggle("소문자", isOn: state.lowercase, charset: .lowercase)
				charsetToggle("숫자", isOn: state.digits, charset: .digits)
				charsetToggle("특수기호", isOn: state.symbols, charset: .symbols)
				if let copyError = state.copyError {
					warning(copyError)
				}
				buttons(state)
			}
			.padding(DesignToken.Spacing.space4)
		}
		.background(DesignToken.View.background)
	}

	@ViewBuilder
	private func header(_ state: PasswordGeneratorViewState) -> some View {
		HStack {
			Text("생성기")
				.font(DesignToken.Typography.title.weight(.bold))
				.accessibilityAddTraits(.isHeader)
			Spacer()
			if state.showsClose {
				Button("닫기") { view.send(PasswordGeneratorViewActionClose.shared) }
					.frame(minHeight: 44)
					.accessibilityIdentifier("generator.close")
			}
		}
	}

	@ViewBuilder
	private func notices(_ state: PasswordGeneratorViewState) -> some View {
		if let problem = state.problemMessage {
			warning(problem)
		}
		if let generated = state.generated, PasswordStrengthKt.isWeak(score: generated.score) {
			warning(PasswordGeneratorMessages.shared.WEAK_WARNING)
		}
		if state.generated?.hasSequentialDigits == true {
			Text(PasswordGeneratorMessages.shared.SEQUENTIAL_DIGITS_NOTICE)
				.font(DesignToken.Typography.caption)
				.foregroundColor(DesignToken.View.mutedForeground)
		}
	}

	/// 길이는 슬라이더로 고르고, 고른 값을 공유 상태의 길이 글자로 보낸다(Android 뷰와 같다)
	private func lengthControl(_ state: PasswordGeneratorViewState) -> some View {
		let range = Double(PasswordGeneratorKt.MIN_GENERATED_LENGTH)...Double(PasswordGeneratorKt.MAX_GENERATED_LENGTH)
		let length = Int(state.lengthText).map { min(max(Double($0), range.lowerBound), range.upperBound) }
			?? Double(PasswordGeneratorKt.DEFAULT_GENERATOR_OPTIONS.length)
		return VStack(spacing: DesignToken.Spacing.space1) {
			HStack {
				Text("길이").font(DesignToken.Typography.label.weight(.semibold))
				Spacer()
				Text("\(Int(length))").font(DesignToken.Typography.bodyMono)
			}
			Slider(
				value: Binding(get: { length }, set: { view.send(PasswordGeneratorViewActionChangeLength(text: String(Int($0)))) }),
				in: range,
				step: 1,
				onEditingChanged: { editing in
					if !editing { view.send(PasswordGeneratorViewActionFinishEditingLength.shared) }
				}
			)
			.accessibilityValue("\(Int(length))자")
			.accessibilityIdentifier("generator.length")
		}
	}

	private func charsetToggle(_ label: String, isOn: Bool, charset: GeneratorCharset) -> some View {
		Toggle(label, isOn: Binding(get: { isOn }, set: { view.send(PasswordGeneratorViewActionSetCharset(charset: charset, enabled: $0)) }))
			.font(DesignToken.Typography.body)
			.frame(minHeight: 44)
	}

	private func buttons(_ state: PasswordGeneratorViewState) -> some View {
		HStack(spacing: DesignToken.Spacing.space2) {
			KMButton("다시 생성", variant: .outline, isFullWidth: true) { view.send(PasswordGeneratorViewActionRegenerate.shared) }
				.accessibilityIdentifier("generator.regenerate")
			if state.showsCopy {
				KMButton("복사", isDisabled: !state.canUseGenerated, isFullWidth: true) { view.send(PasswordGeneratorViewActionCopy.shared) }
					.accessibilityIdentifier("generator.copy")
			}
			if state.showsUse {
				KMButton("사용", isDisabled: !state.canUseGenerated, isFullWidth: true) { view.send(PasswordGeneratorViewActionUse.shared) }
					.accessibilityIdentifier("generator.use")
			}
		}
	}

	private func warning(_ text: String) -> some View {
		Text(text)
			.font(DesignToken.Typography.caption)
			.foregroundColor(DesignToken.View.destructive)
			.frame(maxWidth: .infinity, alignment: .leading)
	}
}
