import Foundation
import KeyManageDomain

// MARK: - ViewState

struct SettingsViewState: Equatable, Sendable {
	var selectedTimeout: AutoLockTimeout = .default
	var toastMessage: String?
	var hasPendingSync: Bool = false
	var isRestoreModalPresented: Bool = false
	var isLogoutConfirmPresented: Bool = false
	var isChangePasswordPresented: Bool = false
	var currentPasswordInput: String = ""
	var newPasswordInput: String = ""
	var confirmPasswordInput: String = ""
	var isDeleteAccountPresented: Bool = false
	var deleteAccountPasswordInput: String = ""
	var deleteAccountConfirmationInput: String = ""
	var deleteAccountErrorMessage: String?
	var isDeletingAccount: Bool = false

	var canExecuteDeleteAccount: Bool {
		deleteAccountConfirmationInput == AccountDeletionStrings.confirmationPhrase &&
		!deleteAccountPasswordInput.isEmpty
	}
}

// MARK: - ViewAction

enum SettingsViewAction: Equatable, Sendable {
	case selectAutoLockTimeout(AutoLockTimeout)
	case requestExportBackup
	case requestRestoreBackup(fileData: Data, password: String)
	case showPendingLogoutConfirmation
	case requestLogout
	case requestChangePassword(current: String, new: String, confirm: String)
	case requestDeleteAccount(password: String, confirmationPhrase: String)
	case setRestoreModalPresented(Bool)
	case setLogoutConfirmPresented(Bool)
	case setChangePasswordPresented(Bool)
	case updateChangePasswordInputs(current: String, new: String, confirm: String)
	case startAccountDeletion
	case cancelAccountDeletion
	case updateDeleteAccountInputs(password: String, confirmation: String)
	case showToast(String)
	case clearToast
}
