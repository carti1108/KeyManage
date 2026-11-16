import SwiftUI
import KeyManageDomain
import KeyManageDesignSystem

public struct AuthView: View {
	@ObservedObject var viewModel: AuthViewModel

	public init(viewModel: AuthViewModel) {
		self.viewModel = viewModel
	}

	public var body: some View {
		ZStack {
			DesignToken.View.background
				.ignoresSafeArea()

			ScrollView {
				VStack(spacing: DesignToken.Spacing.space6) {
					Spacer(minLength: 40)

					// Header
					VStack(spacing: DesignToken.Spacing.space2) {
						Image(systemName: "lock.shield.fill")
							.font(.system(size: 48))
							.foregroundColor(DesignToken.View.primary)

						Text("KeyManage")
							.font(DesignToken.Typography.display)
							.foregroundColor(DesignToken.View.foreground)

						Text(viewModel.state.mode == .register ? AuthStrings.registerTitle : AuthStrings.loginTitle)
							.font(DesignToken.Typography.label)
							.foregroundColor(DesignToken.View.mutedForeground)
					}

					// Card Container
					VStack(alignment: .leading, spacing: DesignToken.Spacing.space5) {
						// Error banner
						if let error = viewModel.state.errorMessage {
							HStack(spacing: DesignToken.Spacing.space3) {
								Image(systemName: "exclamationmark.triangle.fill")
									.foregroundColor(DesignToken.View.destructive)
								Text(error)
									.font(DesignToken.Typography.caption)
									.foregroundColor(DesignToken.View.destructive)
									.fixedSize(horizontal: false, vertical: true)
							}
							.padding(DesignToken.Spacing.space3)
							.frame(maxWidth: .infinity, alignment: .leading)
							.background(DesignToken.View.destructive.opacity(0.1))
							.cornerRadius(DesignToken.Radius.md)
						}

						// Email Input
						VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
							Text("이메일")
								.font(DesignToken.Typography.label.weight(.bold))
								.foregroundColor(DesignToken.View.foreground)

							TextField(AuthStrings.emailPlaceholder, text: $viewModel.email)
								.textFieldStyle(.plain)
								#if os(iOS)
								.keyboardType(.emailAddress)
								.textInputAutocapitalization(.never)
								.textContentType(.username)
								#endif
								.disableAutocorrection(true)
								.padding(DesignToken.Spacing.space3)
								.background(DesignToken.View.card)
								.cornerRadius(DesignToken.Radius.md)
								.overlay(
									RoundedRectangle(cornerRadius: DesignToken.Radius.md)
										.stroke(DesignToken.View.border, lineWidth: 1)
								)
						}

						// Password Input
						VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
							Text("마스터 비밀번호")
								.font(DesignToken.Typography.label.weight(.bold))
								.foregroundColor(DesignToken.View.foreground)

							SecureField(AuthStrings.passwordPlaceholder, text: $viewModel.password)
								.textFieldStyle(.plain)
								#if os(iOS)
								.textContentType(viewModel.state.mode == .register ? .newPassword : .password)
								#endif
								.padding(DesignToken.Spacing.space3)
								.background(DesignToken.View.card)
								.cornerRadius(DesignToken.Radius.md)
								.overlay(
									RoundedRectangle(cornerRadius: DesignToken.Radius.md)
										.stroke(DesignToken.View.border, lineWidth: 1)
								)

							// 가입 화면 강도 안내. 취약해도 가입은 막지 않는다 (SEC-002)
							if viewModel.state.showsStrengthGauge {
								KMGaugeBar(score: viewModel.state.passwordStrengthScore)
							}
							if viewModel.state.showsWeakPasswordWarning {
								Text(AuthStrings.weakMasterPasswordWarning)
									.font(DesignToken.Typography.caption)
									.foregroundColor(DesignToken.View.mutedForeground)
									.fixedSize(horizontal: false, vertical: true)
							}
						}

						// Confirmation Input (Register Mode Only)
						if viewModel.state.mode == .register {
							VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
								Text("비밀번호 확인")
									.font(DesignToken.Typography.label.weight(.bold))
									.foregroundColor(DesignToken.View.foreground)

								SecureField(AuthStrings.confirmationPlaceholder, text: $viewModel.confirmation)
									.textFieldStyle(.plain)
									#if os(iOS)
									.textContentType(.newPassword)
									#endif
									.padding(DesignToken.Spacing.space3)
									.background(DesignToken.View.card)
									.cornerRadius(DesignToken.Radius.md)
									.overlay(
										RoundedRectangle(cornerRadius: DesignToken.Radius.md)
											.stroke(DesignToken.View.border, lineWidth: 1)
									)

								Text("마스터 비밀번호는 12자 이상이어야 하며, 분실 시 복구할 수 없습니다.")
									.font(DesignToken.Typography.caption)
									.foregroundColor(DesignToken.View.mutedForeground)
							}
						}

						// Submit Button
						Button {
							viewModel.send(.submit)
						} label: {
							HStack {
								Spacer()
								if viewModel.state.isLoading {
									ProgressView()
										.progressViewStyle(.circular)
										.tint(DesignToken.View.primaryForeground)
								} else {
									Text(viewModel.state.mode == .register ? AuthStrings.registerTitle : AuthStrings.loginTitle)
										.font(DesignToken.Typography.body.weight(.bold))
										.foregroundColor(DesignToken.View.primaryForeground)
								}
								Spacer()
							}
							.padding(.vertical, DesignToken.Spacing.space4)
							.background(viewModel.canSubmit ? DesignToken.View.primary : DesignToken.View.primary.opacity(0.5))
							.cornerRadius(DesignToken.Radius.md)
						}
						.buttonStyle(.plain)
						.disabled(!viewModel.canSubmit)

						// Switch Mode Button
						Button {
							viewModel.send(.switchMode)
						} label: {
							Text(viewModel.state.mode == .register ? AuthStrings.switchToLoginPrompt : AuthStrings.switchToRegisterPrompt)
								.font(DesignToken.Typography.label)
								.foregroundColor(DesignToken.View.primary)
								.frame(maxWidth: .infinity, alignment: .center)
						}
						.buttonStyle(.plain)
						.padding(.top, DesignToken.Spacing.space1)
					}
					.padding(DesignToken.Spacing.space6)
					.background(DesignToken.View.card)
					.cornerRadius(DesignToken.Radius.lg)
					.overlay(
						RoundedRectangle(cornerRadius: DesignToken.Radius.lg)
							.stroke(DesignToken.View.border, lineWidth: 1)
					)
					.frame(maxWidth: 420)

					Spacer(minLength: 40)
				}
				.padding(.horizontal, DesignToken.Spacing.space5)
			}
		}
	}
}
