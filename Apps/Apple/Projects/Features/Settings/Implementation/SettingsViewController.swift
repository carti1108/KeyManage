import Foundation
import SwiftUI
import MicroRIBs
import KeyManageDomain
import SettingsFeatureInterface

// MARK: - PresentableListener

@MainActor
protocol SettingsPresentableListener: AnyObject {
	func request(action: SettingsViewAction)
	func didSelectAutoLockTimeout(_ timeout: AutoLockTimeout)
	func didRequestExportBackup()
	func didRequestRestoreBackup(fileData: Data, password: String)
	func didRequestLogout()
	func didRequestChangePassword(current: String, new: String, confirm: String)
	func didRequestDeleteAccount(password: String, confirmationPhrase: String)
}

@MainActor
extension SettingsPresentableListener {
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
}

#if os(macOS)
import AppKit
typealias SettingsPlatformHostingController<Content: View> = NSHostingController<Content>
#elseif os(iOS)
import UIKit
typealias SettingsPlatformHostingController<Content: View> = UIHostingController<Content>
#endif

@MainActor
final class SettingsViewController: SettingsPlatformHostingController<SettingsView>, SettingsViewControllable, SettingsPresentable {
	weak var listener: SettingsPresentableListener?
	let viewModel: SettingsViewModel

	init(listener: SettingsPresentableListener? = nil) {
		self.listener = listener
		let vm = SettingsViewModel()
		self.viewModel = vm
		super.init(rootView: SettingsView(viewModel: vm))

		vm.onAction = { [weak self] action in
			self?.listener?.request(action: action)
		}
	}

	@available(*, unavailable)
	required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func update(state: SettingsViewState) {
		viewModel.update(state: state)
	}

	func displayAutoLockTimeout(_ timeout: AutoLockTimeout) {
		viewModel.selectedTimeout = timeout
	}

	func showExportResult(fileName: String, data: Data) {
		viewModel.exportResult = (fileName, data)
#if os(macOS)
		let savePanel = NSSavePanel()
		savePanel.nameFieldStringValue = fileName
		savePanel.allowedContentTypes = [.json]
		savePanel.begin { [weak self] result in
			guard let self = self else { return }
			if result == .OK, let url = savePanel.url {
				do {
					try data.write(to: url, options: .atomic)
					Task { @MainActor in
						self.viewModel.showToast("백업 파일이 저장되었습니다.")
					}
				} catch {
					Task { @MainActor in
						self.viewModel.showToast("파일 저장 실패: \(error.localizedDescription)")
					}
				}
			}
		}
#elseif os(iOS)
		let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
		do {
			try data.write(to: tempURL, options: .atomic)
			let activityVC = UIActivityViewController(activityItems: [tempURL], applicationActivities: nil)
			if let popover = activityVC.popoverPresentationController {
				popover.sourceView = self.view
				popover.sourceRect = CGRect(x: self.view.bounds.midX, y: self.view.bounds.midY, width: 0, height: 0)
				popover.permittedArrowDirections = []
			}
			activityVC.completionWithItemsHandler = { _, _, _, _ in
				try? FileManager.default.removeItem(at: tempURL)
			}
			present(activityVC, animated: true)
		} catch {
			viewModel.showToast("파일 준비 실패: \(error.localizedDescription)")
		}
#endif
	}

	func showToast(message: String) {
		viewModel.showToast(message)
	}

	func dismissChangePasswordModal() {
		viewModel.isChangePasswordPresented = false
		viewModel.resetChangePasswordFields()
	}

	func dismissDeleteAccountModal() {
		viewModel.isDeleteAccountPresented = false
		viewModel.resetDeleteAccountFields()
	}

	func showDeleteAccountError(message: String) {
		viewModel.deleteAccountErrorMessage = message
		viewModel.isDeletingAccount = false
	}

	func setHasPendingSync(_ hasPendingSync: Bool) {
		viewModel.hasPendingSync = hasPendingSync
	}
}
