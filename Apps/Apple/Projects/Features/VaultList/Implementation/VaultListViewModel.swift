import Foundation
import KeyManageDomain

@MainActor
final class VaultListViewModel: ObservableObject {
	@Published var searchQuery: String = ""
	@Published var items: [VaultItem] = []
	@Published var toastMessage: String? = nil
	@Published var revealedPasswordIds: Set<String> = []

	var onSearch: ((String) -> Void)?
	var onCopyPassword: ((VaultItem) -> Void)?
	var onCopyUsername: ((VaultItem) -> Void)?

	init(items: [VaultItem] = []) {
		self.items = items
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
}
