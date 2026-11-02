import Foundation

public enum AuthMode: Equatable, Sendable {
	case login
	case register
}

// MARK: - ViewState

public struct AuthViewState: Equatable, Sendable {
	public var mode: AuthMode = .login
	public var email: String = ""
	public var password: String = ""
	public var confirmation: String = ""
	public var isLoading: Bool = false
	public var errorMessage: String? = nil

	public init(
		mode: AuthMode = .login,
		email: String = "",
		password: String = "",
		confirmation: String = "",
		isLoading: Bool = false,
		errorMessage: String? = nil
	) {
		self.mode = mode
		self.email = email
		self.password = password
		self.confirmation = confirmation
		self.isLoading = isLoading
		self.errorMessage = errorMessage
	}

	public var canSubmit: Bool {
		guard !isLoading else { return false }
		let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
		guard !trimmedEmail.isEmpty else { return false }

		switch mode {
		case .login:
			return !password.isEmpty
		case .register:
			return password.count >= 12 && password == confirmation
		}
	}
}

// MARK: - ViewAction

public enum AuthViewAction: Equatable, Sendable {
	case switchMode
	case submit
	case requestLogin(email: String, password: String)
	case requestRegister(email: String, password: String, confirmation: String)
	case updateEmail(String)
	case updatePassword(String)
	case updateConfirmation(String)
}
