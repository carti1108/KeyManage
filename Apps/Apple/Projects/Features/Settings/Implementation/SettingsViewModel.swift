import Foundation
import KeyManageDomain

@MainActor
final class SettingsViewModel: ObservableObject {
	@Published var state = SettingsViewState()
	@Published var exportResult: (fileName: String, data: Data)?

	var selectedTimeout: AutoLockTimeout {
		get { state.selectedTimeout }
		set { state.selectedTimeout = newValue }
	}

	var toastMessage: String? {
		get { state.toastMessage }
		set { state.toastMessage = newValue }
	}

	var hasPendingSync: Bool {
		get { state.hasPendingSync }
		set { state.hasPendingSync = newValue }
	}

	var isRestoreModalPresented: Bool {
		get { state.isRestoreModalPresented }
		set { state.isRestoreModalPresented = newValue }
	}

	var isLogoutConfirmPresented: Bool {
		get { state.isLogoutConfirmPresented }
		set { state.isLogoutConfirmPresented = newValue }
	}

	var isChangePasswordPresented: Bool {
		get { state.isChangePasswordPresented }
		set { state.isChangePasswordPresented = newValue }
	}

	var currentPasswordInput: String {
		get { state.currentPasswordInput }
		set { state.currentPasswordInput = newValue }
	}

	var newPasswordInput: String {
		get { state.newPasswordInput }
		set { state.newPasswordInput = newValue }
	}

	var confirmPasswordInput: String {
		get { state.confirmPasswordInput }
		set { state.confirmPasswordInput = newValue }
	}

	var isDeleteAccountPresented: Bool {
		get { state.isDeleteAccountPresented }
		set { state.isDeleteAccountPresented = newValue }
	}

	var deleteAccountPasswordInput: String {
		get { state.deleteAccountPasswordInput }
		set { state.deleteAccountPasswordInput = newValue }
	}

	var deleteAccountConfirmationInput: String {
		get { state.deleteAccountConfirmationInput }
		set { state.deleteAccountConfirmationInput = newValue }
	}

	var deleteAccountErrorMessage: String? {
		get { state.deleteAccountErrorMessage }
		set { state.deleteAccountErrorMessage = newValue }
	}

	var isDeletingAccount: Bool {
		get { state.isDeletingAccount }
		set { state.isDeletingAccount = newValue }
	}

	private var toastDismissTask: Task<Void, Never>?

	var onAction: ((SettingsViewAction) -> Void)?
	var onSelectTimeout: ((AutoLockTimeout) -> Void)?
	var onRequestExport: (() -> Void)?
	var onRequestRestore: ((Data, String) -> Void)?
	var onRequestLogout: (() -> Void)?
	var onRequestChangePassword: ((String, String, String) -> Void)?
	var onRequestDeleteAccount: ((String, String) -> Void)?

	var canExecuteDeleteAccount: Bool {
		state.canExecuteDeleteAccount
	}

	func update(state: SettingsViewState) {
		self.state = state
	}

	func send(_ action: SettingsViewAction) {
		switch action {
		case .selectAutoLockTimeout(let timeout):
			state.selectedTimeout = timeout
			onAction?(action)
			onSelectTimeout?(timeout)
		case .requestExportBackup:
			onAction?(action)
			onRequestExport?()
		case .requestRestoreBackup(let fileData, let password):
			onAction?(action)
			onRequestRestore?(fileData, password)
		case .showPendingLogoutConfirmation:
			state.isLogoutConfirmPresented = true
		case .requestLogout:
			onAction?(action)
			onRequestLogout?()
		case .requestChangePassword(let current, let new, let confirm):
			onAction?(action)
			onRequestChangePassword?(current, new, confirm)
		case .requestDeleteAccount(let password, let confirmationPhrase):
			state.isDeletingAccount = true
			onAction?(action)
			onRequestDeleteAccount?(password, confirmationPhrase)
		case .setRestoreModalPresented(let isPresented):
			state.isRestoreModalPresented = isPresented
		case .setLogoutConfirmPresented(let isPresented):
			state.isLogoutConfirmPresented = isPresented
		case .setChangePasswordPresented(let isPresented):
			state.isChangePasswordPresented = isPresented
			if !isPresented {
				resetChangePasswordFields()
			}
		case .updateChangePasswordInputs(let current, let new, let confirm):
			state.currentPasswordInput = current
			state.newPasswordInput = new
			state.confirmPasswordInput = confirm
		case .startAccountDeletion:
			resetDeleteAccountFields()
			state.isDeleteAccountPresented = true
		case .cancelAccountDeletion:
			resetDeleteAccountFields()
			state.isDeleteAccountPresented = false
		case .updateDeleteAccountInputs(let password, let confirmation):
			state.deleteAccountPasswordInput = password
			state.deleteAccountConfirmationInput = confirmation
		case .showToast(let message):
			showToast(message)
		case .clearToast:
			clearToast()
		}
	}

	func startAccountDeletion() {
		send(.startAccountDeletion)
	}

	func cancelAccountDeletion() {
		send(.cancelAccountDeletion)
	}

	func executeDeleteAccount() {
		guard canExecuteDeleteAccount else { return }
		send(.requestDeleteAccount(
			password: state.deleteAccountPasswordInput,
			confirmationPhrase: state.deleteAccountConfirmationInput
		))
	}

	func resetDeleteAccountFields() {
		state.deleteAccountPasswordInput = ""
		state.deleteAccountConfirmationInput = ""
		state.deleteAccountErrorMessage = nil
		state.isDeletingAccount = false
	}

	func selectTimeout(_ timeout: AutoLockTimeout) {
		send(.selectAutoLockTimeout(timeout))
	}

	func exportBackup() {
		send(.requestExportBackup)
	}

	func restoreBackup(fileData: Data, password: String) {
		send(.requestRestoreBackup(fileData: fileData, password: password))
	}

	func showPendingLogoutConfirmation() {
		send(.showPendingLogoutConfirmation)
	}

	func confirmLogout() {
		send(.requestLogout)
	}

	func requestChangePassword() {
		send(.requestChangePassword(
			current: state.currentPasswordInput,
			new: state.newPasswordInput,
			confirm: state.confirmPasswordInput
		))
	}

	func resetChangePasswordFields() {
		state.currentPasswordInput = ""
		state.newPasswordInput = ""
		state.confirmPasswordInput = ""
	}

	func showToast(_ message: String) {
		toastDismissTask?.cancel()
		state.toastMessage = message
		toastDismissTask = Task { @MainActor [weak self] in
			try? await Task.sleep(nanoseconds: 3_000_000_000)
			guard !Task.isCancelled else { return }
			self?.state.toastMessage = nil
		}
	}

	func clearToast() {
		toastDismissTask?.cancel()
		state.toastMessage = nil
	}
}
