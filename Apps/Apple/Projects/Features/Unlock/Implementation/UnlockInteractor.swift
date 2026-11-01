import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface

// MARK: - Presentable Protocols

@MainActor
protocol UnlockPresentable: AnyObject {
	var listener: UnlockPresentableListener? { get set }
	func setErrorMessage(_ message: String?)
	func setLoading(_ isLoading: Bool)
	func clearInputPassword()
}

// MARK: - Interactor

@MainActor
final class UnlockInteractor: Interactor, UnlockInteractable, UnlockPresentableListener {
	weak var router: UnlockRouting?
	weak var listener: UnlockListener?
	weak var presenter: UnlockPresentable?
	let lockManager: VaultLockManagerProtocol
	let biometricKeyStore: BiometricKeyStoreProtocol?

	var errorMessage: String?
	var isLoading: Bool = false
	var password: String = ""
	var inputPassword: String {
		get { password }
		set { password = newValue }
	}

	init(
		lockManager: VaultLockManagerProtocol,
		biometricKeyStore: BiometricKeyStoreProtocol? = nil,
		presenter: UnlockPresentable? = nil
	) {
		self.lockManager = lockManager
		self.biometricKeyStore = biometricKeyStore
		self.presenter = presenter
		super.init()
	}

	func unlockSuccess() {
		listener?.didUnlockVault()
	}

	func unlock(withMasterPassword password: String) {
		guard !password.isEmpty else { return }
		self.password = password
		isLoading = true
		presenter?.setLoading(true)
		defer {
			self.password = ""
			isLoading = false
			presenter?.setLoading(false)
		}

		do {
			try lockManager.unlockWithMasterPassword(password: password, now: Date())
			self.errorMessage = nil
			presenter?.clearInputPassword()
			presenter?.setErrorMessage(nil)
			unlockSuccess()
		} catch let error as LocalizedError {
			let msg = error.errorDescription ?? VaultLockUIStrings.invalidMasterPasswordMessage
			self.errorMessage = msg
			presenter?.setErrorMessage(msg)
		} catch {
			self.errorMessage = VaultLockUIStrings.invalidMasterPasswordMessage
			presenter?.setErrorMessage(VaultLockUIStrings.invalidMasterPasswordMessage)
		}
	}

	func unlockWithBiometrics() {
		guard lockManager.isBiometricsEnabled else { return }

		isLoading = true
		presenter?.setLoading(true)
		defer {
			isLoading = false
			presenter?.setLoading(false)
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
			self.errorMessage = nil
			presenter?.setErrorMessage(nil)
			unlockSuccess()
		} catch let error as LocalizedError {
			let msg = error.errorDescription ?? "생체 인증에 실패했습니다."
			self.errorMessage = msg
			presenter?.setErrorMessage(msg)
		} catch {
			self.errorMessage = "생체 인증에 실패했습니다."
			presenter?.setErrorMessage("생체 인증에 실패했습니다.")
		}
	}
}
