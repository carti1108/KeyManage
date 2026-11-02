import Foundation
import KeyManageDomain

@MainActor
final class PasswordGeneratorViewModel: ObservableObject {
	typealias State = PasswordGeneratorViewState
	typealias Action = PasswordGeneratorViewAction

	weak var listener: PasswordGeneratorPresentableListener?

	@Published var state: State

	private var toastDismissTask: Task<Void, Never>?

	var onOptionsChanged: ((PasswordGenerationOptions) -> Void)?
	var onRequestRegenerate: (() -> Void)?
	var onRequestCopy: ((String) -> Void)?

	var generatedPassword: String {
		get { state.generatedPassword }
		set { state.generatedPassword = newValue }
	}
	var length: Double {
		get { state.length }
		set { state.length = newValue }
	}
	var includeUppercase: Bool {
		get { state.includeUppercase }
		set { state.includeUppercase = newValue }
	}
	var includeLowercase: Bool {
		get { state.includeLowercase }
		set { state.includeLowercase = newValue }
	}
	var includeDigits: Bool {
		get { state.includeDigits }
		set { state.includeDigits = newValue }
	}
	var includeSymbols: Bool {
		get { state.includeSymbols }
		set { state.includeSymbols = newValue }
	}
	var score: Int {
		get { state.score }
		set { state.score = newValue }
	}
	var securityLevel: PasswordSecurityLevel {
		get { state.securityLevel }
		set { state.securityLevel = newValue }
	}
	var sequentialDigitsNotice: String? {
		get { state.sequentialDigitsNotice }
		set { state.sequentialDigitsNotice = newValue }
	}
	var toastMessage: String? {
		get { state.toastMessage }
		set { state.toastMessage = newValue }
	}
	var currentOptions: PasswordGenerationOptions {
		state.currentOptions
	}

	init(
		listener: PasswordGeneratorPresentableListener? = nil,
		state: State = .init()
	) {
		self.listener = listener
		self.state = state
	}

	func update(state: State) {
		self.state = state
	}

	func request(action: Action) {
		if let listener {
			listener.request(action: action)
			return
		}
		applyLocalFallback(for: action)
	}

	func updateLength(_ newLength: Double) {
		request(action: .updateLength(newLength))
	}

	func toggleUppercase() {
		request(action: .toggleUppercase)
	}

	func toggleLowercase() {
		request(action: .toggleLowercase)
	}

	func toggleDigits() {
		request(action: .toggleDigits)
	}

	func toggleSymbols() {
		request(action: .toggleSymbols)
	}

	func regenerate() {
		request(action: .regenerate)
	}

	func copyPassword() {
		request(action: .copyPassword(state.generatedPassword))
	}

	func showToast(_ message: String) {
		toastDismissTask?.cancel()
		state.toastMessage = message
		toastDismissTask = Task { @MainActor [weak self] in
			try? await Task.sleep(nanoseconds: 3_000_000_000)
			guard !Task.isCancelled else { return }
			self?.state.toastMessage = nil
		}
	}

	func clearToast() {
		toastDismissTask?.cancel()
		request(action: .clearToast)
	}

	private func applyLocalFallback(for action: Action) {
		switch action {
		case .viewDidLoad:
			break
		case .updateLength(let newLength):
			state.length = newLength
			onOptionsChanged?(state.currentOptions)
		case .toggleUppercase:
			state.includeUppercase.toggle()
			onOptionsChanged?(state.currentOptions)
		case .toggleLowercase:
			state.includeLowercase.toggle()
			onOptionsChanged?(state.currentOptions)
		case .toggleDigits:
			state.includeDigits.toggle()
			onOptionsChanged?(state.currentOptions)
		case .toggleSymbols:
			state.includeSymbols.toggle()
			onOptionsChanged?(state.currentOptions)
		case .changeOptions(let options):
			state.length = Double(options.length)
			state.includeUppercase = options.includeUppercase
			state.includeLowercase = options.includeLowercase
			state.includeDigits = options.includeDigits
			state.includeSymbols = options.includeSymbols
			onOptionsChanged?(options)
		case .regenerate:
			onRequestRegenerate?()
		case .copyPassword(let password):
			onRequestCopy?(password)
		case .clearToast:
			state.toastMessage = nil
		}
	}
}
