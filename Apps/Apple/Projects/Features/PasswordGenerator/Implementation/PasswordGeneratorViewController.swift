import Foundation
import SwiftUI
import MicroRIBs
import KeyManageDomain
import PasswordGeneratorFeatureInterface

// MARK: - PresentableListener

@MainActor
protocol PasswordGeneratorPresentableListener: AnyObject {
	func didChangeOptions(_ options: PasswordGenerationOptions)
	func didRequestRegenerate()
	func didRequestCopyPassword(_ password: String)
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
	weak var listener: PasswordGeneratorPresentableListener?
	let viewModel: PasswordGeneratorViewModel

	init(listener: PasswordGeneratorPresentableListener? = nil) {
		self.listener = listener
		let vm = PasswordGeneratorViewModel()
		self.viewModel = vm
		super.init(rootView: PasswordGeneratorView(viewModel: vm))

		vm.onOptionsChanged = { [weak self] options in
			self?.listener?.didChangeOptions(options)
		}
		vm.onRequestRegenerate = { [weak self] in
			self?.listener?.didRequestRegenerate()
		}
		vm.onRequestCopy = { [weak self] password in
			self?.listener?.didRequestCopyPassword(password)
		}
	}

	@available(*, unavailable)
	required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func displayGeneratedPassword(_ password: String, score: Int, level: PasswordSecurityLevel, notice: String?) {
		viewModel.generatedPassword = password
		viewModel.score = score
		viewModel.securityLevel = level
		viewModel.sequentialDigitsNotice = notice
	}

	func showToast(message: String) {
		viewModel.showToast(message)
	}
}
