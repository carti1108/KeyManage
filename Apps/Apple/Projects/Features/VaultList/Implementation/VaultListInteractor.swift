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
	func setIsReadOnly(_ isReadOnly: Bool)
	func dismissItemSheet()
	func setSyncStatus(hasPending: Bool, isOffline: Bool, isSyncing: Bool)
}

// MARK: - Interactor

@MainActor
final class VaultListInteractor: Interactor, VaultListInteractable, VaultListPresentableListener {
	weak var router: VaultListRouting?
	weak var listener: VaultListListener?
	weak var presenter: VaultListPresentable?
	let lockManager: VaultLockManagerProtocol
	let clipboardManager: ClipboardManagerProtocol
	let networkClient: AuthNetworkClientProtocol

	private var currentSearchQuery: String = ""

	private var allItems: [VaultItem] {
		(lockManager.decryptedVault?.items ?? []).filter { !$0.isDeleted }
	}

	init(
		lockManager: VaultLockManagerProtocol,
		clipboardManager: ClipboardManagerProtocol,
		networkClient: AuthNetworkClientProtocol = URLSessionAuthNetworkClient(),
		presenter: VaultListPresentable? = nil
	) {
		self.lockManager = lockManager
		self.clipboardManager = clipboardManager
		self.networkClient = networkClient
		self.presenter = presenter
		super.init()
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		presenter?.setIsReadOnly(lockManager.decryptedVault?.isReadOnly ?? false)
		refreshList()
		updateSyncStatus()
		Task { [weak self] in
			await self?.didRequestSync()
		}
	}

	func didSearch(query: String) {
		currentSearchQuery = query
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

	private func refreshList() {
		didSearch(query: currentSearchQuery)
	}

	func didRequestCopyPassword(item: VaultItem) {
		do {
			try clipboardManager.copy(item.password)
			presenter?.showToast(message: "비밀번호가 복사되었습니다. (30초 후 자동 소각)")
		} catch {
			presenter?.showToast(message: ClipboardStrings.copyFailedMessage)
		}
	}

	func didRequestCopyUsername(item: VaultItem) {
		do {
			try clipboardManager.copy(item.username)
			presenter?.showToast(message: "아이디가 복사되었습니다.")
		} catch {
			presenter?.showToast(message: ClipboardStrings.copyFailedMessage)
		}
	}

	func didRequestSaveItem(
		existingId: String?,
		serviceName: String,
		username: String,
		password: String,
		url: String?,
		notes: String?
	) {
		do {
			if let id = existingId {
				guard let currentItem = allItems.first(where: { $0.id == id }) else {
					throw VaultItemError.itemNotFound
				}
				var updatedItem = currentItem
				updatedItem.serviceName = serviceName
				updatedItem.username = username
				updatedItem.password = password
				updatedItem.url = url
				updatedItem.notes = notes
				try lockManager.updateVaultItem(item: updatedItem)
				presenter?.showToast(message: "항목이 수정되었습니다.")
			} else {
				try lockManager.addVaultItem(
					serviceName: serviceName,
					username: username,
					password: password,
					url: url,
					notes: notes
				)
				presenter?.showToast(message: "항목이 추가되었습니다.")
			}
			presenter?.dismissItemSheet()
			refreshList()
			updateSyncStatus()
			Task { [weak self] in
				await self?.didRequestSync()
			}
		} catch let error as LocalizedError {
			presenter?.showToast(message: error.errorDescription ?? error.localizedDescription)
		} catch {
			presenter?.showToast(message: error.localizedDescription)
		}
	}

	func didRequestDeleteItem(id: String) {
		do {
			try lockManager.deleteVaultItem(id: id)
			presenter?.showToast(message: "항목이 삭제되었습니다.")
			presenter?.dismissItemSheet()
			refreshList()
			updateSyncStatus()
			Task { [weak self] in
				await self?.didRequestSync()
			}
		} catch let error as LocalizedError {
			presenter?.showToast(message: error.errorDescription ?? error.localizedDescription)
		} catch {
			presenter?.showToast(message: error.localizedDescription)
		}
	}

	func didRequestSync() async {
		updateSyncStatus()
		do {
			_ = try await lockManager.sync(networkClient: networkClient)
			refreshList()
			updateSyncStatus()
		} catch let error as LocalizedError {
			updateSyncStatus()
			presenter?.showToast(message: error.errorDescription ?? error.localizedDescription)
		} catch {
			updateSyncStatus()
			presenter?.showToast(message: error.localizedDescription)
		}
	}

	private func updateSyncStatus() {
		presenter?.setSyncStatus(
			hasPending: lockManager.hasPendingSync,
			isOffline: lockManager.isOffline,
			isSyncing: lockManager.isSyncing
		)
	}
}
