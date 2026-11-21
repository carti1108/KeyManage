import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 잠금 화면의 SwiftUI 뷰. 공유 Unlock RIB이 `render`로 상태를 넘기고, 입력은 `listener`로 돌려보낸다.
/// 상태와 흐름은 공유 Kotlin이 맡고 여기에는 그리는 일만 둔다 (ComposeUnlockView의 짝)
@MainActor
@Observable
public final class SwiftUIUnlockView: NSObject, @preconcurrency UnlockViewContract, SwiftUIRenderable {
	@ObservationIgnored public var listener: (any UnlockPresentableListener)?
	public private(set) var state = UnlockViewState(
		email: "", password: "", isLoading: false, error: nil, isConfirmingRollback: false,
		isConfirmingLogout: false, isBiometricsAvailable: false, biometricNotice: nil
	)

	override public init() {}

	public func render(unlockState state: UnlockViewState) {
		self.state = state
	}

	func send(_ action: UnlockViewAction) {
		listener?.handle(unlockAction: action)
	}

	public func makeView() -> AnyView {
		AnyView(UnlockScreen(view: self))
	}
}

private struct UnlockScreen: View {
	let view: SwiftUIUnlockView

	private var state: UnlockViewState { view.state }

	var body: some View {
		ScrollView {
			VStack(spacing: DesignToken.Spacing.space4) {
				header
				KMPasswordField(
					text: Binding(
						get: { state.password },
						set: { view.send(UnlockViewActionUpdatePassword(password: $0)) }
					),
					placeholder: "마스터 비밀번호",
					showCopyAction: false
				)
				.disabled(state.isLoading)
				.accessibilityIdentifier("unlock.password")

				if let notice = state.biometricNotice {
					Text(notice)
						.font(DesignToken.Typography.caption)
						.foregroundColor(DesignToken.View.mutedForeground)
						.frame(maxWidth: .infinity, alignment: .leading)
				}
				if let error = state.error {
					ErrorText(error: error, identifier: "unlock.error")
				}
				KMButton(
					"잠금 해제",
					size: .lg,
					isLoading: state.isLoading,
					isDisabled: state.password.isEmpty,
					isFullWidth: true
				) { view.send(UnlockViewActionUnlock.shared) }
					.accessibilityIdentifier("unlock.submit")

				if state.isBiometricsAvailable {
					KMButton(
						BiometricUnlockMessages.shared.ACTION,
						variant: .outline,
						size: .lg,
						icon: "faceid",
						isDisabled: state.isLoading,
						isFullWidth: true
					) { view.send(UnlockViewActionUnlockWithBiometrics.shared) }
						.accessibilityIdentifier("unlock.biometric")
				}

				Button("로그아웃") { view.send(UnlockViewActionRequestLogout.shared) }
					.buttonStyle(.plain)
					.font(DesignToken.Typography.label.weight(.medium))
					.foregroundColor(DesignToken.View.mutedForeground)
					.frame(minHeight: 44)
					.disabled(state.isLoading)
					.accessibilityIdentifier("unlock.logout")
			}
			.frame(maxWidth: 400)
			.padding(DesignToken.Spacing.space4)
			.frame(maxWidth: .infinity)
		}
		.background(DesignToken.View.background)
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

	private var header: some View {
		VStack(spacing: DesignToken.Spacing.space2) {
			Image(systemName: DesignToken.Icon.vaultLock)
				.font(.system(size: 44))
				.foregroundColor(DesignToken.View.primary)
				.accessibilityHidden(true)
			Text("볼트가 잠겼습니다")
				.font(DesignToken.Typography.title.weight(.bold))
				.foregroundColor(DesignToken.View.foreground)
				.accessibilityAddTraits(.isHeader)
			Text(state.email)
				.font(DesignToken.Typography.label)
				.foregroundColor(DesignToken.View.mutedForeground)
		}
		.padding(.top, DesignToken.Spacing.space6)
		.padding(.bottom, DesignToken.Spacing.space2)
	}
}
