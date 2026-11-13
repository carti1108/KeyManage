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
			VStack(alignment: .leading, spacing: 16) {
				Text(state.isRegistering ? "가입" : "로그인").font(.largeTitle.bold())
				if let notice = state.notice {
					Text(notice).accessibilityIdentifier("authNotice")
				}
				if state.isRegistering {
					Text(MasterPasswordSetup.shared.BODY).font(.footnote).foregroundStyle(.secondary)
				}
				SharedTextInput(title: "이메일", value: state.email) { screen.send(AuthViewActionUpdateEmail(email: $0)) }
					.textContentType(.username)
					.keyboardType(.emailAddress)
					.textInputAutocapitalization(.never)
					.autocorrectionDisabled()
					.accessibilityIdentifier("authEmailField")
				SharedTextInput(title: "마스터 비밀번호", value: state.password, isSecure: true) { screen.send(AuthViewActionUpdatePassword(password: $0)) }
					.accessibilityIdentifier("authPasswordField")
				if state.isRegistering {
					SharedTextInput(title: "마스터 비밀번호 확인", value: state.confirmation, isSecure: true) {
						screen.send(AuthViewActionUpdateConfirmation(confirmation: $0))
					}
						.accessibilityIdentifier("authConfirmationField")
					if state.showsWeakPasswordWarning {
						Text(AuthNoticesKt.WEAK_MASTER_PASSWORD_WARNING).font(.footnote).foregroundStyle(.secondary)
					}
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
				if state.isLoading {
					ProgressView().accessibilityIdentifier("authLoading")
				}
				Button(state.isRegistering ? "가입" : "로그인") { screen.send(AuthViewActionSubmit.shared) }
					.buttonStyle(.borderedProminent)
					.disabled(state.isLoading)
					.accessibilityIdentifier("authSubmitButton")
				Button(state.isRegistering ? "로그인으로 전환" : "가입으로 전환") { screen.send(AuthViewActionSwitchMode.shared) }
					.disabled(state.isLoading)
					.accessibilityIdentifier("authSwitchModeButton")
			}
			.padding(24)
		}
	}

}
