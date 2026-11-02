import Foundation
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface

// MARK: - Presentable Protocols

@MainActor
protocol VaultListPresentable: AnyObject {
	var listener: VaultListPresentableListener? { get set }
	func update(state: VaultListViewState)
	func displayItems(_ items: [VaultItem])
	func showToast(message: String)
	func setIsReadOnly(_ isReadOnly: Bool)
	func dismissItemSheet()
	func setSyncStatus(hasPending: Bool, isOffline: Bool, isSyncing: Bool)
}

extension VaultListPresentable {
	func update(state: VaultListViewState) {}
	func displayItems(_ items: [VaultItem]) {}
	func showToast(message: String) {}
	func setIsReadOnly(_ isReadOnly: Bool) {}
	func dismissItemSheet() {}
	func setSyncStatus(hasPending: Bool, isOffline: Bool, isSyncing: Bool) {}
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

	private(set) var state = VaultListViewState()
	private var toastDismissTask: Task<Void, Never>?

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
		presenter?.listener = self
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		request(action: .viewDidLoad)
	}

	override func willResignActive() {
		super.willResignActive()
		toastDismissTask?.cancel()
		toastDismissTask = nil
	}

	func request(action: VaultListViewAction) {
		switch action {
		case .viewDidLoad:
			let readOnly = lockManager.decryptedVault?.isReadOnly ?? false
			state.isReadOnly = readOnly
			presenter?.setIsReadOnly(readOnly)
			refreshList()
			updateSyncStatus()
			Task { [weak self] in
				await self?.didRequestSync()
			}
		case .search(let query):
			didSearch(query: query)
		case .copyPassword(let item):
			didRequestCopyPassword(item: item)
		case .copyUsername(let item):
			didRequestCopyUsername(item: item)
		case .togglePasswordVisibility(let id):
			if state.revealedPasswordIds.contains(id) {
				state.revealedPasswordIds.remove(id)
			} else {
				state.revealedPasswordIds.insert(id)
			}
			presenter?.update(state: state)
		case .startCreatingItem:
			resetFormState()
			state.isItemSheetPresented = true
			presenter?.update(state: state)
		case .startEditingItem(let item):
			state.editingItem = item
			state.formServiceName = item.serviceName
			state.formUsername = item.username
			state.formPassword = item.password
			state.formUrl = item.url ?? ""
			state.formNotes = item.notes ?? ""
			state.formErrorMessage = nil
			state.isItemSheetPresented = true
			presenter?.update(state: state)
		case .cancelEditing:
			state.isItemSheetPresented = false
			resetFormState()
			presenter?.dismissItemSheet()
			presenter?.update(state: state)
		case .generatePasswordForForm:
			if let generated = try? PasswordGenerator.generate() {
				state.formPassword = generated
				presenter?.update(state: state)
			}
		case .updateFormServiceName(let value):
			state.formServiceName = value
			presenter?.update(state: state)
		case .updateFormUsername(let value):
			state.formUsername = value
			presenter?.update(state: state)
		case .updateFormPassword(let value):
			state.formPassword = value
			presenter?.update(state: state)
		case .updateFormUrl(let value):
			state.formUrl = value
			presenter?.update(state: state)
		case .updateFormNotes(let value):
			state.formNotes = value
			presenter?.update(state: state)
		case .setItemSheetPresented(let presented):
			state.isItemSheetPresented = presented
			if !presented {
				resetFormState()
			}
			presenter?.update(state: state)
		case .requestSaveItem:
			let trimmedService = state.formServiceName.trimmingCharacters(in: .whitespacesAndNewlines)
			guard !trimmedService.isEmpty else {
				state.formErrorMessage = VaultItemError.emptyServiceName.errorDescription
				presenter?.update(state: state)
				return
			}
			didRequestSaveItem(
				existingId: state.editingItem?.id,
				serviceName: trimmedService,
				username: state.formUsername,
				password: state.formPassword,
				url: state.formUrl.isEmpty ? nil : state.formUrl,
				notes: state.formNotes.isEmpty ? nil : state.formNotes
			)
		case .saveItem(let existingId, let serviceName, let username, let password, let url, let notes):
			didRequestSaveItem(
				existingId: existingId,
				serviceName: serviceName,
				username: username,
				password: password,
				url: url,
				notes: notes
			)
		case .confirmDeleteItem(let item):
			state.itemToDelete = item
			state.isDeleteAlertPresented = true
			presenter?.update(state: state)
		case .cancelDeleteItem:
			state.itemToDelete = nil
			state.isDeleteAlertPresented = false
			presenter?.update(state: state)
		case .setDeleteAlertPresented(let presented):
			state.isDeleteAlertPresented = presented
			if !presented {
				state.itemToDelete = nil
			}
			presenter?.update(state: state)
		case .executeDeleteItem:
			guard let item = state.itemToDelete else { return }
			state.itemToDelete = nil
			state.isDeleteAlertPresented = false
			didRequestDeleteItem(id: item.id)
		case .deleteItem(let id):
			didRequestDeleteItem(id: id)
		case .requestSync:
			Task { [weak self] in
				await self?.didRequestSync()
			}
		}
	}

	func didSearch(query: String) {
		state.searchQuery = query
		let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
		if trimmed.isEmpty {
			state.items = allItems
		} else {
			state.items = allItems.filter { item in
				item.serviceName.localizedCaseInsensitiveContains(trimmed) ||
				item.username.localizedCaseInsensitiveContains(trimmed)
			}
		}
		presenter?.displayItems(state.items)
		presenter?.update(state: state)
	}

	private func refreshList() {
		didSearch(query: state.searchQuery)
	}

	func didRequestCopyPassword(item: VaultItem) {
		do {
			try clipboardManager.copy(item.password)
			presentToast(message: "비밀번호가 복사되었습니다. (30초 후 자동 소각)")
		} catch {
			presentToast(message: ClipboardStrings.copyFailedMessage)
		}
	}

	func didRequestCopyUsername(item: VaultItem) {
		do {
			try clipboardManager.copy(item.username)
			presentToast(message: "아이디가 복사되었습니다.")
		} catch {
			presentToast(message: ClipboardStrings.copyFailedMessage)
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
				presentToast(message: "항목이 수정되었습니다.")
			} else {
				try lockManager.addVaultItem(
					serviceName: serviceName,
					username: username,
					password: password,
					url: url,
					notes: notes
				)
				presentToast(message: "항목이 추가되었습니다.")
			}
			state.isItemSheetPresented = false
			resetFormState()
			presenter?.dismissItemSheet()
			refreshList()
			updateSyncStatus()
			Task { [weak self] in
				await self?.didRequestSync()
			}
		} catch let error as LocalizedError {
			presentToast(message: error.errorDescription ?? error.localizedDescription)
		} catch {
			presentToast(message: error.localizedDescription)
		}
	}

	func didRequestDeleteItem(id: String) {
		do {
			try lockManager.deleteVaultItem(id: id)
			presentToast(message: "항목이 삭제되었습니다.")
			state.isItemSheetPresented = false
			resetFormState()
			presenter?.dismissItemSheet()
			refreshList()
			updateSyncStatus()
			Task { [weak self] in
				await self?.didRequestSync()
			}
		} catch let error as LocalizedError {
			presentToast(message: error.errorDescription ?? error.localizedDescription)
		} catch {
			presentToast(message: error.localizedDescription)
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
			presentToast(message: error.errorDescription ?? error.localizedDescription)
		} catch {
			updateSyncStatus()
			presentToast(message: error.localizedDescription)
		}
	}

	private func updateSyncStatus() {
		state.hasPendingSync = lockManager.hasPendingSync
		state.isOffline = lockManager.isOffline
		state.isSyncing = lockManager.isSyncing
		presenter?.setSyncStatus(
			hasPending: lockManager.hasPendingSync,
			isOffline: lockManager.isOffline,
			isSyncing: lockManager.isSyncing
		)
		presenter?.update(state: state)
	}

	private func presentToast(message: String) {
		toastDismissTask?.cancel()
		state.toastMessage = message
		presenter?.showToast(message: message)
		presenter?.update(state: state)
		toastDismissTask = Task { @MainActor [weak self] in
			try? await Task.sleep(nanoseconds: 2_500_000_000)
			guard let self, !Task.isCancelled else { return }
			if self.state.toastMessage == message {
				self.state.toastMessage = nil
				self.presenter?.update(state: self.state)
			}
		}
	}

	private func resetFormState() {
		state.formServiceName = ""
		state.formUsername = ""
		state.formPassword = ""
		state.formUrl = ""
		state.formNotes = ""
		state.formErrorMessage = nil
		state.editingItem = nil
	}
}
