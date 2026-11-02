import Foundation
import MicroRIBs
import KeyManageDomain
import PasswordGeneratorFeatureInterface

// MARK: - Presentable Protocols

@MainActor
protocol PasswordGeneratorPresentable: AnyObject {
	var listener: PasswordGeneratorPresentableListener? { get set }
	func update(state: PasswordGeneratorViewState)
	func displayGeneratedPassword(_ password: String, score: Int, level: PasswordSecurityLevel, notice: String?)
	func showToast(message: String)
}

extension PasswordGeneratorPresentable {
	func update(state: PasswordGeneratorViewState) {}
	func displayGeneratedPassword(_ password: String, score: Int, level: PasswordSecurityLevel, notice: String?) {}
	func showToast(message: String) {}
}

// MARK: - Interactor

@MainActor
final class PasswordGeneratorInteractor: Interactor, PasswordGeneratorInteractable, PasswordGeneratorPresentableListener {
	weak var router: PasswordGeneratorRouting?
	weak var listener: PasswordGeneratorListener?
	weak var presenter: PasswordGeneratorPresentable?
	let clipboardManager: ClipboardManagerProtocol
	private let generator: PasswordGeneratorProtocol

	private(set) var state = PasswordGeneratorViewState()
	private var toastDismissTask: Task<Void, Never>?

	init(
		clipboardManager: ClipboardManagerProtocol,
		generator: PasswordGeneratorProtocol = PasswordGenerator(),
		presenter: PasswordGeneratorPresentable? = nil
	) {
		self.clipboardManager = clipboardManager
		self.generator = generator
		self.presenter = presenter
		super.init()
		presenter?.listener = self
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		request(action: .viewDidLoad)
	}

	override func willResignActive() {
		super.willResignActive()
		toastDismissTask?.cancel()
		toastDismissTask = nil
	}

	func request(action: PasswordGeneratorViewAction) {
		switch action {
		case .viewDidLoad:
			generatePassword()
		case .updateLength(let newLength):
			state.length = newLength
			generatePassword()
		case .toggleUppercase:
			state.includeUppercase.toggle()
			generatePassword()
		case .toggleLowercase:
			state.includeLowercase.toggle()
			generatePassword()
		case .toggleDigits:
			state.includeDigits.toggle()
			generatePassword()
		case .toggleSymbols:
			state.includeSymbols.toggle()
			generatePassword()
		case .changeOptions(let options):
			didChangeOptions(options)
		case .regenerate:
			didRequestRegenerate()
		case .copyPassword(let password):
			didRequestCopyPassword(password)
		case .clearToast:
			toastDismissTask?.cancel()
			state.toastMessage = nil
			presenter?.update(state: state)
		}
	}

	func didChangeOptions(_ options: PasswordGenerationOptions) {
		state.length = Double(options.length)
		state.includeUppercase = options.includeUppercase
		state.includeLowercase = options.includeLowercase
		state.includeDigits = options.includeDigits
		state.includeSymbols = options.includeSymbols
		generatePassword(using: options)
	}

	func didRequestRegenerate() {
		generatePassword()
	}

	func didRequestCopyPassword(_ password: String) {
		guard !password.isEmpty else { return }
		do {
			try clipboardManager.copy(password)
			presentToast(message: ClipboardStrings.autoBurnNoticeMessage)
		} catch {
			presentToast(message: ClipboardStrings.copyFailedMessage)
		}
	}

	private func generatePassword(using explicitOptions: PasswordGenerationOptions? = nil) {
		let options = explicitOptions ?? state.currentOptions
		do {
			let password = try generator.generate(options: options)
			let score = EntropyScorer.score(for: password)
			let level = EntropyScorer.securityLevel(for: score)
			let notice: String? = SequentialDigitsChecker.hasSequentialDigits(password) ? SequentialDigitsChecker.noticeMessage : nil
			state.generatedPassword = password
			state.score = score
			state.securityLevel = level
			state.sequentialDigitsNotice = notice
			presenter?.displayGeneratedPassword(password, score: score, level: level, notice: notice)
			presenter?.update(state: state)
		} catch {
			state.generatedPassword = ""
			state.score = 0
			state.securityLevel = .danger
			state.sequentialDigitsNotice = nil
			presenter?.displayGeneratedPassword("", score: 0, level: .danger, notice: nil)
			presentToast(message: error.localizedDescription)
		}
	}

	private func presentToast(message: String) {
		toastDismissTask?.cancel()
		state.toastMessage = message
		presenter?.showToast(message: message)
		presenter?.update(state: state)
		toastDismissTask = Task { @MainActor [weak self] in
			try? await Task.sleep(nanoseconds: 3_000_000_000)
			guard let self, !Task.isCancelled else { return }
			if self.state.toastMessage == message {
				self.state.toastMessage = nil
				self.presenter?.update(state: self.state)
			}
		}
	}
}
