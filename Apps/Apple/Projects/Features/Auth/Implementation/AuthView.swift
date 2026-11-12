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
				VStack(spacing: 24) {
					Spacer(minLength: 40)

					// Header
					VStack(spacing: 8) {
						Image(systemName: "lock.shield.fill")
							.font(.system(size: 48))
							.foregroundColor(DesignToken.View.primary)

						Text("KeyManage")
							.font(.largeTitle.bold())
							.foregroundColor(DesignToken.View.foreground)

						Text(viewModel.state.mode == .register ? AuthStrings.registerTitle : AuthStrings.loginTitle)
							.font(.subheadline)
							.foregroundColor(DesignToken.View.mutedForeground)
					}

					// Card Container
					VStack(alignment: .leading, spacing: 18) {
						// Error banner
						if let error = viewModel.state.errorMessage {
							HStack(spacing: 10) {
								Image(systemName: "exclamationmark.triangle.fill")
									.foregroundColor(.red)
								Text(error)
									.font(.footnote)
									.foregroundColor(.red)
									.fixedSize(horizontal: false, vertical: true)
							}
							.padding(12)
							.frame(maxWidth: .infinity, alignment: .leading)
							.background(Color.red.opacity(0.1))
							.cornerRadius(8)
						}

						// Email Input
						VStack(alignment: .leading, spacing: 6) {
							Text("이메일")
								.font(.subheadline.bold())
								.foregroundColor(DesignToken.View.foreground)

							TextField(AuthStrings.emailPlaceholder, text: $viewModel.email)
								.textFieldStyle(.plain)
								#if os(iOS)
								.keyboardType(.emailAddress)
								.textInputAutocapitalization(.never)
								.textContentType(.username)
								#endif
								.disableAutocorrection(true)
								.padding(12)
								.background(DesignToken.View.card)
								.cornerRadius(8)
								.overlay(
									RoundedRectangle(cornerRadius: 8)
										.stroke(DesignToken.View.border, lineWidth: 1)
								)
						}

						// Password Input
						VStack(alignment: .leading, spacing: 6) {
							Text("마스터 비밀번호")
								.font(.subheadline.bold())
								.foregroundColor(DesignToken.View.foreground)

							SecureField(AuthStrings.passwordPlaceholder, text: $viewModel.password)
								.textFieldStyle(.plain)
								#if os(iOS)
								.textContentType(viewModel.state.mode == .register ? .newPassword : .password)
								#endif
								.padding(12)
								.background(DesignToken.View.card)
								.cornerRadius(8)
								.overlay(
									RoundedRectangle(cornerRadius: 8)
										.stroke(DesignToken.View.border, lineWidth: 1)
								)

							// 가입 화면 강도 안내. 취약해도 가입은 막지 않는다 (SEC-002)
							if viewModel.state.showsStrengthGauge {
								KMGaugeBar(score: viewModel.state.passwordStrengthScore)
							}
							if viewModel.state.showsWeakPasswordWarning {
								Text(AuthStrings.weakMasterPasswordWarning)
									.font(.footnote)
									.foregroundColor(DesignToken.View.mutedForeground)
									.fixedSize(horizontal: false, vertical: true)
							}
						}

						// Confirmation Input (Register Mode Only)
						if viewModel.state.mode == .register {
							VStack(alignment: .leading, spacing: 6) {
								Text("비밀번호 확인")
									.font(.subheadline.bold())
									.foregroundColor(DesignToken.View.foreground)

								SecureField(AuthStrings.confirmationPlaceholder, text: $viewModel.confirmation)
									.textFieldStyle(.plain)
									#if os(iOS)
									.textContentType(.newPassword)
									#endif
									.padding(12)
									.background(DesignToken.View.card)
									.cornerRadius(8)
									.overlay(
										RoundedRectangle(cornerRadius: 8)
											.stroke(DesignToken.View.border, lineWidth: 1)
									)

								Text("마스터 비밀번호는 12자 이상이어야 하며, 분실 시 복구할 수 없습니다.")
									.font(.caption)
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
										.tint(.white)
								} else {
									Text(viewModel.state.mode == .register ? AuthStrings.registerTitle : AuthStrings.loginTitle)
										.font(.body.weight(.bold))
										.foregroundColor(.white)
								}
								Spacer()
							}
							.padding(.vertical, 14)
							.background(viewModel.canSubmit ? DesignToken.View.primary : Color.gray.opacity(0.5))
							.cornerRadius(8)
						}
						.buttonStyle(.plain)
						.disabled(!viewModel.canSubmit)

						// Switch Mode Button
						Button {
							viewModel.send(.switchMode)
						} label: {
							Text(viewModel.state.mode == .register ? AuthStrings.switchToLoginPrompt : AuthStrings.switchToRegisterPrompt)
								.font(.subheadline)
								.foregroundColor(DesignToken.View.primary)
								.frame(maxWidth: .infinity, alignment: .center)
						}
						.buttonStyle(.plain)
						.padding(.top, 4)
					}
					.padding(24)
					.background(DesignToken.View.card)
					.cornerRadius(12)
					.overlay(
						RoundedRectangle(cornerRadius: 12)
							.stroke(DesignToken.View.border, lineWidth: 1)
					)
					.frame(maxWidth: 420)

					Spacer(minLength: 40)
				}
				.padding(.horizontal, 20)
			}
		}
	}
}
