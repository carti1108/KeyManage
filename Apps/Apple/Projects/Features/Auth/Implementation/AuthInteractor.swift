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

	func didRequestLogin(email: String, password: String) {
		authTask?.cancel()
		presenter?.setLoading(true)
		presenter?.setErrorMessage(nil)

		authTask = Task { [weak self] in
			guard let self else { return }
			defer {
				if !Task.isCancelled {
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
				self.presenter?.clearInputs()
				self.listener?.authDidComplete()
			} catch let error as LocalizedError {
				guard !Task.isCancelled else { return }
				self.presenter?.setErrorMessage(error.errorDescription ?? error.localizedDescription)
			} catch {
				guard !Task.isCancelled else { return }
				self.presenter?.setErrorMessage(error.localizedDescription)
			}
		}
	}

	func didRequestRegister(email: String, password: String, confirmation: String) {
		authTask?.cancel()
		presenter?.setLoading(true)
		presenter?.setErrorMessage(nil)

		authTask = Task { [weak self] in
			guard let self else { return }
			defer {
				if !Task.isCancelled {
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
				self.presenter?.clearInputs()
				self.listener?.authDidComplete()
			} catch let error as LocalizedError {
				guard !Task.isCancelled else { return }
				self.presenter?.setErrorMessage(error.errorDescription ?? error.localizedDescription)
			} catch {
				guard !Task.isCancelled else { return }
				self.presenter?.setErrorMessage(error.localizedDescription)
			}
		}
	}
}
