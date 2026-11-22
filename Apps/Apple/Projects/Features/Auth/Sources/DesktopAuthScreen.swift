#if os(macOS)
import KeyManageDesignSystem
import KeyManageShared
import SwiftUI

/// macOS 로그인·가입 화면. 웹 AuthRibScreen과 같은 가운데 카드다
struct DesktopAuthScreen: View {
	let view: SwiftUIAuthView

	var body: some View {
		if let state = view.state {
			card(state)
				.desktopCenteredCard()
				.alert(RollbackWarning.shared.TITLE, isPresented: sharedStateBinding(state.isConfirmingRollback) { view.send(AuthViewActionCancelRollback.shared) }) {
					Button(RollbackWarning.shared.ACCEPT, role: .destructive) { view.send(AuthViewActionAcceptRollback.shared) }
					Button(RollbackWarning.shared.CANCEL, role: .cancel) { view.send(AuthViewActionCancelRollback.shared) }
				} message: {
					Text(RollbackWarning.shared.BODY)
				}
		}
	}

	private func card(_ state: AuthViewState) -> some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
				Text("KeyManage")
					.font(.system(size: 28, weight: .bold))
					.accessibilityAddTraits(.isHeader)
				Text(state.isRegistering ? "새 계정 만들기" : "볼트에 로그인")
					.font(DesignToken.Typography.label)
					.foregroundColor(DesignToken.View.mutedForeground)
			}
			if let notice = state.notice {
				DesktopAlert(text: notice, isError: false, identifier: "auth.notice")
			}
			DesktopTextField(label: "이메일", text: state.email, identifier: "auth.email") {
				view.send(AuthViewActionUpdateEmail(email: $0))
			}
			.disabled(state.isLoading)
			if state.isRegistering {
				DesktopAlert(title: MasterPasswordSetup.shared.TITLE, text: MasterPasswordSetup.shared.BODY, isError: false)
			}
			DesktopPasswordField(label: "마스터 비밀번호", text: state.password, identifier: "auth.password") {
				view.send(AuthViewActionUpdatePassword(password: $0))
			}
			.disabled(state.isLoading)
			if state.showsStrengthGauge {
				KMGaugeBar(score: Int(state.passwordStrengthScore))
			}
			if state.isRegistering {
				if state.showsWeakPasswordWarning {
					Text(AuthNoticesKt.WEAK_MASTER_PASSWORD_WARNING)
						.font(DesignToken.Typography.label)
						.foregroundColor(DesignToken.View.mutedForeground)
				}
				DesktopPasswordField(label: "마스터 비밀번호 확인", text: state.confirmation, identifier: "auth.confirmation") {
					view.send(AuthViewActionUpdateConfirmation(confirmation: $0))
				}
				.disabled(state.isLoading)
			}
			if let error = state.error {
				DesktopAlert(title: error.title, text: error.body, identifier: "auth.error")
			}
			KMButton(
				state.isRegistering ? "가입" : "로그인",
				isLoading: state.isLoading,
				isDisabled: state.email.trimmingCharacters(in: .whitespaces).isEmpty || state.password.isEmpty,
				isFullWidth: true
			) { view.send(AuthViewActionSubmit.shared) }
				.keyboardShortcut(.defaultAction)
				.accessibilityIdentifier("auth.submit")
			KMButton(state.isRegistering ? "이미 계정이 있어요" : "계정 만들기", variant: .ghost, size: .sm, isDisabled: state.isLoading, isFullWidth: true) {
				view.send(AuthViewActionSwitchMode.shared)
			}
			.accessibilityIdentifier("auth.switchMode")
		}
	}
}
#endif
