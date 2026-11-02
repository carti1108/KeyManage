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
	}

	@available(*, unavailable)
	required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func displayItems(_ items: [VaultItem]) {
		viewModel.items = items
	}

	func showToast(message: String) {
		viewModel.toastMessage = message
		Task { @MainActor [weak self] in
			try? await Task.sleep(nanoseconds: 2_500_000_000)
			if self?.viewModel.toastMessage == message {
				self?.viewModel.toastMessage = nil
			}
		}
	}
}
