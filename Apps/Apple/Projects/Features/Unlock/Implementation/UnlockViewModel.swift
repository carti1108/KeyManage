import Foundation

@MainActor
public final class UnlockViewModel: ObservableObject {
	@Published public var password: String = ""
	@Published public var errorMessage: String?
	@Published public var isLoading: Bool = false
	public let isBiometricsEnabled: Bool
	public var onUnlockPassword: ((String) -> Void)?
	public var onUnlockBiometrics: (() -> Void)?

	public init(
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

	public func unlockWithPassword() {
		onUnlockPassword?(password)
	}

	public func unlockWithBiometrics() {
		onUnlockBiometrics?()
	}

	public func clearPassword() {
		password = ""
	}
}
