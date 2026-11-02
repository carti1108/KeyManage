import Foundation
import KeyManageDomain

// MARK: - ViewState

struct PasswordGeneratorViewState: Equatable, Sendable {
	var generatedPassword: String = ""
	var length: Double = Double(PasswordGenerationOptions.defaultLength)
	var includeUppercase: Bool = true
	var includeLowercase: Bool = true
	var includeDigits: Bool = true
	var includeSymbols: Bool = true
	var score: Int = 0
	var securityLevel: PasswordSecurityLevel = .secure
	var sequentialDigitsNotice: String?
	var toastMessage: String?

	var currentOptions: PasswordGenerationOptions {
		PasswordGenerationOptions(
			length: Int(length),
			includeUppercase: includeUppercase,
			includeLowercase: includeLowercase,
			includeDigits: includeDigits,
			includeSymbols: includeSymbols
		)
	}
}

// MARK: - ViewAction

enum PasswordGeneratorViewAction: Equatable, Sendable {
	case viewDidLoad
	case updateLength(Double)
	case toggleUppercase
	case toggleLowercase
	case toggleDigits
	case toggleSymbols
	case changeOptions(PasswordGenerationOptions)
	case regenerate
	case copyPassword(String)
	case clearToast
}
