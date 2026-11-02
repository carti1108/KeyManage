import Foundation
import MicroRIBs
import KeyManageDomain
import PasswordGeneratorFeatureInterface

// MARK: - Presentable Protocols

@MainActor
protocol PasswordGeneratorPresentable: AnyObject {
	var listener: PasswordGeneratorPresentableListener? { get set }
	func displayGeneratedPassword(_ password: String, score: Int, level: PasswordSecurityLevel, notice: String?)
	func showToast(message: String)
}

// MARK: - Interactor

@MainActor
final class PasswordGeneratorInteractor: Interactor, PasswordGeneratorInteractable, PasswordGeneratorPresentableListener {
	weak var router: PasswordGeneratorRouting?
	weak var listener: PasswordGeneratorListener?
	weak var presenter: PasswordGeneratorPresentable?
	let clipboardManager: ClipboardManagerProtocol
	private let generator: PasswordGeneratorProtocol

	private var currentOptions = PasswordGenerationOptions()

	init(
		clipboardManager: ClipboardManagerProtocol,
		generator: PasswordGeneratorProtocol = PasswordGenerator(),
		presenter: PasswordGeneratorPresentable? = nil
	) {
		self.clipboardManager = clipboardManager
		self.generator = generator
		self.presenter = presenter
		super.init()
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		generatePassword()
	}

	func didChangeOptions(_ options: PasswordGenerationOptions) {
		self.currentOptions = options
		generatePassword()
	}

	func didRequestRegenerate() {
		generatePassword()
	}

	func didRequestCopyPassword(_ password: String) {
		guard !password.isEmpty else { return }
		do {
			try clipboardManager.copy(password)
			presenter?.showToast(message: ClipboardStrings.autoBurnNoticeMessage)
		} catch {
			presenter?.showToast(message: ClipboardStrings.copyFailedMessage)
		}
	}

	private func generatePassword() {
		do {
			let password = try generator.generate(options: currentOptions)
			let score = EntropyScorer.score(for: password)
			let level = EntropyScorer.securityLevel(for: score)
			let notice: String? = SequentialDigitsChecker.hasSequentialDigits(password) ? SequentialDigitsChecker.noticeMessage : nil
			presenter?.displayGeneratedPassword(password, score: score, level: level, notice: notice)
		} catch {
			presenter?.displayGeneratedPassword("", score: 0, level: .danger, notice: nil)
			presenter?.showToast(message: error.localizedDescription)
		}
	}
}
