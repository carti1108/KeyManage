import SwiftUI
import KeyManageDomain
import KeyManageDesignSystem

struct UnlockView: View {
	@ObservedObject var viewModel: UnlockViewModel

	init(viewModel: UnlockViewModel) {
		self.viewModel = viewModel
	}

	#if DEBUG
	init(
		errorMessage: String? = nil,
		isLoading: Bool = false,
		isBiometricsEnabled: Bool = false,
		onUnlockPassword: ((String) -> Void)? = nil,
		onUnlockBiometrics: (() -> Void)? = nil
	) {
		self.init(viewModel: UnlockViewModel(
			isBiometricsEnabled: isBiometricsEnabled,
			errorMessage: errorMessage,
			isLoading: isLoading,
			onUnlockPassword: onUnlockPassword,
			onUnlockBiometrics: onUnlockBiometrics
		))
	}
	#endif

	var body: some View {
		VStack(spacing: DesignToken.Spacing.space4) {
			header

			KMPasswordField(
				text: Binding(
					get: { viewModel.state.password },
					set: { viewModel.request(action: .updatePassword($0)) }
				),
				placeholder: "마스터 비밀번호",
				showCopyAction: false
			)

			if let errorMessage = viewModel.state.errorMessage, !errorMessage.isEmpty {
				Text(errorMessage)
					.font(DesignToken.Typography.caption)
					.foregroundColor(DesignToken.View.destructive)
					.frame(maxWidth: .infinity, alignment: .leading)
					.accessibilityLabel("오류 안내: \(errorMessage)")
			}

			KMButton(
				VaultLockUIStrings.unlockButtonTitle,
				variant: .primary,
				size: .lg,
				isLoading: viewModel.state.isLoading,
				isDisabled: viewModel.state.isLoading || viewModel.state.password.isEmpty,
				isFullWidth: true
			) {
				viewModel.request(action: .unlockWithPassword(viewModel.state.password))
			}

			if viewModel.state.isBiometricsEnabled {
				KMButton(
					VaultLockUIStrings.biometricUnlockButtonTitle,
					variant: .secondary,
					size: .lg,
					icon: DesignToken.Icon.vaultLock,
					isDisabled: viewModel.state.isLoading,
					isFullWidth: true
				) {
					viewModel.request(action: .unlockWithBiometrics)
				}
			}

			Button(VaultLockUIStrings.logoutButtonTitle) {
				viewModel.request(action: .logout)
			}
			.buttonStyle(.plain)
			.font(DesignToken.Typography.label.weight(.medium))
			.foregroundColor(DesignToken.View.mutedForeground)
			.frame(minHeight: 44)
			.disabled(viewModel.state.isLoading)
		}
		.frame(maxWidth: 400)
		.padding(DesignToken.Spacing.space4)
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.background(DesignToken.View.background)
	}

	private var header: some View {
		VStack(spacing: DesignToken.Spacing.space2) {
			Image(systemName: DesignToken.Icon.vaultLock)
				.font(.system(size: 44))
				.foregroundColor(DesignToken.View.primary)
				.accessibilityHidden(true)

			Text(VaultLockUIStrings.lockedTitle)
				.font(DesignToken.Typography.title.weight(.bold))
				.foregroundColor(DesignToken.View.foreground)
				.accessibilityAddTraits(.isHeader)

			if let email = viewModel.state.email {
				Text(email)
					.font(DesignToken.Typography.label)
					.foregroundColor(DesignToken.View.mutedForeground)
			}
		}
		.padding(.bottom, DesignToken.Spacing.space2)
	}
}
