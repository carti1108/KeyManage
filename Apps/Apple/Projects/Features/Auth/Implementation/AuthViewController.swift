import Foundation
import SwiftUI
import MicroRIBs
import AuthFeatureInterface

@MainActor
protocol AuthViewControllable: ViewControllable {}

@MainActor
protocol AuthPresentable: AnyObject {
	func setLoading(_ isLoading: Bool)
	func setErrorMessage(_ message: String?)
	func clearInputs()
}

@MainActor
protocol AuthPresentableListener: AnyObject {
	func didRequestLogin(email: String, password: String)
	func didRequestRegister(email: String, password: String, confirmation: String)
}

#if os(macOS)
import AppKit
typealias PlatformHostingController<Content: View> = NSHostingController<Content>
#elseif os(iOS)
import UIKit
typealias PlatformHostingController<Content: View> = UIHostingController<Content>
#endif

@MainActor
final class AuthViewController: PlatformHostingController<AuthView>, AuthViewControllable, AuthPresentable {
	weak var listener: AuthPresentableListener?
	let viewModel: AuthViewModel

	init(listener: AuthPresentableListener? = nil) {
		self.listener = listener
		let model = AuthViewModel()
		self.viewModel = model
		super.init(rootView: AuthView(viewModel: model))

		model.onLogin = { [weak self] email, password in
			self?.listener?.didRequestLogin(email: email, password: password)
		}
		model.onRegister = { [weak self] email, password, confirmation in
			self?.listener?.didRequestRegister(email: email, password: password, confirmation: confirmation)
		}
	}

	@MainActor required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func setLoading(_ isLoading: Bool) {
		viewModel.isLoading = isLoading
	}

	func setErrorMessage(_ message: String?) {
		viewModel.errorMessage = message
	}

	func clearInputs() {
		viewModel.password = ""
		viewModel.confirmation = ""
	}
}
