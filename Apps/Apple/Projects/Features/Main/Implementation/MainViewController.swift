import Foundation
import SwiftUI
import MicroRIBs
import MainFeatureInterface

// MARK: - PresentableListener

@MainActor
protocol MainPresentableListener: AnyObject {
	func requestLockVault()
	func didSelectTab(_ tab: MainTab)
}

#if os(macOS)
import AppKit
typealias MainPlatformHostingController<Content: View> = NSHostingController<Content>
#elseif os(iOS)
import UIKit
typealias MainPlatformHostingController<Content: View> = UIHostingController<Content>
#endif

@MainActor
final class MainViewController: MainPlatformHostingController<MainView>, MainViewControllable, MainPresentable {
	weak var listener: MainPresentableListener?
	let viewModel: MainViewModel

	init(listener: MainPresentableListener? = nil) {
		self.listener = listener
		let vm = MainViewModel()
		self.viewModel = vm
		super.init(rootView: MainView(viewModel: vm))
		vm.onLockRequested = { [weak self] in
			self?.listener?.requestLockVault()
		}
		vm.onTabSelected = { [weak self] tab in
			self?.listener?.didSelectTab(tab)
		}
	}

	@available(*, unavailable)
	required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func selectTab(_ tab: MainTab) {
		viewModel.selectedTab = tab
	}

	func setVaultListViewControllable(_ viewControllable: ViewControllable) {
		viewModel.vaultListViewControllable = viewControllable
	}

	func setSettingsViewControllable(_ viewControllable: ViewControllable) {
		viewModel.settingsViewControllable = viewControllable
	}

	func setPasswordGeneratorViewControllable(_ viewControllable: ViewControllable) {
		viewModel.passwordGeneratorViewControllable = viewControllable
	}
}
