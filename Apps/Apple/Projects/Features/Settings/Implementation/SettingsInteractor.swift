import Foundation
import MicroRIBs
import KeyManageDomain
import SettingsFeatureInterface

// MARK: - Presentable Protocols

@MainActor
protocol SettingsPresentable: AnyObject {
	var listener: SettingsPresentableListener? { get set }
	func update(state: SettingsViewState)
	func displayAutoLockTimeout(_ timeout: AutoLockTimeout)
	func showExportResult(fileName: String, data: Data)
	func showToast(message: String)
	func dismissChangePasswordModal()
	func dismissDeleteAccountModal()
	func showDeleteAccountError(message: String)
	func setHasPendingSync(_ hasPendingSync: Bool)
}

@MainActor
extension SettingsPresentable {
	func update(state: SettingsViewState) {}
}

// MARK: - Interactor

@MainActor
final class SettingsInteractor: Interactor, SettingsInteractable, SettingsPresentableListener {
	weak var router: SettingsRouting?
	weak var listener: SettingsListener?
	weak var presenter: SettingsPresentable?
	let lockManager: VaultLockManagerProtocol
	let networkClient: AuthNetworkClientProtocol
	private var deleteAccountTask: Task<Void, Never>?
	private(set) var state = SettingsViewState()

	init(
		lockManager: VaultLockManagerProtocol,
		networkClient: AuthNetworkClientProtocol = URLSessionAuthNetworkClient(),
		presenter: SettingsPresentable? = nil
	) {
		self.lockManager = lockManager
		self.networkClient = networkClient
		self.presenter = presenter
		super.init()
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		state.selectedTimeout = lockManager.autoLockTimeout
		state.hasPendingSync = lockManager.hasPendingSync
		presenter?.update(state: state)
		presenter?.displayAutoLockTimeout(lockManager.autoLockTimeout)
		presenter?.setHasPendingSync(lockManager.hasPendingSync)
	}

	override func willResignActive() {
		super.willResignActive()
		deleteAccountTask?.cancel()
		deleteAccountTask = nil
	}

	func request(action: SettingsViewAction) {
		switch action {
		case .selectAutoLockTimeout(let timeout):
			didSelectAutoLockTimeout(timeout)
		case .requestExportBackup:
			didRequestExportBackup()
		case .requestRestoreBackup(let fileData, let password):
			didRequestRestoreBackup(fileData: fileData, password: password)
		case .requestLogout:
			didRequestLogout()
		case .requestChangePassword(let current, let new, let confirm):
			didRequestChangePassword(current: current, new: new, confirm: confirm)
		case .requestDeleteAccount(let password, let confirmationPhrase):
			didRequestDeleteAccount(password: password, confirmationPhrase: confirmationPhrase)
		default:
			break
		}
	}

	func didSelectAutoLockTimeout(_ timeout: AutoLockTimeout) {
		lockManager.autoLockTimeout = timeout
		state.selectedTimeout = timeout
		presenter?.update(state: state)
		presenter?.displayAutoLockTimeout(timeout)
	}

	func didRequestExportBackup() {
		do {
			let (fileName, data) = try lockManager.exportBackup()
			presenter?.showExportResult(fileName: fileName, data: data)
		} catch {
			state.toastMessage = error.localizedDescription
			presenter?.update(state: state)
			presenter?.showToast(message: error.localizedDescription)
		}
	}

	func didRequestRestoreBackup(fileData: Data, password: String) {
		do {
			let (restoredCount, _) = try lockManager.restoreBackup(fileData: fileData, password: password)
			let message = KeyManageBackupStrings.restoreCompletedMessage(count: restoredCount)
			state.toastMessage = message
			presenter?.update(state: state)
			presenter?.showToast(message: message)
		} catch {
			state.toastMessage = error.localizedDescription
			presenter?.update(state: state)
			presenter?.showToast(message: error.localizedDescription)
		}
	}

	func didRequestChangePassword(current: String, new: String, confirm: String) {
		guard !current.isEmpty else {
			presenter?.showToast(message: MasterPasswordChangeStrings.invalidCurrentPasswordMessage)
			return
		}
		guard new.count >= 12 else {
			presenter?.showToast(message: MasterPasswordChangeStrings.tooShortMessage)
			return
		}
		guard new != current else {
			presenter?.showToast(message: MasterPasswordChangeStrings.samePasswordMessage)
			return
		}
		guard new == confirm else {
			presenter?.showToast(message: MasterPasswordChangeStrings.confirmationMismatchMessage)
			return
		}

		Task {
			do {
				try await lockManager.changeMasterPassword(
					currentPassword: current,
					newPassword: new,
					networkClient: networkClient
				)
				presenter?.dismissChangePasswordModal()
				presenter?.showToast(message: MasterPasswordChangeStrings.completedMessage)
			} catch let error as MasterPasswordChangeError {
				presenter?.showToast(message: error.localizedDescription)
			} catch {
				presenter?.showToast(message: error.localizedDescription)
			}
		}
	}

	func didRequestLogout() {
		lockManager.logout()
		listener?.settingsDidRequestLogout()
	}

	func didRequestDeleteAccount(password: String, confirmationPhrase: String) {
		deleteAccountTask = Task { [weak self] in
			guard let self else { return }
			do {
				try await self.lockManager.deleteAccount(
					password: password,
					confirmationPhrase: confirmationPhrase,
					networkClient: self.networkClient
				)
				guard !Task.isCancelled else { return }
				self.presenter?.dismissDeleteAccountModal()
				self.presenter?.showToast(message: AccountDeletionStrings.successMessage)
				self.listener?.settingsDidRequestLogout()
			} catch let error as LocalizedError {
				guard !Task.isCancelled else { return }
				self.presenter?.showDeleteAccountError(message: error.errorDescription ?? error.localizedDescription)
			} catch {
				guard !Task.isCancelled else { return }
				self.presenter?.showDeleteAccountError(message: error.localizedDescription)
			}
		}
	}
}
