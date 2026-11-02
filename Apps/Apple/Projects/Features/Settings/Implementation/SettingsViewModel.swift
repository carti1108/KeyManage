import Foundation
import KeyManageDomain

@MainActor
final class SettingsViewModel: ObservableObject {
	@Published var selectedTimeout: AutoLockTimeout = .default
	@Published var toastMessage: String?
	@Published var isRestoreModalPresented: Bool = false
	@Published var isLogoutConfirmPresented: Bool = false
	@Published var exportResult: (fileName: String, data: Data)?

	private var toastDismissTask: Task<Void, Never>?

	var onSelectTimeout: ((AutoLockTimeout) -> Void)?
	var onRequestExport: (() -> Void)?
	var onRequestRestore: ((Data, String) -> Void)?
	var onRequestLogout: (() -> Void)?

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
