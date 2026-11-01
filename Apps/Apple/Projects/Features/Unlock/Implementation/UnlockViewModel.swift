import Foundation

@MainActor
final class UnlockViewModel: ObservableObject {
	@Published var password: String = ""
	@Published var errorMessage: String?
	@Published var isLoading: Bool = false
	let isBiometricsEnabled: Bool
	var onUnlockPassword: ((String) -> Void)?
	var onUnlockBiometrics: (() -> Void)?

	init(
		isBiometricsEnabled: Bool = false,
		errorMessage: String? = nil,
		isLoading: Bool = false,
		onUnlockPassword: ((String) -> Void)? = nil,
		onUnlockBiometrics: (() -> Void)? = nil
	) {
		self.isBiometricsEnabled = isBiometricsEnabled
		self.errorMessage = errorMessage
		self.isLoading = isLoading
		self.onUnlockPassword = onUnlockPassword
		self.onUnlockBiometrics = onUnlockBiometrics
	}

	func unlockWithPassword() {
		onUnlockPassword?(password)
	}

	func unlockWithBiometrics() {
		onUnlockBiometrics?()
	}

	func clearPassword() {
		password = ""
	}
}
