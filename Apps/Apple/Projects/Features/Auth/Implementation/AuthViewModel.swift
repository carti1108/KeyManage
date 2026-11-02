import Foundation
import Combine
import KeyManageDomain

@MainActor
public final class AuthViewModel: ObservableObject {
	@Published public var state: AuthViewState

	public var mode: AuthMode {
		get { state.mode }
		set { state.mode = newValue }
	}

	public var email: String {
		get { state.email }
		set { state.email = newValue }
	}

	public var password: String {
		get { state.password }
		set { state.password = newValue }
	}

	public var confirmation: String {
		get { state.confirmation }
		set { state.confirmation = newValue }
	}

	public var isLoading: Bool {
		get { state.isLoading }
		set { state.isLoading = newValue }
	}

	public var errorMessage: String? {
		get { state.errorMessage }
		set { state.errorMessage = newValue }
	}

	public var onAction: ((AuthViewAction) -> Void)?
	public var onLogin: ((String, String) -> Void)?
	public var onRegister: ((String, String, String) -> Void)?

	public init(mode: AuthMode = .login) {
		self.state = AuthViewState(mode: mode)
	}

	public var canSubmit: Bool {
		state.canSubmit
	}

	public func update(state: AuthViewState) {
		self.state = state
	}

	public func send(_ action: AuthViewAction) {
		switch action {
		case .switchMode:
			state.mode = (state.mode == .login) ? .register : .login
			state.password = ""
			state.confirmation = ""
			state.errorMessage = nil
			onAction?(action)
		case .submit:
			guard canSubmit else { return }
			state.errorMessage = nil
			let trimmedEmail = state.email.trimmingCharacters(in: .whitespacesAndNewlines)

			switch state.mode {
			case .login:
				let loginAction = AuthViewAction.requestLogin(email: trimmedEmail, password: state.password)
				onAction?(loginAction)
				onLogin?(trimmedEmail, state.password)
			case .register:
				let registerAction = AuthViewAction.requestRegister(
					email: trimmedEmail,
					password: state.password,
					confirmation: state.confirmation
				)
				onAction?(registerAction)
				onRegister?(trimmedEmail, state.password, state.confirmation)
			}
		case .requestLogin(let email, let password):
			onAction?(action)
			onLogin?(email, password)
		case .requestRegister(let email, let password, let confirmation):
			onAction?(action)
			onRegister?(email, password, confirmation)
		case .updateEmail(let email):
			state.email = email
		case .updatePassword(let password):
			state.password = password
		case .updateConfirmation(let confirmation):
			state.confirmation = confirmation
		}
	}

	public func switchMode() {
		send(.switchMode)
	}

	public func submit() {
		send(.submit)
	}
}
