import Foundation
import SwiftUI
import MicroRIBs
import KeyManageDomain
import PasswordGeneratorFeatureInterface

// MARK: - PresentableListener

@MainActor
protocol PasswordGeneratorPresentableListener: AnyObject {
	func request(action: PasswordGeneratorViewAction)
}

#if os(macOS)
import AppKit
typealias PasswordGeneratorPlatformHostingController<Content: View> = NSHostingController<Content>
#elseif os(iOS)
import UIKit
typealias PasswordGeneratorPlatformHostingController<Content: View> = UIHostingController<Content>
#endif

@MainActor
final class PasswordGeneratorViewController: PasswordGeneratorPlatformHostingController<PasswordGeneratorView>, PasswordGeneratorViewControllable, PasswordGeneratorPresentable {
	weak var listener: PasswordGeneratorPresentableListener? {
		didSet {
			viewModel.listener = listener
		}
	}
	let viewModel: PasswordGeneratorViewModel

	init(listener: PasswordGeneratorPresentableListener? = nil) {
		self.listener = listener
		let vm = PasswordGeneratorViewModel(listener: listener, state: .init())
		self.viewModel = vm
		super.init(rootView: PasswordGeneratorView(viewModel: vm))
	}

	@available(*, unavailable)
	required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func update(state: PasswordGeneratorViewState) {
		viewModel.update(state: state)
	}

	func displayGeneratedPassword(_ password: String, score: Int, level: PasswordSecurityLevel, notice: String?) {
		var next = viewModel.state
		next.generatedPassword = password
		next.score = score
		next.securityLevel = level
		next.sequentialDigitsNotice = notice
		viewModel.update(state: next)
	}

	func showToast(message: String) {
		viewModel.showToast(message)
	}
}
