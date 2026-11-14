import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 비밀번호 생성기 RIB의 뷰. 생성기 탭과 항목 폼 안(사용·닫기) 두 곳에서 쓴다.
/// 옛 Swift PasswordGeneratorView 배치(생성 결과 카드, 강도 카드, 옵션 카드)를 따른다
@MainActor
@Observable
final class SharedPasswordGeneratorScreen: NSObject, @preconcurrency PasswordGeneratorViewContract, SwiftUIRenderable {
	@ObservationIgnored weak var listener: (any PasswordGeneratorPresentableListener)?
	@ObservationIgnored private let activity: @MainActor () -> Void

	init(activity: @escaping @MainActor () -> Void) {
		self.activity = activity
	}

	private(set) var state: PasswordGeneratorViewState?

	func renderPasswordGenerator(state: PasswordGeneratorViewState) {
		self.state = state
	}

	func send(_ action: any PasswordGeneratorViewAction) {
		activity()
		listener?.handlePasswordGenerator(action: action)
	}

	func makeView() -> AnyView {
		AnyView(SharedPasswordGeneratorView(screen: self))
	}
}

private struct SharedPasswordGeneratorView: View {
	let screen: SharedPasswordGeneratorScreen

	var body: some View {
		if let state = screen.state {
			// 폼 안에서는 시트의 스크롤 안에 들어가므로 따로 스크롤하지 않는다
			if state.showsUse || state.showsClose {
				SharedPasswordGeneratorContent(screen: screen, state: state)
			} else {
				ScrollView {
					SharedPasswordGeneratorContent(screen: screen, state: state).padding(24)
				}
			}
		}
	}
}

private struct SharedPasswordGeneratorContent: View {
	let screen: SharedPasswordGeneratorScreen
	let state: PasswordGeneratorViewState

	var body: some View {
		VStack(spacing: 24) {
			resultCard
			if let generated = state.generated {
				strengthCard(generated)
			}
			optionsCard
		}
		.accessibilityElement(children: .contain)
		.onChange(of: state.announcement) { _, announcement in
			// 새 비밀번호를 만들었다는 사실만 보조 기술에 알린다
			if !announcement.isEmpty { AccessibilityNotification.Announcement(announcement).post() }
		}
	}

	private var resultCard: some View {
		KMCard {
			VStack(alignment: .leading, spacing: 16) {
				Text("생성된 비밀번호").font(.headline).foregroundStyle(DesignToken.View.foreground)
				VStack(alignment: .leading, spacing: 12) {
					Text(state.generated?.text ?? "")
						.font(.system(.title3, design: .monospaced).bold())
						.foregroundStyle(DesignToken.View.primary)
						.lineLimit(2)
						.minimumScaleFactor(0.7)
						.textSelection(.disabled)
						.accessibilityIdentifier("generatedPassword")
					HStack(spacing: 8) {
						KMButton("재생성", variant: .outline, size: .sm, icon: DesignToken.Icon.regenerate) {
							screen.send(PasswordGeneratorViewActionRegenerate.shared)
						}
						.accessibilityIdentifier("regenerateButton")
						if state.showsCopy {
							KMButton("복사", variant: .primary, size: .sm, icon: DesignToken.Icon.clipboardCopy, isDisabled: !state.canUseGenerated) {
								screen.send(PasswordGeneratorViewActionCopy.shared)
							}
							.accessibilityIdentifier("copyGeneratedButton")
						}
						if state.showsUse {
							KMButton("사용", variant: .primary, size: .sm, isDisabled: !state.canUseGenerated) {
								screen.send(PasswordGeneratorViewActionUse.shared)
							}
							.accessibilityIdentifier("useGeneratedButton")
						}
						if state.showsClose {
							KMButton("닫기", variant: .ghost, size: .sm) { screen.send(PasswordGeneratorViewActionClose.shared) }
								.accessibilityIdentifier("closeGeneratorButton")
						}
					}
				}
				.padding(12)
				.frame(maxWidth: .infinity, alignment: .leading)
				.background(DesignToken.View.muted)
				.clipShape(RoundedRectangle(cornerRadius: 8))
				if let problem = state.problemMessage {
					SharedBanner(kind: .error, text: problem).accessibilityIdentifier("generatorProblem")
				}
				if let error = state.copyError {
					SharedBanner(kind: .error, text: error)
				}
				if let notice = state.copyNotice {
					SharedCopyNoticeBanner(notice: notice)
				}
			}
			.padding(16)
		}
	}

	private func strengthCard(_ generated: GeneratedPassword) -> some View {
		KMCard {
			VStack(alignment: .leading, spacing: 16) {
				HStack {
					Text("보안 강도").font(.headline).foregroundStyle(DesignToken.View.foreground)
					Spacer()
					Text("\(generated.score)점 / 100점")
						.font(.subheadline.bold())
						.foregroundStyle(DesignToken.View.mutedForeground)
				}
				KMGaugeBar(score: Int(generated.score))
				if PasswordStrengthKt.isWeak(score: generated.score) {
					Text(VaultListMessages.shared.WEAK_PASSWORD_BODY)
						.font(.footnote)
						.foregroundStyle(DesignToken.View.destructive)
				}
				if generated.hasSequentialDigits {
					Text(VaultListMessages.shared.SEQUENTIAL_DIGITS_NOTICE)
						.font(.footnote)
						.foregroundStyle(DesignToken.View.destructive)
						.accessibilityIdentifier("generatorSequentialNotice")
				}
			}
			.padding(16)
		}
	}

	private var optionsCard: some View {
		KMCard {
			VStack(alignment: .leading, spacing: 20) {
				Text("생성 옵션").font(.headline).foregroundStyle(DesignToken.View.foreground)
				lengthControl
				Divider()
				VStack(alignment: .leading, spacing: 12) {
					charsetToggle(.uppercase, isOn: state.uppercase, title: "대문자 (A-Z)", sample: "ABC...XYZ")
					charsetToggle(.lowercase, isOn: state.lowercase, title: "소문자 (a-z)", sample: "abc...xyz")
					charsetToggle(.digits, isOn: state.digits, title: "숫자 (0-9)", sample: "0123456789")
					charsetToggle(.symbols, isOn: state.symbols, title: "특수문자", sample: "!@#$%^&*()_+-=[]{}|;:,.<>?")
				}
			}
			.padding(16)
		}
	}

	private var lengthControl: some View {
		VStack(alignment: .leading, spacing: 8) {
			HStack {
				Text("비밀번호 길이").font(.subheadline).foregroundStyle(DesignToken.View.foreground)
				Spacer()
				SharedTextInput(title: "길이", value: state.lengthText) {
					screen.send(PasswordGeneratorViewActionChangeLength(text: $0))
				}
				.sharedNumberInput()
				.frame(width: 72)
				.onSubmit { screen.send(PasswordGeneratorViewActionFinishEditingLength.shared) }
				.accessibilityIdentifier("generatorLengthField")
				Text("자").font(.subheadline)
			}
			Slider(
				value: Binding(
					get: { Double(Int(state.lengthText) ?? Int(PasswordGeneratorKt.MIN_GENERATED_LENGTH)) },
					set: { screen.send(PasswordGeneratorViewActionChangeLength(text: String(Int($0)))) }
				),
				in: Double(PasswordGeneratorKt.MIN_GENERATED_LENGTH)...Double(PasswordGeneratorKt.MAX_GENERATED_LENGTH),
				step: 1
			) { editing in
				if !editing { screen.send(PasswordGeneratorViewActionFinishEditingLength.shared) }
			}
			.accessibilityLabel("비밀번호 길이")
		}
	}

	private func charsetToggle(_ charset: GeneratorCharset, isOn: Bool, title: String, sample: String) -> some View {
		Toggle(isOn: Binding(
			get: { isOn },
			set: { screen.send(PasswordGeneratorViewActionSetCharset(charset: charset, enabled: $0)) }
		)) {
			VStack(alignment: .leading) {
				Text(title).font(.subheadline)
				Text(sample).font(.caption).foregroundStyle(DesignToken.View.mutedForeground)
			}
		}
	}
}
