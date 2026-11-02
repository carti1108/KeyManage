import Foundation
import SwiftUI
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface

// MARK: - PresentableListener

@MainActor
protocol VaultListPresentableListener: AnyObject {
	func request(action: VaultListViewAction)
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
	weak var listener: VaultListPresentableListener? {
		didSet {
			viewModel.listener = listener
		}
	}
	let viewModel: VaultListViewModel

	init(listener: VaultListPresentableListener? = nil) {
		self.listener = listener
		let vm = VaultListViewModel(listener: listener, state: .init())
		self.viewModel = vm
		super.init(rootView: VaultListView(viewModel: vm))
	}

	@available(*, unavailable)
	required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func update(state: VaultListViewState) {
		viewModel.update(state: state)
	}

	func setSyncStatus(hasPending: Bool, isOffline: Bool, isSyncing: Bool) {
		var next = viewModel.state
		next.hasPendingSync = hasPending
		next.isOffline = isOffline
		next.isSyncing = isSyncing
		viewModel.update(state: next)
	}

	func displayItems(_ items: [VaultItem]) {
		var next = viewModel.state
		next.items = items
		viewModel.update(state: next)
	}

	func setIsReadOnly(_ isReadOnly: Bool) {
		var next = viewModel.state
		next.isReadOnly = isReadOnly
		viewModel.update(state: next)
	}

	func dismissItemSheet() {
		var next = viewModel.state
		next.isItemSheetPresented = false
		next.editingItem = nil
		viewModel.update(state: next)
	}

	func showToast(message: String) {
		viewModel.showToast(message)
	}
}
