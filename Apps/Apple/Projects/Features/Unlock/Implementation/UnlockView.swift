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
		VStack(spacing: 24) {
			Text(VaultLockUIStrings.lockedTitle)
				.font(.title2.bold())
				.foregroundColor(DesignToken.View.foreground)
				.accessibilityAddTraits(.isHeader)

			KMCard(padding: .lg) {
				VStack(alignment: .leading, spacing: 16) {
					KMPasswordField(
						text: $viewModel.password,
						placeholder: "마스터 비밀번호 입력",
						showCopyAction: false
					)

					if let errorMessage = viewModel.errorMessage, !errorMessage.isEmpty {
						Text(errorMessage)
							.font(.footnote)
							.foregroundColor(DesignToken.View.destructive)
							.accessibilityLabel("오류 안내: \(errorMessage)")
					}

					KMButton(
						VaultLockUIStrings.unlockButtonTitle,
						variant: .primary,
						isLoading: viewModel.isLoading,
						isDisabled: viewModel.isLoading || viewModel.password.isEmpty
					) {
						viewModel.unlockWithPassword()
					}

					if viewModel.isBiometricsEnabled {
						KMButton(
							VaultLockUIStrings.biometricUnlockButtonTitle,
							variant: .outline,
							icon: DesignToken.Icon.vaultLock,
							isDisabled: viewModel.isLoading
						) {
							viewModel.unlockWithBiometrics()
						}
					}
				}
			}
			.frame(maxWidth: 400)
		}
		.padding(32)
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.background(DesignToken.View.background)
	}
}
