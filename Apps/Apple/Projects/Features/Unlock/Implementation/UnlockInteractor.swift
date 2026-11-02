import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface

// MARK: - Presentable Protocols

@MainActor
protocol UnlockPresentable: AnyObject {
	var listener: UnlockPresentableListener? { get set }
	func update(state: UnlockViewState)
	func setErrorMessage(_ message: String?)
	func setLoading(_ isLoading: Bool)
	func clearInputPassword()
}

extension UnlockPresentable {
	func update(state: UnlockViewState) {}
	func setErrorMessage(_ message: String?) {}
	func setLoading(_ isLoading: Bool) {}
	func clearInputPassword() {}
}

// MARK: - Interactor

@MainActor
final class UnlockInteractor: Interactor, UnlockInteractable, UnlockPresentableListener {
	weak var router: UnlockRouting?
	weak var listener: UnlockListener?
	weak var presenter: UnlockPresentable?
	let lockManager: VaultLockManagerProtocol
	let biometricKeyStore: BiometricKeyStoreProtocol?
	let networkClient: AuthNetworkClientProtocol?

	private(set) var state = UnlockViewState()

	var errorMessage: String? {
		get { state.errorMessage }
		set { state.errorMessage = newValue }
	}
	var isLoading: Bool {
		get { state.isLoading }
		set { state.isLoading = newValue }
	}
	var password: String {
		get { state.password }
		set { state.password = newValue }
	}
	var inputPassword: String {
		get { state.password }
		set { state.password = newValue }
	}

	init(
		lockManager: VaultLockManagerProtocol,
		biometricKeyStore: BiometricKeyStoreProtocol? = nil,
		networkClient: AuthNetworkClientProtocol? = nil,
		presenter: UnlockPresentable? = nil
	) {
		self.lockManager = lockManager
		self.biometricKeyStore = biometricKeyStore
		self.networkClient = networkClient
		self.presenter = presenter
		self.state = UnlockViewState(isBiometricsEnabled: lockManager.isBiometricsEnabled)
		super.init()
		presenter?.listener = self
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		request(action: .viewDidLoad)
	}

	func request(action: UnlockViewAction) {
		switch action {
		case .viewDidLoad:
			state.isBiometricsEnabled = lockManager.isBiometricsEnabled
			presenter?.update(state: state)
		case .updatePassword(let newPassword):
			state.password = newPassword
			presenter?.update(state: state)
		case .unlockWithPassword(let password):
			unlock(withMasterPassword: password)
		case .unlockWithBiometrics:
			unlockWithBiometrics()
		case .clearPassword:
			state.password = ""
			presenter?.clearInputPassword()
			presenter?.update(state: state)
		}
	}

	func unlockSuccess() {
		listener?.didUnlockVault()
	}

	func unlock(withMasterPassword password: String) {
		guard !password.isEmpty else { return }
		state.password = password
		state.isLoading = true
		presenter?.setLoading(true)
		presenter?.update(state: state)
		defer {
			state.password = ""
			state.isLoading = false
			presenter?.setLoading(false)
			presenter?.update(state: state)
		}

		do {
			try lockManager.unlockWithMasterPassword(password: password, now: Date())
			state.errorMessage = nil
			state.password = ""
			presenter?.clearInputPassword()
			presenter?.setErrorMessage(nil)
			presenter?.update(state: state)
			unlockSuccess()
		} catch {
			if let networkClient = networkClient {
				Task { [weak self] in
					guard let self = self, self.lockManager.isLocked else { return }
					if let changed = try? await self.lockManager.checkIfPasswordChangedOnServer(networkClient: networkClient), changed {
						guard self.lockManager.isLocked else { return }
						self.state.errorMessage = MasterPasswordChangeStrings.passwordChangedReLoginNotice
						self.presenter?.setErrorMessage(MasterPasswordChangeStrings.passwordChangedReLoginNotice)
						self.presenter?.update(state: self.state)
					}
				}
			}
			let msg = (error as? LocalizedError)?.errorDescription ?? VaultLockUIStrings.invalidMasterPasswordMessage
			state.errorMessage = msg
			presenter?.setErrorMessage(msg)
			presenter?.update(state: state)
		}
	}

	func unlockWithBiometrics() {
		guard lockManager.isBiometricsEnabled else { return }

		state.isLoading = true
		presenter?.setLoading(true)
		presenter?.update(state: state)
		defer {
			state.isLoading = false
			presenter?.setLoading(false)
			presenter?.update(state: state)
		}

		do {
			let stateHash: Data
			let recoveredKey: Data
			if let biometricKeyStore = biometricKeyStore {
				(stateHash, recoveredKey) = try biometricKeyStore.evaluateAndRecoverKey()
			} else {
				stateHash = lockManager.currentBiometryStateHash ?? Data()
				recoveredKey = lockManager.encryptionKey ?? Data()
			}

			try lockManager.unlockWithBiometrics(
				evaluatedStateHash: stateHash,
				recoveredEncryptionKey: recoveredKey,
				now: Date()
			)
			state.errorMessage = nil
			presenter?.setErrorMessage(nil)
			presenter?.update(state: state)
			unlockSuccess()
		} catch let error as LocalizedError {
			let msg = error.errorDescription ?? "생체 인증에 실패했습니다."
			state.errorMessage = msg
			presenter?.setErrorMessage(msg)
			presenter?.update(state: state)
		} catch {
			state.errorMessage = "생체 인증에 실패했습니다."
			presenter?.setErrorMessage("생체 인증에 실패했습니다.")
			presenter?.update(state: state)
		}
	}
}
