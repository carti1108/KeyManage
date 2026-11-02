import Foundation
import KeyManageDomain

@MainActor
final class PasswordGeneratorViewModel: ObservableObject {
	@Published var generatedPassword: String = ""
	@Published var length: Double = Double(PasswordGenerationOptions.defaultLength)
	@Published var includeUppercase: Bool = true
	@Published var includeLowercase: Bool = true
	@Published var includeDigits: Bool = true
	@Published var includeSymbols: Bool = true

	@Published var score: Int = 0
	@Published var securityLevel: PasswordSecurityLevel = .secure
	@Published var sequentialDigitsNotice: String?
	@Published var toastMessage: String?

	private var toastDismissTask: Task<Void, Never>?

	var onOptionsChanged: ((PasswordGenerationOptions) -> Void)?
	var onRequestRegenerate: (() -> Void)?
	var onRequestCopy: ((String) -> Void)?

	var currentOptions: PasswordGenerationOptions {
		PasswordGenerationOptions(
			length: Int(length),
			includeUppercase: includeUppercase,
			includeLowercase: includeLowercase,
			includeDigits: includeDigits,
			includeSymbols: includeSymbols
		)
	}

	func updateLength(_ newLength: Double) {
		length = newLength
		notifyOptionsChanged()
	}

	func toggleUppercase() {
		includeUppercase.toggle()
		notifyOptionsChanged()
	}

	func toggleLowercase() {
		includeLowercase.toggle()
		notifyOptionsChanged()
	}

	func toggleDigits() {
		includeDigits.toggle()
		notifyOptionsChanged()
	}

	func toggleSymbols() {
		includeSymbols.toggle()
		notifyOptionsChanged()
	}

	func regenerate() {
		onRequestRegenerate?()
	}

	func copyPassword() {
		onRequestCopy?(generatedPassword)
	}

	func showToast(_ message: String) {
		toastDismissTask?.cancel()
		toastMessage = message
		toastDismissTask = Task { @MainActor [weak self] in
			try? await Task.sleep(nanoseconds: 3_000_000_000)
			guard !Task.isCancelled else { return }
			self?.toastMessage = nil
		}
	}

	func clearToast() {
		toastDismissTask?.cancel()
		toastMessage = nil
	}

	private func notifyOptionsChanged() {
		onOptionsChanged?(currentOptions)
	}
}
