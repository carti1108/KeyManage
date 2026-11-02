import Testing
import Foundation
import KeyManageDomain
@testable import UnlockFeatureImplementation
@testable import UnlockFeatureTesting
@testable import UnlockFeatureInterface

@Suite("UnlockFeature 잠금 해제 인터랙터 및 뷰 조립 단위 테스트")
@MainActor
struct UnlockFeatureTests {

	// MARK: - 1. 마스터 비밀번호 일치 시 잠금 해제 성공
	@Test("마스터 비밀번호 일치 시 잠금 해제 성공 및 listener.didUnlockVault() 1회 호출 검증")
	func unlockWithMasterPassword_whenPasswordMatches_succeedsAndNotifiesListener() {
		// Given
		let lockManager = MockVaultLockManager()
		let interactor = UnlockInteractor(lockManager: lockManager)
		let listener = UnlockListenerMock()
		interactor.listener = listener

		// When
		interactor.unlock(withMasterPassword: "ValidMasterPassword123!")

		// Then
		#expect(lockManager.unlockWithMasterPasswordCallCount == 1)
		#expect(lockManager.unlockWithMasterPasswordReceivedPassword == "ValidMasterPassword123!")
		#expect(listener.didUnlockVaultCallCount == 1)
		#expect(interactor.errorMessage == nil)
	}

	// MARK: - 2. 마스터 비밀번호 불일치 시 SEC-003 에러 노출
	@Test("마스터 비밀번호 불일치 시 invalidMasterPassword 발생 ➔ SEC-003 표준 에러 노출 및 리스너 미호출 검증")
	func unlockWithMasterPassword_whenPasswordIncorrect_showsSEC003ErrorMessageAndDoesNotNotifyListener() {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.unlockWithMasterPasswordError = VaultLockError.invalidMasterPassword
		let interactor = UnlockInteractor(lockManager: lockManager)
		let listener = UnlockListenerMock()
		interactor.listener = listener

		// When
		interactor.unlock(withMasterPassword: "WrongPassword")

		// Then
		#expect(lockManager.unlockWithMasterPasswordCallCount == 1)
		#expect(listener.didUnlockVaultCallCount == 0)
		#expect(interactor.errorMessage == "마스터 비밀번호가 올바르지 않습니다.")
	}

	// MARK: - 3. 잠금 해제 성공 시 입력 비밀번호 메모리 초기화
	@Test("잠금 해제 성공 시 입력 비밀번호 메모리 초기화 검증")
	func unlockWithMasterPassword_whenSucceeds_zeroizesInputPassword() {
		// Given
		let lockManager = MockVaultLockManager()
		let interactor = UnlockInteractor(lockManager: lockManager)
		let listener = UnlockListenerMock()
		interactor.listener = listener
		interactor.password = "SensitiveMasterPassword"

		// When
		interactor.unlock(withMasterPassword: "SensitiveMasterPassword")

		// Then
		#expect(interactor.password.isEmpty)
	}

	// MARK: - 4. 생체 인증 성공 시 잠금 해제
	@Test("생체 인증 활성화 상태에서 생체 인증 성공 시 didUnlockVault() 호출 검증")
	func unlockWithBiometrics_whenSuccessful_notifiesListener() {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.isBiometricsEnabled = true
		let interactor = UnlockInteractor(lockManager: lockManager)
		let listener = UnlockListenerMock()
		interactor.listener = listener

		// When
		interactor.unlockWithBiometrics()

		// Then
		#expect(lockManager.unlockWithBiometricsCallCount == 1)
		#expect(listener.didUnlockVaultCallCount == 1)
		#expect(interactor.errorMessage == nil)
	}

	// MARK: - 4-1. 생체 인증 비활성화 시 해제 미시도
	@Test("생체 인증 비활성화 상태에서 생체 인증 시도 시 lockManager 호출 없이 조기 종료 검증")
	func unlockWithBiometrics_whenBiometricsDisabled_doesNotAttemptUnlock() {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.isBiometricsEnabled = false
		let interactor = UnlockInteractor(lockManager: lockManager)
		let listener = UnlockListenerMock()
		interactor.listener = listener

		// When
		interactor.unlockWithBiometrics()

		// Then
		#expect(lockManager.unlockWithBiometricsCallCount == 0)
		#expect(listener.didUnlockVaultCallCount == 0)
	}

	// MARK: - 4-2. 생체 인증 14일 만료 시 마스터 비밀번호 재요구
	@Test("생체 인증 14일 만료 시 LockError.biometricsExpiredRequirePassword 에러 노출 및 리스너 미호출 검증")
	func unlockWithBiometrics_whenBiometricsExpired_showsRequirePasswordError() {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.isBiometricsEnabled = true
		lockManager.unlockWithBiometricsError = LockError.biometricsExpiredRequirePassword
		let interactor = UnlockInteractor(lockManager: lockManager)
		let listener = UnlockListenerMock()
		interactor.listener = listener

		// When
		interactor.unlockWithBiometrics()

		// Then
		#expect(lockManager.unlockWithBiometricsCallCount == 1)
		#expect(listener.didUnlockVaultCallCount == 0)
		#expect(interactor.errorMessage == "보안을 위해 마스터 비밀번호를 다시 입력해 주세요.")
	}

	// MARK: - 4-3. 생체 정보 변경 시 무효화
	@Test("생체 정보 변경으로 무효화 시 LockError.biometricsInvalidatedRequirePassword 에러 노출 및 리스너 미호출 검증")
	func unlockWithBiometrics_whenBiometricsInvalidated_showsReauthenticationError() {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.isBiometricsEnabled = true
		lockManager.unlockWithBiometricsError = LockError.biometricsInvalidatedRequirePassword
		let interactor = UnlockInteractor(lockManager: lockManager)
		let listener = UnlockListenerMock()
		interactor.listener = listener

		// When
		interactor.unlockWithBiometrics()

		// Then
		#expect(lockManager.unlockWithBiometricsCallCount == 1)
		#expect(listener.didUnlockVaultCallCount == 0)
		#expect(interactor.errorMessage == "생체 정보가 변경되었습니다. 마스터 비밀번호를 입력해 주세요.")
	}

	// MARK: - 4-4. BiometricKeyStore 주입 시 복원된 키와 해시 사용 검증
	@Test("BiometricKeyStore 주입 시 평가된 해시 및 복원된 암호화 키를 사용하여 잠금 해제 검증")
	func unlockWithBiometrics_withBiometricKeyStore_usesRecoveredKeyAndHash() {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.isBiometricsEnabled = true
		let keyStore = MockBiometricKeyStore()
		keyStore.stubbedHash = Data([0xDE, 0xAD])
		keyStore.stubbedKey = Data([0xBE, 0xEF])
		let interactor = UnlockInteractor(lockManager: lockManager, biometricKeyStore: keyStore)
		let listener = UnlockListenerMock()
		interactor.listener = listener

		// When
		interactor.unlockWithBiometrics()

		// Then
		#expect(keyStore.evaluateAndRecoverKeyCallCount == 1)
		#expect(lockManager.unlockWithBiometricsCallCount == 1)
		#expect(lockManager.unlockWithBiometricsReceivedHash == Data([0xDE, 0xAD]))
		#expect(lockManager.unlockWithBiometricsReceivedKey == Data([0xBE, 0xEF]))
		#expect(listener.didUnlockVaultCallCount == 1)
		#expect(interactor.errorMessage == nil)
	}

	// MARK: - 5. UnlockBuilder 의존성 바인딩 검증
	@Test("UnlockBuilder가 lockManager 의존성을 올바르게 바인딩하여 Router/Interactor를 생성한다")
	func builder_wiresLockManagerDependencySuccessfully() {
		// Given
		let lockManager = MockVaultLockManager()
		final class TestDependency: UnlockDependency {
			let lockManager: VaultLockManagerProtocol
			init(lockManager: VaultLockManagerProtocol) {
				self.lockManager = lockManager
			}
		}
		let dependency = TestDependency(lockManager: lockManager)
		let builder = UnlockBuilder(dependency: dependency)
		let listener = UnlockListenerMock()

		// When
		let routing = builder.build(withListener: listener)

		// Then
		#expect(routing.interactable is UnlockInteractor)
		let interactor = routing.interactable as? UnlockInteractor
		#expect(interactor?.listener === listener)
		#expect(interactor?.router === routing)
		#expect(interactor?.lockManager === lockManager)
		#expect(routing.viewControllable is UnlockViewControllable)
	}

	// MARK: - 6. SEC-003 규칙 11 원격 변경 감지 시 안내 문구 갱신
	@Test("마스터 비밀번호 불일치 시 서버 KDF 변경이 감지되면 passwordChangedReLoginNotice로 에러 메시지가 갱신된다")
	func unlockWithMasterPassword_whenIncorrectAndServerKdfChanged_updatesErrorMessage() async {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.unlockWithMasterPasswordError = VaultLockError.invalidMasterPassword
		lockManager.checkIfPasswordChangedOnServerResult = true

		final class TestAuthNetworkClient: AuthNetworkClientProtocol, @unchecked Sendable {
			func prelogin(email: String) async throws -> PreloginResponse {
				PreloginResponse(kdf: "PBKDF2-HMAC-SHA256", salt: "dGVzdC1zYWx0", iterations: 600_000)
			}
			func changePassword(accessToken: String, request: ChangePasswordRequestPayload) async throws -> ChangePasswordResponsePayload {
				fatalError("Not used")
			}
			func deleteAccount(accessToken: String, authHash: String) async throws {}
			func register(request: RegisterRequestPayload) async throws -> RegisterResponsePayload {
				fatalError("Not used")
			}
			func login(request: LoginRequestPayload) async throws -> LoginResponsePayload {
				fatalError("Not used")
			}
			func getVault(accessToken: String) async throws -> GetVaultResponsePayload? {
				nil
			}
			func saveVault(
				accessToken: String,
				expectedRevision: Int,
				payload: EncryptedVaultPayload
			) async throws -> Int {
				1
			}
		}

		let networkClient = TestAuthNetworkClient()
		let interactor = UnlockInteractor(lockManager: lockManager, networkClient: networkClient)

		// When
		interactor.unlock(withMasterPassword: "WrongPassword")

		// Then - 비동기 KDF 감지 대기
		try? await Task.sleep(nanoseconds: 60_000_000)
		#expect(lockManager.checkIfPasswordChangedOnServerCallCount == 1)
		#expect(interactor.errorMessage == MasterPasswordChangeStrings.passwordChangedReLoginNotice)
	}
}
