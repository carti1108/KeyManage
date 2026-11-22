#if os(macOS)
import KeyManageDesignSystem
import KeyManageShared
import SwiftUI

/// macOS 잠금 화면. 웹 UnlockRibScreen과 같은 가운데 카드다
struct DesktopUnlockScreen: View {
	let view: SwiftUIUnlockView

	private var state: UnlockViewState { view.state }

	var body: some View {
		card
			.desktopCenteredCard()
			.alert(RollbackWarning.shared.TITLE, isPresented: sharedStateBinding(state.isConfirmingRollback) { view.send(UnlockViewActionCancelRollback.shared) }) {
				Button(RollbackWarning.shared.ACCEPT, role: .destructive) { view.send(UnlockViewActionAcceptRollback.shared) }
				Button(RollbackWarning.shared.CANCEL, role: .cancel) { view.send(UnlockViewActionCancelRollback.shared) }
			} message: {
				Text(RollbackWarning.shared.BODY)
			}
			// 올리지 못한 변경 사항이 있을 때 로그아웃 전에 묻는다 (VLT-002)
			.alert(PendingLogoutConfirm.shared.TITLE, isPresented: sharedStateBinding(state.isConfirmingLogout) { view.send(UnlockViewActionCancelLogout.shared) }) {
				Button(PendingLogoutConfirm.shared.CONFIRM, role: .destructive) { view.send(UnlockViewActionConfirmLogout.shared) }
				Button(PendingLogoutConfirm.shared.CANCEL, role: .cancel) { view.send(UnlockViewActionCancelLogout.shared) }
			} message: {
				Text(PendingLogoutConfirm.shared.BODY)
			}
	}

	private var card: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
			VStack(spacing: DesignToken.Spacing.space2) {
				Image(systemName: DesignToken.Icon.vaultLock)
					.font(.system(size: 28))
					.foregroundColor(DesignToken.View.primary)
					.accessibilityHidden(true)
				Text("볼트가 잠겼습니다")
					.font(DesignToken.Typography.title.weight(.bold))
					.accessibilityAddTraits(.isHeader)
				Text(state.email)
					.font(DesignToken.Typography.label)
					.foregroundColor(DesignToken.View.mutedForeground)
			}
			.frame(maxWidth: .infinity)
			DesktopPasswordField(label: "마스터 비밀번호", text: state.password, identifier: "unlock.password") {
				view.send(UnlockViewActionUpdatePassword(password: $0))
			}
			.disabled(state.isLoading)
			if let notice = state.biometricNotice {
				Text(notice)
					.font(DesignToken.Typography.caption)
					.foregroundColor(DesignToken.View.mutedForeground)
			}
			if let error = state.error {
				DesktopAlert(title: error.title, text: error.body, identifier: "unlock.error")
			}
			KMButton("잠금 해제", isLoading: state.isLoading, isDisabled: state.password.isEmpty, isFullWidth: true) {
				view.send(UnlockViewActionUnlock.shared)
			}
			.keyboardShortcut(.defaultAction)
			.accessibilityIdentifier("unlock.submit")
			if state.isBiometricsAvailable {
				KMButton(BiometricUnlockMessages.shared.ACTION, variant: .outline, icon: "touchid", isDisabled: state.isLoading, isFullWidth: true) {
					view.send(UnlockViewActionUnlockWithBiometrics.shared)
				}
				.accessibilityIdentifier("unlock.biometric")
			}
			KMButton("로그아웃", variant: .ghost, size: .sm, isDisabled: state.isLoading, isFullWidth: true) {
				view.send(UnlockViewActionRequestLogout.shared)
			}
			.accessibilityIdentifier("unlock.logout")
		}
	}
}
#endif
