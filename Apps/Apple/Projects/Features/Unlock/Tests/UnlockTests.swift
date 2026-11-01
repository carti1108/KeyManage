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
}
