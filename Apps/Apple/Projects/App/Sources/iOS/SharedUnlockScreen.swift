import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 잠금 해제 RIB의 뷰. 옛 Swift UnlockView 배치(제목, 카드 안 비밀번호·해제·생체 해제)를 따른다
@MainActor
@Observable
final class SharedUnlockScreen: NSObject, @preconcurrency UnlockViewContract, SwiftUIRenderable {
	@ObservationIgnored weak var listener: (any UnlockPresentableListener)?
	@ObservationIgnored private let activity: @MainActor () -> Void

	init(activity: @escaping @MainActor () -> Void) {
		self.activity = activity
	}

	private(set) var state = UnlockViewState(
		email: "", password: "", isLoading: false, error: nil,
		isConfirmingRollback: false, isConfirmingLogout: false, isBiometricsAvailable: false,
		offersBiometricEnrollment: false, enrollsBiometrics: false
	)

	func renderUnlock(state: UnlockViewState) {
		self.state = state
	}

	func send(_ action: any UnlockViewAction) {
		activity()
		listener?.handleUnlock(action: action)
	}

	func makeView() -> AnyView {
		AnyView(SharedUnlockView(screen: self))
	}
}

private struct SharedUnlockView: View {
	let screen: SharedUnlockScreen

	private var state: UnlockViewState { screen.state }

	var body: some View {
		ScrollView {
			VStack(spacing: 24) {
				VStack(spacing: 8) {
					Image(systemName: DesignToken.Icon.vaultLock)
						.font(.system(size: 40))
						.foregroundStyle(DesignToken.View.primary)
						.accessibilityHidden(true)
					Text("잠금 해제")
						.font(.title2.bold())
						.foregroundStyle(DesignToken.View.foreground)
						.accessibilityAddTraits(.isHeader)
						.accessibilityIdentifier("unlockTitle")
					Text(state.email)
						.font(.subheadline)
						.foregroundStyle(DesignToken.View.mutedForeground)
						.accessibilityIdentifier("unlockEmail")
				}
				KMCard(padding: .lg) {
					VStack(alignment: .leading, spacing: 16) {
						SharedTextInput(title: "마스터 비밀번호 입력", value: state.password, isSecure: true) {
							screen.send(UnlockViewActionUpdatePassword(password: $0))
						}
						.accessibilityIdentifier("unlockPasswordField")
						if let error = state.error {
							SharedErrorText(message: error).accessibilityIdentifier("unlockError")
						}
						if state.isConfirmingRollback {
							SharedRollbackPrompt(
								accept: { screen.send(UnlockViewActionAcceptRollback.shared) },
								cancel: { screen.send(UnlockViewActionCancelRollback.shared) }
							)
						}
						if state.offersBiometricEnrollment {
							Toggle(state.biometricEnrollmentTitle, isOn: Binding(
								get: { state.enrollsBiometrics },
								set: { screen.send(UnlockViewActionSetBiometricEnrollment(enabled: $0)) }
							))
							.font(.footnote)
							.disabled(state.isLoading)
							.accessibilityIdentifier("unlockBiometricEnrollToggle")
						}
						KMButton(
							"잠금 해제",
							variant: .primary,
							isLoading: state.isLoading,
							isDisabled: state.isLoading
						) { screen.send(UnlockViewActionUnlock.shared) }
							.accessibilityIdentifier("unlockButton")
						if state.isLoading {
							ProgressView().frame(maxWidth: .infinity).accessibilityIdentifier("unlockLoading")
						}
						if state.isBiometricsAvailable {
							KMButton(
								state.biometricButtonTitle,
								variant: .outline,
								icon: DesignToken.Icon.vaultLock,
								isDisabled: state.isLoading
							) { screen.send(UnlockViewActionUnlockWithBiometrics.shared) }
								.accessibilityIdentifier("unlockBiometricButton")
						}
					}
				}
				.frame(maxWidth: 400)
				if state.isConfirmingLogout {
					KMCard {
						SharedLogoutPrompt(
							confirm: { screen.send(UnlockViewActionConfirmLogout.shared) },
							cancel: { screen.send(UnlockViewActionCancelLogout.shared) }
						)
					}
					.frame(maxWidth: 400)
				}
				KMButton("로그아웃", variant: .ghost, isDisabled: state.isLoading) {
					screen.send(UnlockViewActionRequestLogout.shared)
				}
				.frame(maxWidth: 200)
				.accessibilityIdentifier("unlockLogoutButton")
			}
			.padding(32)
			.frame(maxWidth: .infinity)
		}
		.background(DesignToken.View.background)
	}
}
