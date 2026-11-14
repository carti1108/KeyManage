import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 로그인·가입 RIB의 뷰. 상태는 공유 코드가 `render`로 넘기고, 입력은 `listener`로 돌려준다
@MainActor
@Observable
final class SharedAuthScreen: NSObject, @preconcurrency AuthViewContract, SwiftUIRenderable {
	@ObservationIgnored weak var listener: (any AuthPresentableListener)?
	@ObservationIgnored private let activity: @MainActor () -> Void

	init(activity: @escaping @MainActor () -> Void) {
		self.activity = activity
	}

	private(set) var state = AuthViewState(
		mode: .login, email: "", password: "", confirmation: "", isLoading: false,
		notice: nil, error: nil, isConfirmingRollback: false
	)

	func renderAuth(state: AuthViewState) {
		self.state = state
	}

	func send(_ action: any AuthViewAction) {
		activity()
		listener?.handleAuth(action: action)
	}

	func makeView() -> AnyView {
		AnyView(SharedAuthView(screen: self))
	}
}

private struct SharedAuthView: View {
	let screen: SharedAuthScreen

	private var state: AuthViewState { screen.state }

	var body: some View {
		ScrollView {
			VStack(spacing: 16) {
				header
				KMCard(padding: .md) {
					VStack(alignment: .leading, spacing: 12) {
						if let notice = state.notice {
							SharedBanner(kind: .info, text: notice).accessibilityIdentifier("authNotice")
						}
						if let error = state.error {
							SharedErrorText(message: error).accessibilityIdentifier("authError")
						}
						if state.isConfirmingRollback {
							SharedRollbackPrompt(
								accept: { screen.send(AuthViewActionAcceptRollback.shared) },
								cancel: { screen.send(AuthViewActionCancelRollback.shared) }
							)
						}
						fields
						KMButton(
							state.isRegistering ? "가입" : "로그인",
							variant: .primary,
							size: .lg,
							isLoading: state.isLoading,
							isDisabled: state.isLoading
						) { screen.send(AuthViewActionSubmit.shared) }
							.accessibilityIdentifier("authSubmitButton")
						if state.isLoading {
							ProgressView().frame(maxWidth: .infinity).accessibilityIdentifier("authLoading")
						}
					}
				}
				.frame(maxWidth: 440)
				Button(state.isRegistering ? "이미 계정이 있나요? 로그인" : "계정이 없나요? 가입") {
					screen.send(AuthViewActionSwitchMode.shared)
				}
				.font(.subheadline)
				.disabled(state.isLoading)
				.accessibilityIdentifier("authSwitchModeButton")
			}
			.padding(16)
			.frame(maxWidth: .infinity)
		}
		.scrollDismissesKeyboard(.interactively)
		.background(DesignToken.View.background)
	}

	private var header: some View {
		HStack(spacing: 8) {
			Image(systemName: "lock.shield.fill")
				.font(.title)
				.foregroundStyle(DesignToken.View.primary)
				.accessibilityHidden(true)
			Text("KeyManage").font(.title2.bold()).foregroundStyle(DesignToken.View.foreground)
			Text(state.isRegistering ? "가입" : "로그인")
				.font(.subheadline)
				.foregroundStyle(DesignToken.View.mutedForeground)
				.accessibilityAddTraits(.isHeader)
		}
	}

	@ViewBuilder private var fields: some View {
		VStack(alignment: .leading, spacing: 6) {
			SharedFieldLabel("이메일")
			SharedTextInput(title: "이메일", value: state.email) { screen.send(AuthViewActionUpdateEmail(email: $0)) }
				.textContentType(.username)
				.sharedEmailInput()
				.autocorrectionDisabled()
				.accessibilityIdentifier("authEmailField")
		}
		VStack(alignment: .leading, spacing: 6) {
			SharedFieldLabel("마스터 비밀번호")
			SharedTextInput(title: "마스터 비밀번호", value: state.password, isSecure: true) {
				screen.send(AuthViewActionUpdatePassword(password: $0))
			}
			.accessibilityIdentifier("authPasswordField")
			if state.showsStrengthGauge {
				KMGaugeBar(score: Int(state.passwordStrengthScore)).accessibilityIdentifier("authStrengthGauge")
			}
			if state.showsWeakPasswordWarning {
				Text(AuthNoticesKt.WEAK_MASTER_PASSWORD_WARNING)
					.font(.footnote)
					.foregroundStyle(DesignToken.View.mutedForeground)
					.accessibilityIdentifier("authWeakWarning")
			}
		}
		if state.isRegistering {
			VStack(alignment: .leading, spacing: 6) {
				SharedFieldLabel("마스터 비밀번호 확인")
				SharedTextInput(title: "마스터 비밀번호 확인", value: state.confirmation, isSecure: true) {
					screen.send(AuthViewActionUpdateConfirmation(confirmation: $0))
				}
				.accessibilityIdentifier("authConfirmationField")
			}
			Text(MasterPasswordSetup.shared.BODY)
				.font(.footnote)
				.foregroundStyle(DesignToken.View.mutedForeground)
				.fixedSize(horizontal: false, vertical: true)
		}
	}
}
