import Foundation
import SwiftUI
import MicroRIBs
import AuthFeatureInterface

@MainActor
protocol AuthViewControllable: ViewControllable {}

@MainActor
protocol AuthPresentable: AnyObject {
	func update(state: AuthViewState)
	func setLoading(_ isLoading: Bool)
	func setErrorMessage(_ message: String?)
	func clearInputs()
}

@MainActor
extension AuthPresentable {
	func update(state: AuthViewState) {}
}

@MainActor
protocol AuthPresentableListener: AnyObject {
	func request(action: AuthViewAction)
	func didRequestLogin(email: String, password: String)
	func didRequestRegister(email: String, password: String, confirmation: String)
}

@MainActor
extension AuthPresentableListener {
	func request(action: AuthViewAction) {
		switch action {
		case .requestLogin(let email, let password):
			didRequestLogin(email: email, password: password)
		case .requestRegister(let email, let password, let confirmation):
			didRequestRegister(email: email, password: password, confirmation: confirmation)
		default:
			break
		}
	}
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

		model.onAction = { [weak self] action in
			self?.listener?.request(action: action)
		}
	}

	@MainActor required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func update(state: AuthViewState) {
		viewModel.update(state: state)
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
