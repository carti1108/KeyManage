import Foundation
import MicroRIBs
import KeyManageDomain
import SettingsFeatureInterface

// MARK: - Presentable Protocols

@MainActor
protocol SettingsPresentable: AnyObject {
	var listener: SettingsPresentableListener? { get set }
	func displayAutoLockTimeout(_ timeout: AutoLockTimeout)
	func showExportResult(fileName: String, data: Data)
	func showToast(message: String)
	func dismissChangePasswordModal()
	func dismissDeleteAccountModal()
	func showDeleteAccountError(message: String)
	func setHasPendingSync(_ hasPendingSync: Bool)
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
		presenter?.displayAutoLockTimeout(lockManager.autoLockTimeout)
		presenter?.setHasPendingSync(lockManager.hasPendingSync)
	}

	override func willResignActive() {
		super.willResignActive()
		deleteAccountTask?.cancel()
		deleteAccountTask = nil
	}

	func didSelectAutoLockTimeout(_ timeout: AutoLockTimeout) {
		lockManager.autoLockTimeout = timeout
		presenter?.displayAutoLockTimeout(timeout)
	}

	func didRequestExportBackup() {
		do {
			let (fileName, data) = try lockManager.exportBackup()
			presenter?.showExportResult(fileName: fileName, data: data)
		} catch {
			presenter?.showToast(message: error.localizedDescription)
		}
	}

	func didRequestRestoreBackup(fileData: Data, password: String) {
		do {
			let (restoredCount, _) = try lockManager.restoreBackup(fileData: fileData, password: password)
			let message = KeyManageBackupStrings.restoreCompletedMessage(count: restoredCount)
			presenter?.showToast(message: message)
		} catch {
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
