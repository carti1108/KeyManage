import Foundation
import KeyManageDomain

@MainActor
final class VaultListViewModel: ObservableObject {
	typealias State = VaultListViewState
	typealias Action = VaultListViewAction

	weak var listener: VaultListPresentableListener?

	@Published var state: State

	var onSearch: ((String) -> Void)?
	var onCopyPassword: ((VaultItem) -> Void)?
	var onCopyUsername: ((VaultItem) -> Void)?
	var onSaveItem: ((_ existingId: String?, _ serviceName: String, _ username: String, _ password: String, _ url: String?, _ notes: String?) -> Void)?
	var onDeleteItem: ((String) -> Void)?
	var onSync: (@MainActor () async -> Void)?

	private var toastDismissTask: Task<Void, Never>?

	var searchQuery: String {
		get { state.searchQuery }
		set { state.searchQuery = newValue }
	}
	var items: [VaultItem] {
		get { state.items }
		set { state.items = newValue }
	}
	var toastMessage: String? {
		get { state.toastMessage }
		set { state.toastMessage = newValue }
	}
	var revealedPasswordIds: Set<String> {
		get { state.revealedPasswordIds }
		set { state.revealedPasswordIds = newValue }
	}
	var isReadOnly: Bool {
		get { state.isReadOnly }
		set { state.isReadOnly = newValue }
	}
	var hasPendingSync: Bool {
		get { state.hasPendingSync }
		set { state.hasPendingSync = newValue }
	}
	var isOffline: Bool {
		get { state.isOffline }
		set { state.isOffline = newValue }
	}
	var isSyncing: Bool {
		get { state.isSyncing }
		set { state.isSyncing = newValue }
	}
	var isItemSheetPresented: Bool {
		get { state.isItemSheetPresented }
		set { state.isItemSheetPresented = newValue }
	}
	var isDeleteAlertPresented: Bool {
		get { state.isDeleteAlertPresented }
		set { state.isDeleteAlertPresented = newValue }
	}
	var itemToDelete: VaultItem? {
		get { state.itemToDelete }
		set { state.itemToDelete = newValue }
	}
	var editingItem: VaultItem? {
		get { state.editingItem }
		set { state.editingItem = newValue }
	}
	var formServiceName: String {
		get { state.formServiceName }
		set { state.formServiceName = newValue }
	}
	var formUsername: String {
		get { state.formUsername }
		set { state.formUsername = newValue }
	}
	var formPassword: String {
		get { state.formPassword }
		set { state.formPassword = newValue }
	}
	var formUrl: String {
		get { state.formUrl }
		set { state.formUrl = newValue }
	}
	var formNotes: String {
		get { state.formNotes }
		set { state.formNotes = newValue }
	}
	var formErrorMessage: String? {
		get { state.formErrorMessage }
		set { state.formErrorMessage = newValue }
	}

	init(
		listener: VaultListPresentableListener? = nil,
		state: State = .init()
	) {
		self.listener = listener
		self.state = state
	}

	convenience init(
		items: [VaultItem],
		isReadOnly: Bool = false,
		hasPendingSync: Bool = false,
		isOffline: Bool = false
	) {
		self.init(
			state: VaultListViewState(
				items: items,
				isReadOnly: isReadOnly,
				hasPendingSync: hasPendingSync,
				isOffline: isOffline
			)
		)
	}

	func update(state: State) {
		self.state = state
	}

	func request(action: Action) {
		if let listener {
			listener.request(action: action)
			return
		}
		applyLocalFallback(for: action)
	}

	func triggerSync() async {
		if let listener {
			await listener.didRequestSync()
		} else {
			await onSync?()
		}
	}

	func updateSearchQuery(_ query: String) {
		request(action: .search(query))
	}

	func copyPassword(for item: VaultItem) {
		request(action: .copyPassword(item))
	}

	func copyUsername(for item: VaultItem) {
		request(action: .copyUsername(item))
	}

	func togglePasswordVisibility(id: String) {
		request(action: .togglePasswordVisibility(id: id))
	}

	func isPasswordRevealed(id: String) -> Bool {
		state.isPasswordRevealed(id: id)
	}

	func startCreatingItem() {
		request(action: .startCreatingItem)
	}

	func startEditingItem(_ item: VaultItem) {
		request(action: .startEditingItem(item))
	}

	func generatePasswordForForm() {
		request(action: .generatePasswordForForm)
	}

	func requestSaveItem() {
		request(action: .requestSaveItem)
	}

	func resetForm() {
		resetFormState()
	}

	func cancelEditing() {
		request(action: .cancelEditing)
	}

	func confirmDeleteItem(_ item: VaultItem) {
		request(action: .confirmDeleteItem(item))
	}

	func executeDeleteItem() {
		request(action: .executeDeleteItem)
	}

	private static let toastDismissNanoseconds: UInt64 = 2_500_000_000

	func showToast(_ message: String) {
		toastDismissTask?.cancel()
		state.toastMessage = message
		toastDismissTask = Task { @MainActor [weak self] in
			try? await Task.sleep(nanoseconds: Self.toastDismissNanoseconds)
			guard !Task.isCancelled else { return }
			if self?.state.toastMessage == message {
				self?.state.toastMessage = nil
			}
		}
	}

	private func applyLocalFallback(for action: Action) {
		switch action {
		case .viewDidLoad:
			break
		case .search(let query):
			state.searchQuery = query
			onSearch?(query)
		case .copyPassword(let item):
			onCopyPassword?(item)
		case .copyUsername(let item):
			onCopyUsername?(item)
		case .togglePasswordVisibility(let id):
			if state.revealedPasswordIds.contains(id) {
				state.revealedPasswordIds.remove(id)
			} else {
				state.revealedPasswordIds.insert(id)
			}
		case .startCreatingItem:
			resetFormState()
			state.isItemSheetPresented = true
		case .startEditingItem(let item):
			state.editingItem = item
			state.formServiceName = item.serviceName
			state.formUsername = item.username
			state.formPassword = item.password
			state.formUrl = item.url ?? ""
			state.formNotes = item.notes ?? ""
			state.formErrorMessage = nil
			state.isItemSheetPresented = true
		case .cancelEditing:
			state.isItemSheetPresented = false
			resetFormState()
		case .generatePasswordForForm:
			if let generated = try? PasswordGenerator.generate() {
				state.formPassword = generated
			}
		case .updateFormServiceName(let value):
			state.formServiceName = value
		case .updateFormUsername(let value):
			state.formUsername = value
		case .updateFormPassword(let value):
			state.formPassword = value
		case .updateFormUrl(let value):
			state.formUrl = value
		case .updateFormNotes(let value):
			state.formNotes = value
		case .setItemSheetPresented(let presented):
			state.isItemSheetPresented = presented
		case .requestSaveItem:
			let trimmedService = state.formServiceName.trimmingCharacters(in: .whitespacesAndNewlines)
			guard !trimmedService.isEmpty else {
				state.formErrorMessage = VaultItemError.emptyServiceName.errorDescription
				return
			}
			onSaveItem?(
				state.editingItem?.id,
				trimmedService,
				state.formUsername,
				state.formPassword,
				state.formUrl.isEmpty ? nil : state.formUrl,
				state.formNotes.isEmpty ? nil : state.formNotes
			)
		case .saveItem(let existingId, let serviceName, let username, let password, let url, let notes):
			onSaveItem?(existingId, serviceName, username, password, url, notes)
		case .confirmDeleteItem(let item):
			state.itemToDelete = item
			state.isDeleteAlertPresented = true
		case .cancelDeleteItem:
			state.itemToDelete = nil
			state.isDeleteAlertPresented = false
		case .setDeleteAlertPresented(let presented):
			state.isDeleteAlertPresented = presented
		case .executeDeleteItem:
			guard let item = state.itemToDelete else { return }
			onDeleteItem?(item.id)
			state.itemToDelete = nil
			state.isDeleteAlertPresented = false
			if state.isItemSheetPresented && state.editingItem?.id == item.id {
				state.isItemSheetPresented = false
				resetFormState()
			}
		case .deleteItem(let id):
			onDeleteItem?(id)
		case .requestSync:
			Task { @MainActor [weak self] in
				await self?.onSync?()
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
