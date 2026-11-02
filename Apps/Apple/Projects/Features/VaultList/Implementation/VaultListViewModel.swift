import Foundation
import KeyManageDomain

@MainActor
final class VaultListViewModel: ObservableObject {
	@Published var searchQuery: String = ""
	@Published var items: [VaultItem] = []
	@Published var toastMessage: String? = nil
	@Published var revealedPasswordIds: Set<String> = []
	@Published var isReadOnly: Bool = false
	@Published var hasPendingSync: Bool = false
	@Published var isOffline: Bool = false
	@Published var isSyncing: Bool = false

	// 모달 및 폼 상태
	@Published var isItemSheetPresented: Bool = false
	@Published var isDeleteAlertPresented: Bool = false
	@Published var itemToDelete: VaultItem? = nil
	@Published var editingItem: VaultItem? = nil

	@Published var formServiceName: String = ""
	@Published var formUsername: String = ""
	@Published var formPassword: String = ""
	@Published var formUrl: String = ""
	@Published var formNotes: String = ""
	@Published var formErrorMessage: String? = nil

	private var toastDismissTask: Task<Void, Never>?

	var onSearch: ((String) -> Void)?
	var onCopyPassword: ((VaultItem) -> Void)?
	var onCopyUsername: ((VaultItem) -> Void)?
	var onSaveItem: ((_ existingId: String?, _ serviceName: String, _ username: String, _ password: String, _ url: String?, _ notes: String?) -> Void)?
	var onDeleteItem: ((String) -> Void)?
	var onSync: (@MainActor () async -> Void)?

	init(
		items: [VaultItem] = [],
		isReadOnly: Bool = false,
		hasPendingSync: Bool = false,
		isOffline: Bool = false
	) {
		self.items = items
		self.isReadOnly = isReadOnly
		self.hasPendingSync = hasPendingSync
		self.isOffline = isOffline
	}

	func triggerSync() async {
		await onSync?()
	}

	func updateSearchQuery(_ query: String) {
		searchQuery = query
		onSearch?(query)
	}

	func copyPassword(for item: VaultItem) {
		onCopyPassword?(item)
	}

	func copyUsername(for item: VaultItem) {
		onCopyUsername?(item)
	}

	func togglePasswordVisibility(id: String) {
		if revealedPasswordIds.contains(id) {
			revealedPasswordIds.remove(id)
		} else {
			revealedPasswordIds.insert(id)
		}
	}

	func isPasswordRevealed(id: String) -> Bool {
		revealedPasswordIds.contains(id)
	}

	// MARK: - Item Sheet & Actions

	func startCreatingItem() {
		resetForm()
		isItemSheetPresented = true
	}

	func startEditingItem(_ item: VaultItem) {
		editingItem = item
		formServiceName = item.serviceName
		formUsername = item.username
		formPassword = item.password
		formUrl = item.url ?? ""
		formNotes = item.notes ?? ""
		formErrorMessage = nil
		isItemSheetPresented = true
	}

	func generatePasswordForForm() {
		if let generated = try? PasswordGenerator.generate() {
			formPassword = generated
		}
	}

	func requestSaveItem() {
		let trimmedService = formServiceName.trimmingCharacters(in: .whitespacesAndNewlines)
		guard !trimmedService.isEmpty else {
			formErrorMessage = VaultItemError.emptyServiceName.errorDescription
			return
		}

		onSaveItem?(
			editingItem?.id,
			trimmedService,
			formUsername,
			formPassword,
			formUrl.isEmpty ? nil : formUrl,
			formNotes.isEmpty ? nil : formNotes
		)
	}

	func resetForm() {
		formServiceName = ""
		formUsername = ""
		formPassword = ""
		formUrl = ""
		formNotes = ""
		formErrorMessage = nil
		editingItem = nil
	}

	func cancelEditing() {
		isItemSheetPresented = false
		resetForm()
	}

	func confirmDeleteItem(_ item: VaultItem) {
		itemToDelete = item
		isDeleteAlertPresented = true
	}

	func executeDeleteItem() {
		guard let item = itemToDelete else { return }
		onDeleteItem?(item.id)
		itemToDelete = nil
		isDeleteAlertPresented = false
		if isItemSheetPresented && editingItem?.id == item.id {
			isItemSheetPresented = false
			resetForm()
		}
	}

	private static let toastDismissNanoseconds: UInt64 = 2_500_000_000

	func showToast(_ message: String) {
		toastDismissTask?.cancel()
		toastMessage = message
		toastDismissTask = Task { @MainActor [weak self] in
			try? await Task.sleep(nanoseconds: Self.toastDismissNanoseconds)
			guard !Task.isCancelled else { return }
			if self?.toastMessage == message {
				self?.toastMessage = nil
			}
		}
	}
}
