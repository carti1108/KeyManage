import Foundation
import SwiftUI
import MicroRIBs
import UnlockFeatureInterface

@MainActor
protocol UnlockViewControllable: ViewControllable {}

@MainActor
protocol UnlockPresentableListener: AnyObject {
	func request(action: UnlockViewAction)
}

#if os(macOS)
import AppKit
typealias PlatformHostingController<Content: View> = NSHostingController<Content>
#elseif os(iOS)
import UIKit
typealias PlatformHostingController<Content: View> = UIHostingController<Content>
#endif

@MainActor
final class UnlockViewController: PlatformHostingController<UnlockView>, UnlockViewControllable, UnlockPresentable {
	weak var listener: UnlockPresentableListener? {
		didSet {
			viewModel.listener = listener
		}
	}
	let viewModel: UnlockViewModel

	init(
		isBiometricsEnabled: Bool = false,
		listener: UnlockPresentableListener? = nil
	) {
		self.listener = listener
		let model = UnlockViewModel(
			listener: listener,
			state: UnlockViewState(isBiometricsEnabled: isBiometricsEnabled)
		)
		self.viewModel = model
		super.init(rootView: UnlockView(viewModel: model))
	}

	@MainActor required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func update(state: UnlockViewState) {
		viewModel.update(state: state)
	}

	func setErrorMessage(_ message: String?) {
		var next = viewModel.state
		next.errorMessage = message
		viewModel.update(state: next)
	}

	func setLoading(_ isLoading: Bool) {
		var next = viewModel.state
		next.isLoading = isLoading
		viewModel.update(state: next)
	}

	func clearInputPassword() {
		var next = viewModel.state
		next.password = ""
		viewModel.update(state: next)
	}
}
