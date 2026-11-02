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
}

// MARK: - Interactor

@MainActor
final class SettingsInteractor: Interactor, SettingsInteractable, SettingsPresentableListener {
	weak var router: SettingsRouting?
	weak var listener: SettingsListener?
	weak var presenter: SettingsPresentable?
	let lockManager: VaultLockManagerProtocol

	init(
		lockManager: VaultLockManagerProtocol,
		presenter: SettingsPresentable? = nil
	) {
		self.lockManager = lockManager
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

	func didRequestLogout() {
		lockManager.logout()
		listener?.settingsDidRequestLogout()
	}
}
