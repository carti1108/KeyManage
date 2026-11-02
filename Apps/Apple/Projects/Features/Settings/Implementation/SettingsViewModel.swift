import Foundation
import KeyManageDomain

@MainActor
final class SettingsViewModel: ObservableObject {
	@Published var selectedTimeout: AutoLockTimeout = .default
	@Published var toastMessage: String?
	@Published var isRestoreModalPresented: Bool = false
	@Published var isLogoutConfirmPresented: Bool = false
	@Published var isChangePasswordPresented: Bool = false
	@Published var currentPasswordInput: String = ""
	@Published var newPasswordInput: String = ""
	@Published var confirmPasswordInput: String = ""
	@Published var exportResult: (fileName: String, data: Data)?

	// SEC-005 계정 삭제
	@Published var isDeleteAccountPresented: Bool = false
	@Published var deleteAccountPasswordInput: String = ""
	@Published var deleteAccountConfirmationInput: String = ""
	@Published var deleteAccountErrorMessage: String?
	@Published var isDeletingAccount: Bool = false

	private var toastDismissTask: Task<Void, Never>?

	var onSelectTimeout: ((AutoLockTimeout) -> Void)?
	var onRequestExport: (() -> Void)?
	var onRequestRestore: ((Data, String) -> Void)?
	var onRequestLogout: (() -> Void)?
	var onRequestChangePassword: ((String, String, String) -> Void)?
	var onRequestDeleteAccount: ((String, String) -> Void)?

	var canExecuteDeleteAccount: Bool {
		deleteAccountConfirmationInput == AccountDeletionStrings.confirmationPhrase &&
		!deleteAccountPasswordInput.isEmpty
	}

	func startAccountDeletion() {
		resetDeleteAccountFields()
		isDeleteAccountPresented = true
	}

	func cancelAccountDeletion() {
		resetDeleteAccountFields()
		isDeleteAccountPresented = false
	}

	func executeDeleteAccount() {
		guard canExecuteDeleteAccount else { return }
		isDeletingAccount = true
		onRequestDeleteAccount?(deleteAccountPasswordInput, deleteAccountConfirmationInput)
	}

	func resetDeleteAccountFields() {
		deleteAccountPasswordInput = ""
		deleteAccountConfirmationInput = ""
		deleteAccountErrorMessage = nil
		isDeletingAccount = false
	}

	func selectTimeout(_ timeout: AutoLockTimeout) {
		selectedTimeout = timeout
		onSelectTimeout?(timeout)
	}

	func exportBackup() {
		onRequestExport?()
	}

	func restoreBackup(fileData: Data, password: String) {
		onRequestRestore?(fileData, password)
	}

	func confirmLogout() {
		onRequestLogout?()
	}

	func requestChangePassword() {
		onRequestChangePassword?(currentPasswordInput, newPasswordInput, confirmPasswordInput)
	}

	func resetChangePasswordFields() {
		currentPasswordInput = ""
		newPasswordInput = ""
		confirmPasswordInput = ""
	}

	func showToast(_ message: String) {
		toastDismissTask?.cancel()
		toastMessage = message
		toastDismissTask = Task { @MainActor [weak self] in
			try? await Task.sleep(nanoseconds: 3_000_000_000)
			guard !Task.isCancelled else { return }
			self?.toastMessage = nil
		}
	}

	func clearToast() {
		toastDismissTask?.cancel()
		toastMessage = nil
	}
}
