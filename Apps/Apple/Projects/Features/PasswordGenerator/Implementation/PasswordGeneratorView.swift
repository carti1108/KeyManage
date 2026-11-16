import SwiftUI
import KeyManageDomain
import KeyManageDesignSystem

struct PasswordGeneratorView: View {
	@ObservedObject var viewModel: PasswordGeneratorViewModel

	var body: some View {
		ScrollView {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				resultCard
				lengthControl
				optionsGroup
				actionButtons
			}
			.padding(DesignToken.Spacing.space4)
		}
		.background(DesignToken.View.background)
		.overlay(alignment: .bottom) {
			if let toast = viewModel.state.toastMessage {
				toastOverlay(toast)
			}
		}
	}

	// MARK: - Subviews

	private var resultCard: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space3) {
			Text(viewModel.state.generatedPassword.isEmpty ? "비밀번호를 생성 중입니다..." : viewModel.state.generatedPassword)
				.font(DesignToken.Typography.titleMono)
				.foregroundColor(DesignToken.View.foreground)
				.lineLimit(3)
				.minimumScaleFactor(0.7)
				.textSelection(.enabled)
				.accessibilityLabel("생성된 비밀번호")
				.accessibilityValue(viewModel.state.generatedPassword)

			KMGaugeBar(score: viewModel.state.score)

			if viewModel.state.securityLevel == .danger {
				warningText(EntropyScorer.warningMessage)
			}
			if let notice = viewModel.state.sequentialDigitsNotice {
				warningText(notice)
			}
		}
		.padding(DesignToken.Spacing.space4)
		.frame(maxWidth: .infinity, alignment: .leading)
		.background(DesignToken.View.muted)
		.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.lg))
	}

	private var lengthControl: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
			HStack {
				Text("길이")
					.font(DesignToken.Typography.body.weight(.medium))
				Spacer()
				Text("\(Int(viewModel.state.length))")
					.font(DesignToken.Typography.bodyMono.weight(.bold))
			}
			.foregroundColor(DesignToken.View.foreground)

			Slider(
				value: Binding(
					get: { viewModel.state.length },
					set: { viewModel.request(action: .updateLength($0)) }
				),
				in: Double(PasswordGenerationOptions.minimumLength)...Double(PasswordGenerationOptions.maximumLength),
				step: 1
			)
			.accessibilityLabel("길이")
			.accessibilityValue("\(Int(viewModel.state.length))자")
		}
	}

	private var optionsGroup: some View {
		VStack(spacing: 0) {
			optionToggle("대문자", isOn: viewModel.state.includeUppercase, action: .toggleUppercase)
			Divider()
			optionToggle("소문자", isOn: viewModel.state.includeLowercase, action: .toggleLowercase)
			Divider()
			optionToggle("숫자", isOn: viewModel.state.includeDigits, action: .toggleDigits)
			Divider()
			optionToggle("특수기호", isOn: viewModel.state.includeSymbols, action: .toggleSymbols)
		}
		.padding(.horizontal, DesignToken.Spacing.space4)
		.background(DesignToken.View.muted)
		.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.lg))
	}

	private var actionButtons: some View {
		HStack(spacing: DesignToken.Spacing.space2) {
			KMButton("다시 생성", variant: .secondary, size: .lg, isFullWidth: true) {
				viewModel.request(action: .regenerate)
			}
			KMButton(
				"복사",
				variant: .primary,
				size: .lg,
				isDisabled: viewModel.state.generatedPassword.isEmpty,
				isFullWidth: true
			) {
				viewModel.request(action: .copyPassword(viewModel.state.generatedPassword))
			}
		}
	}

	// MARK: - Helpers

	private func optionToggle(_ title: String, isOn: Bool, action: PasswordGeneratorViewAction) -> some View {
		Toggle(isOn: Binding(
			get: { isOn },
			set: { _ in viewModel.request(action: action) }
		)) {
			Text(title)
				.font(DesignToken.Typography.body)
				.foregroundColor(DesignToken.View.foreground)
		}
		.padding(.vertical, DesignToken.Spacing.space3)
	}

	private func warningText(_ message: String) -> some View {
		Text(message)
			.font(DesignToken.Typography.caption)
			.foregroundColor(DesignToken.View.destructive)
	}

	private func toastOverlay(_ message: String) -> some View {
		Text(message)
			.font(DesignToken.Typography.caption)
			.foregroundColor(DesignToken.View.primaryForeground)
			.padding(.horizontal, DesignToken.Spacing.space4)
			.padding(.vertical, DesignToken.Spacing.space3)
			.background(DesignToken.View.primary)
			.cornerRadius(DesignToken.Radius.md)
			.padding(.bottom, DesignToken.Spacing.space6)
			.transition(.opacity)
	}
}
