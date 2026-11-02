import Foundation
import Combine
import KeyManageDomain

public enum AuthMode: Sendable {
	case login
	case register
}

@MainActor
public final class AuthViewModel: ObservableObject {
	@Published public var mode: AuthMode = .login
	@Published public var email: String = ""
	@Published public var password: String = ""
	@Published public var confirmation: String = ""
	@Published public var isLoading: Bool = false
	@Published public var errorMessage: String? = nil

	public var onLogin: ((String, String) -> Void)?
	public var onRegister: ((String, String, String) -> Void)?

	public init(mode: AuthMode = .login) {
		self.mode = mode
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

	public func switchMode() {
		mode = (mode == .login) ? .register : .login
		password = ""
		confirmation = ""
		errorMessage = nil
	}

	public func submit() {
		guard canSubmit else { return }
		errorMessage = nil
		let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)

		switch mode {
		case .login:
			onLogin?(trimmedEmail, password)
		case .register:
			onRegister?(trimmedEmail, password, confirmation)
		}
	}
}
