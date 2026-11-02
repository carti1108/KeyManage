import Foundation
import SwiftUI
import MicroRIBs
import MainFeatureInterface

// MARK: - PresentableListener

@MainActor
protocol MainPresentableListener: AnyObject {
	func request(action: MainViewAction)
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
	weak var listener: MainPresentableListener? {
		didSet {
			viewModel.listener = listener
		}
	}
	let viewModel: MainViewModel

	init(listener: MainPresentableListener? = nil) {
		self.listener = listener
		let vm = MainViewModel(listener: listener, state: .init())
		self.viewModel = vm
		super.init(rootView: MainView(viewModel: vm))
	}

	@available(*, unavailable)
	required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func update(state: MainViewState) {
		viewModel.update(state: state)
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
