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
}

// MARK: - Interactor

@MainActor
final class SettingsInteractor: Interactor, SettingsInteractable, SettingsPresentableListener {
	weak var router: SettingsRouting?
	weak var listener: SettingsListener?
	weak var presenter: SettingsPresentable?
	let lockManager: VaultLockManagerProtocol
	let networkClient: AuthNetworkClientProtocol

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
}
