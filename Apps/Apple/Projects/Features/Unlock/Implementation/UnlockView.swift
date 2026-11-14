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
		VStack(spacing: DesignToken.Spacing.space6) {
			Text(VaultLockUIStrings.lockedTitle)
				.font(DesignToken.Typography.title.weight(.bold))
				.foregroundColor(DesignToken.View.foreground)
				.accessibilityAddTraits(.isHeader)

			KMCard(padding: .lg) {
				VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
					KMPasswordField(
						text: Binding(
							get: { viewModel.state.password },
							set: { viewModel.request(action: .updatePassword($0)) }
						),
						placeholder: "마스터 비밀번호 입력",
						showCopyAction: false
					)

					if let errorMessage = viewModel.state.errorMessage, !errorMessage.isEmpty {
						Text(errorMessage)
							.font(DesignToken.Typography.caption)
							.foregroundColor(DesignToken.View.destructive)
							.accessibilityLabel("오류 안내: \(errorMessage)")
					}

					KMButton(
						VaultLockUIStrings.unlockButtonTitle,
						variant: .primary,
						isLoading: viewModel.state.isLoading,
						isDisabled: viewModel.state.isLoading || viewModel.state.password.isEmpty
					) {
						viewModel.request(action: .unlockWithPassword(viewModel.state.password))
					}

					if viewModel.state.isBiometricsEnabled {
						KMButton(
							VaultLockUIStrings.biometricUnlockButtonTitle,
							variant: .outline,
							icon: DesignToken.Icon.vaultLock,
							isDisabled: viewModel.state.isLoading
						) {
							viewModel.request(action: .unlockWithBiometrics)
						}
					}
				}
			}
			.frame(maxWidth: 400)
		}
		.padding(DesignToken.Spacing.space6)
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.background(DesignToken.View.background)
	}
}
