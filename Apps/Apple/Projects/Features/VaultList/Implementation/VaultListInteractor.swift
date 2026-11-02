import Foundation
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface

// MARK: - Presentable Protocols

@MainActor
protocol VaultListPresentable: AnyObject {
	var listener: VaultListPresentableListener? { get set }
	func displayItems(_ items: [VaultItem])
	func showToast(message: String)
}

// MARK: - Interactor

@MainActor
final class VaultListInteractor: Interactor, VaultListInteractable, VaultListPresentableListener {
	weak var router: VaultListRouting?
	weak var listener: VaultListListener?
	weak var presenter: VaultListPresentable?
	let lockManager: VaultLockManagerProtocol
	let clipboardManager: ClipboardManagerProtocol

	private var allItems: [VaultItem] {
		lockManager.decryptedVault?.items ?? []
	}

	init(
		lockManager: VaultLockManagerProtocol,
		clipboardManager: ClipboardManagerProtocol,
		presenter: VaultListPresentable? = nil
	) {
		self.lockManager = lockManager
		self.clipboardManager = clipboardManager
		self.presenter = presenter
		super.init()
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		presenter?.displayItems(allItems)
	}

	func didSearch(query: String) {
		let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
		if trimmed.isEmpty {
			presenter?.displayItems(allItems)
		} else {
			let filtered = allItems.filter { item in
				item.serviceName.localizedCaseInsensitiveContains(trimmed) ||
				item.username.localizedCaseInsensitiveContains(trimmed)
			}
			presenter?.displayItems(filtered)
		}
	}

	func didRequestCopyPassword(item: VaultItem) {
		do {
			try clipboardManager.copy(item.password)
			presenter?.showToast(message: "비밀번호가 복사되었습니다. (30초 후 자동 소각)")
		} catch {
			// ignore or handle
		}
	}

	func didRequestCopyUsername(item: VaultItem) {
		do {
			try clipboardManager.copy(item.username)
			presenter?.showToast(message: "아이디가 복사되었습니다.")
		} catch {
			// ignore or handle
		}
	}
}
