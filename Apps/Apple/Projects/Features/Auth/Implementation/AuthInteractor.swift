import Foundation
import MicroRIBs
import KeyManageDomain
import AuthFeatureInterface

@MainActor
protocol AuthInteractable: Interactable {
	var router: AuthRouting? { get set }
	var listener: AuthListener? { get set }
}

@MainActor
final class AuthInteractor: Interactor, AuthInteractable, AuthPresentableListener {
	weak var router: AuthRouting?
	weak var listener: AuthListener?
	weak var presenter: AuthPresentable?
	let lockManager: VaultLockManagerProtocol
	let networkClient: AuthNetworkClientProtocol

	private var authTask: Task<Void, Never>?
	private(set) var state = AuthViewState()

	init(
		lockManager: VaultLockManagerProtocol,
		networkClient: AuthNetworkClientProtocol,
		presenter: AuthPresentable? = nil
	) {
		self.lockManager = lockManager
		self.networkClient = networkClient
		self.presenter = presenter
		super.init()
	}

	override func willResignActive() {
		super.willResignActive()
		authTask?.cancel()
		authTask = nil
	}

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

	func didRequestLogin(email: String, password: String) {
		authTask?.cancel()
		state.isLoading = true
		state.errorMessage = nil
		presenter?.setLoading(true)
		presenter?.setErrorMessage(nil)

		authTask = Task { [weak self] in
			guard let self else { return }
			defer {
				if !Task.isCancelled {
					self.state.isLoading = false
					self.presenter?.setLoading(false)
				}
			}

			do {
				try await self.lockManager.login(
					email: email,
					password: password,
					networkClient: self.networkClient
				)
				guard !Task.isCancelled else { return }
				self.state.password = ""
				self.state.confirmation = ""
				self.presenter?.clearInputs()
				self.listener?.authDidComplete()
			} catch let error as LocalizedError {
				guard !Task.isCancelled else { return }
				let msg = error.errorDescription ?? error.localizedDescription
				self.state.errorMessage = msg
				self.presenter?.setErrorMessage(msg)
			} catch {
				guard !Task.isCancelled else { return }
				self.state.errorMessage = error.localizedDescription
				self.presenter?.setErrorMessage(error.localizedDescription)
			}
		}
	}

	func didRequestRegister(email: String, password: String, confirmation: String) {
		authTask?.cancel()
		state.isLoading = true
		state.errorMessage = nil
		presenter?.setLoading(true)
		presenter?.setErrorMessage(nil)

		authTask = Task { [weak self] in
			guard let self else { return }
			defer {
				if !Task.isCancelled {
					self.state.isLoading = false
					self.presenter?.setLoading(false)
				}
			}

			do {
				try await self.lockManager.register(
					email: email,
					password: password,
					confirmation: confirmation,
					networkClient: self.networkClient
				)
				guard !Task.isCancelled else { return }
				self.state.password = ""
				self.state.confirmation = ""
				self.presenter?.clearInputs()
				self.listener?.authDidComplete()
			} catch let error as LocalizedError {
				guard !Task.isCancelled else { return }
				let msg = error.errorDescription ?? error.localizedDescription
				self.state.errorMessage = msg
				self.presenter?.setErrorMessage(msg)
			} catch {
				guard !Task.isCancelled else { return }
				self.state.errorMessage = error.localizedDescription
				self.presenter?.setErrorMessage(error.localizedDescription)
			}
		}
	}
}
