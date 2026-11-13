import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 잠금 해제 RIB의 뷰
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
		isConfirmingRollback: false, isConfirmingLogout: false, isBiometricsAvailable: false
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
			VStack(alignment: .leading, spacing: 16) {
				Text("잠금 해제").font(.largeTitle.bold()).accessibilityIdentifier("unlockTitle")
				Text(state.email).foregroundStyle(.secondary).accessibilityIdentifier("unlockEmail")
				SharedTextInput(title: "마스터 비밀번호", value: state.password, isSecure: true) {
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
				if state.isConfirmingLogout {
					SharedLogoutPrompt(
						confirm: { screen.send(UnlockViewActionConfirmLogout.shared) },
						cancel: { screen.send(UnlockViewActionCancelLogout.shared) }
					)
				}
				if state.isLoading {
					ProgressView().accessibilityIdentifier("unlockLoading")
				}
				Button("잠금 해제") { screen.send(UnlockViewActionUnlock.shared) }
					.buttonStyle(.borderedProminent)
					.disabled(state.isLoading)
					.accessibilityIdentifier("unlockButton")
				Button("로그아웃") { screen.send(UnlockViewActionRequestLogout.shared) }
					.disabled(state.isLoading)
					.accessibilityIdentifier("unlockLogoutButton")
			}
			.padding(24)
		}
	}
}
