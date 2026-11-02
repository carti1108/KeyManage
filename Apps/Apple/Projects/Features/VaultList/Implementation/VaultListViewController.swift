import Foundation
import SwiftUI
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface

// MARK: - PresentableListener

@MainActor
protocol VaultListPresentableListener: AnyObject {
	func didSearch(query: String)
	func didRequestCopyPassword(item: VaultItem)
	func didRequestCopyUsername(item: VaultItem)
	func didRequestSaveItem(existingId: String?, serviceName: String, username: String, password: String, url: String?, notes: String?)
	func didRequestDeleteItem(id: String)
	func didRequestSync() async
}

#if os(macOS)
import AppKit
typealias VaultListPlatformHostingController<Content: View> = NSHostingController<Content>
#elseif os(iOS)
import UIKit
typealias VaultListPlatformHostingController<Content: View> = UIHostingController<Content>
#endif

@MainActor
final class VaultListViewController: VaultListPlatformHostingController<VaultListView>, VaultListViewControllable, VaultListPresentable {
	weak var listener: VaultListPresentableListener?
	let viewModel: VaultListViewModel

	init(listener: VaultListPresentableListener? = nil) {
		self.listener = listener
		let vm = VaultListViewModel()
		self.viewModel = vm
		super.init(rootView: VaultListView(viewModel: vm))

		vm.onSearch = { [weak self] query in
			self?.listener?.didSearch(query: query)
		}
		vm.onCopyPassword = { [weak self] item in
			self?.listener?.didRequestCopyPassword(item: item)
		}
		vm.onCopyUsername = { [weak self] item in
			self?.listener?.didRequestCopyUsername(item: item)
		}
		vm.onSaveItem = { [weak self] existingId, serviceName, username, password, url, notes in
			self?.listener?.didRequestSaveItem(
				existingId: existingId,
				serviceName: serviceName,
				username: username,
				password: password,
				url: url,
				notes: notes
			)
		}
		vm.onDeleteItem = { [weak self] id in
			self?.listener?.didRequestDeleteItem(id: id)
		}
		vm.onSync = { [weak self] in
			await self?.listener?.didRequestSync()
		}
	}

	@available(*, unavailable)
	required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func setSyncStatus(hasPending: Bool, isOffline: Bool, isSyncing: Bool) {
		viewModel.hasPendingSync = hasPending
		viewModel.isOffline = isOffline
		viewModel.isSyncing = isSyncing
	}

	func displayItems(_ items: [VaultItem]) {
		viewModel.items = items
	}

	func setIsReadOnly(_ isReadOnly: Bool) {
		viewModel.isReadOnly = isReadOnly
	}

	func dismissItemSheet() {
		viewModel.isItemSheetPresented = false
		viewModel.editingItem = nil
	}

	func showToast(message: String) {
		viewModel.showToast(message)
	}
}
