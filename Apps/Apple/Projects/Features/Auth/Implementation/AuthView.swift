import SwiftUI
import KeyManageDomain
import KeyManageDesignSystem

public struct AuthView: View {
	@ObservedObject var viewModel: AuthViewModel

	public init(viewModel: AuthViewModel) {
		self.viewModel = viewModel
	}

	private var isRegister: Bool { viewModel.state.mode == .register }

	public var body: some View {
		ZStack(alignment: .topLeading) {
			DesignToken.View.background
				.ignoresSafeArea()

			GeometryReader { proxy in
				ScrollView {
					VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
						header
						errorBanner
						fields
						submitButton
						switchModeButton
					}
					.frame(maxWidth: 420)
					.padding(.horizontal, DesignToken.Spacing.space4)
					.padding(.vertical, DesignToken.Spacing.space6)
					.frame(maxWidth: .infinity, minHeight: proxy.size.height)
				}
			}

			if isRegister {
				backToLoginButton
			}
		}
	}

	// MARK: - Sections

	private var header: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
			if !isRegister {
				Text("KeyManage")
					.font(DesignToken.Typography.heading)
					.foregroundColor(DesignToken.View.foreground)
			}
			Text(isRegister ? AuthStrings.registerTitle : AuthStrings.loginTitle)
				.font(DesignToken.Typography.display)
				.foregroundColor(DesignToken.View.foreground)
				.accessibilityAddTraits(.isHeader)
		}
	}

	@ViewBuilder
	private var errorBanner: some View {
		if let error = viewModel.state.errorMessage {
			HStack(spacing: DesignToken.Spacing.space3) {
				Image(systemName: DesignToken.Icon.warning)
					.foregroundColor(DesignToken.View.destructive)
					.accessibilityHidden(true)
				Text(error)
					.font(DesignToken.Typography.caption)
					.foregroundColor(DesignToken.View.destructive)
					.fixedSize(horizontal: false, vertical: true)
			}
			.padding(DesignToken.Spacing.space3)
			.frame(maxWidth: .infinity, alignment: .leading)
			.background(DesignToken.View.destructive.opacity(0.1))
			.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.md))
		}
	}

	private var fields: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
			labeled(AuthStrings.emailLabel) {
				TextField(AuthStrings.emailLabel, text: $viewModel.email)
					.textFieldStyle(.plain)
					#if os(iOS)
					.keyboardType(.emailAddress)
					.textInputAutocapitalization(.never)
					.textContentType(.username)
					#endif
					.disableAutocorrection(true)
					.kmFieldContainer()
			}

			labeled(AuthStrings.passwordLabel) {
				SecureField(AuthStrings.passwordLabel, text: $viewModel.password)
					.textFieldStyle(.plain)
					#if os(iOS)
					.textContentType(isRegister ? .newPassword : .password)
					#endif
					.kmFieldContainer()
			}

			if isRegister {
				labeled(AuthStrings.confirmationLabel) {
					SecureField(AuthStrings.confirmationLabel, text: $viewModel.confirmation)
						.textFieldStyle(.plain)
						#if os(iOS)
						.textContentType(.newPassword)
						#endif
						.kmFieldContainer()
				}

				// 가입 화면 강도 안내. 취약해도 가입은 막지 않는다 (SEC-002)
				if viewModel.state.showsStrengthGauge {
					KMGaugeBar(score: viewModel.state.passwordStrengthScore)
				}
				if viewModel.state.showsWeakPasswordWarning {
					helperText(AuthStrings.weakMasterPasswordWarning)
				}
				helperText(AuthStrings.setupNotice)
			}
		}
	}

	private var submitButton: some View {
		KMButton(
			isRegister ? AuthStrings.registerButtonTitle : AuthStrings.loginButtonTitle,
			size: .lg,
			isLoading: viewModel.state.isLoading,
			isDisabled: !viewModel.canSubmit,
			isFullWidth: true
		) {
			viewModel.send(.submit)
		}
	}

	private var switchModeButton: some View {
		Button {
			viewModel.send(.switchMode)
		} label: {
			Text(isRegister ? AuthStrings.switchToLoginPrompt : AuthStrings.switchToRegisterPrompt)
				.font(DesignToken.Typography.label.weight(.medium))
				.foregroundColor(DesignToken.View.foreground)
				.frame(maxWidth: .infinity, minHeight: 44)
		}
		.buttonStyle(.plain)
	}

	private var backToLoginButton: some View {
		Button {
			viewModel.send(.switchMode)
		} label: {
			HStack(spacing: DesignToken.Spacing.space1) {
				Image(systemName: "chevron.left")
					.font(DesignToken.Typography.body.weight(.semibold))
				Text(AuthStrings.loginButtonTitle)
					.font(DesignToken.Typography.body)
			}
			.foregroundColor(DesignToken.View.foreground)
			.frame(minHeight: 44)
		}
		.buttonStyle(.plain)
		.padding(.horizontal, DesignToken.Spacing.space4)
		.accessibilityLabel("로그인으로 돌아가기")
	}

	// MARK: - Helpers

	private func labeled<Field: View>(_ title: String, @ViewBuilder field: () -> Field) -> some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
			Text(title)
				.font(DesignToken.Typography.caption.weight(.medium))
				.foregroundColor(DesignToken.View.mutedForeground)
			field()
		}
	}

	private func helperText(_ text: String) -> some View {
		Text(text)
			.font(DesignToken.Typography.caption)
			.foregroundColor(DesignToken.View.mutedForeground)
			.fixedSize(horizontal: false, vertical: true)
	}
}
