import Foundation
import Testing
import MicroRIBs
import KeyManageDomain
import SettingsFeatureInterface
@testable import SettingsFeatureImplementation
import SettingsFeatureTesting

#if os(macOS)
import AppKit
#elseif os(iOS)
import UIKit
#endif

// MARK: - Test Doubles

@MainActor
final class SettingsPresenterSpy: SettingsPresentable {
	weak var listener: SettingsPresentableListener?
	var displayAutoLockTimeoutCallCount = 0
	var receivedTimeout: AutoLockTimeout?
	var showExportResultCallCount = 0
	var receivedFileName: String?
	var receivedData: Data?
	var showToastCallCount = 0
	var receivedToastMessage: String?

	func displayAutoLockTimeout(_ timeout: AutoLockTimeout) {
		displayAutoLockTimeoutCallCount += 1
		receivedTimeout = timeout
	}

	func showExportResult(fileName: String, data: Data) {
		showExportResultCallCount += 1
		receivedFileName = fileName
		receivedData = data
	}

	func showToast(message: String) {
		showToastCallCount += 1
		receivedToastMessage = message
	}

	var dismissChangePasswordModalCallCount = 0
	func dismissChangePasswordModal() {
		dismissChangePasswordModalCallCount += 1
	}

	var dismissDeleteAccountModalCallCount = 0
	func dismissDeleteAccountModal() {
		dismissDeleteAccountModalCallCount += 1
	}

	var showDeleteAccountErrorCallCount = 0
	var receivedDeleteAccountErrorMessage: String?
	func showDeleteAccountError(message: String) {
		showDeleteAccountErrorCallCount += 1
		receivedDeleteAccountErrorMessage = message
	}
}

@MainActor
final class MockSettingsInteractable: SettingsInteractable {
	weak var router: SettingsRouting?
	weak var listener: SettingsListener?

	var isActive: Bool {
		isActiveSubject.value
	}
	var isActiveStream: AsyncStream<Bool> {
		isActiveSubject.stream
	}
	private let isActiveSubject = CurrentValueAsyncStream<Bool>(false)

	func activate() {
		isActiveSubject.send(true)
	}

	func deactivate() {
		isActiveSubject.send(false)
	}
}

@MainActor
final class SettingsViewControllableSpy: SettingsViewControllable {
#if os(macOS)
	var nsViewController: NSViewController = NSViewController()
#elseif os(iOS)
	var uiViewController: UIViewController = UIViewController()
#endif
}

final class MockVaultLockManager: VaultLockManagerProtocol, @unchecked Sendable {
	var isLocked: Bool = false
	var hasActiveSession: Bool = true
	var isPrivacyCoverActive: Bool = false
	var autoLockTimeout: AutoLockTimeout = .fiveMinutes
	var lastInteractionTime: Date = Date()
	var email: String? = nil
	var sessionToken: String? = nil
	var salt: Data? = nil
	var iterations: Int = 100_000
	var encryptedVault: EncryptedVaultPayload? = nil
	var revision: Int = 1
	var masterKey: Data? = nil
	var encryptionKey: Data? = nil
	var decryptedVault: VaultData? = nil
	var isBiometricsEnabled: Bool = false
	var lastMasterPasswordUnlockDate: Date? = nil
	var currentBiometryStateHash: Data? = nil
	var onClipboardClearNeeded: (@Sendable () -> Void)? = nil

	var exportBackupCallCount = 0
	var restoreBackupCallCount = 0
	var logoutCallCount = 0
	var changeMasterPasswordCallCount = 0
	var receivedCurrentPassword: String?
	var receivedNewPassword: String?
	var shouldThrowChangePassword: Error?
	var deleteAccountCallCount = 0
	var receivedDeletePassword: String?
	var receivedConfirmationPhrase: String?
	var shouldThrowDeleteAccount: Error?
	var exportResultToReturn: (fileName: String, data: Data)? = ("keymanage-backup-test.json", Data("{\"version\":1}".utf8))
	var restoreResultToReturn: (restoredCount: Int, newVault: VaultData)? = (3, VaultData(updatedAt: "2026-11-02T00:00:00Z", items: []))
	var shouldThrowExport: Error? = nil
	var shouldThrowRestore: Error? = nil

	func setupSession(
		email: String,
		sessionToken: String,
		salt: Data,
		iterations: Int,
		encryptedVault: EncryptedVaultPayload,
		isBiometricsEnabled: Bool = false,
		biometryStateHash: Data? = nil,
		revision: Int = 1
	) {}
	func recordInteraction(at date: Date) {}
	func checkIdleTimeout(at date: Date) -> Bool { false }
	func didEnterBackground(at date: Date) {}
	func willEnterForeground(at date: Date) {}
	func lock() { isLocked = true }
	func unlockWithMasterPassword(password: String, now: Date) throws {}
	func unlockWithBiometrics(evaluatedStateHash: Data, recoveredEncryptionKey: Data, now: Date) throws {}

	func changeMasterPassword(
		currentPassword: String,
		newPassword: String,
		networkClient: AuthNetworkClientProtocol,
		now: Date
	) async throws {
		changeMasterPasswordCallCount += 1
		receivedCurrentPassword = currentPassword
		receivedNewPassword = newPassword
		if let error = shouldThrowChangePassword {
			throw error
		}
	}

	func checkIfPasswordChangedOnServer(
		networkClient: AuthNetworkClientProtocol
	) async throws -> Bool {
		false
	}

	func deleteAccount(
		password: String,
		confirmationPhrase: String,
		networkClient: AuthNetworkClientProtocol,
		now: Date
	) async throws {
		deleteAccountCallCount += 1
		receivedDeletePassword = password
		receivedConfirmationPhrase = confirmationPhrase
		if let error = shouldThrowDeleteAccount {
			throw error
		}
		isLocked = true
	}

	func logout() {
		logoutCallCount += 1
		isLocked = true
	}

	func exportBackup(now: Date) throws -> (fileName: String, data: Data) {
		exportBackupCallCount += 1
		if let error = shouldThrowExport {
			throw error
		}
		guard let result = exportResultToReturn else {
			throw KeyManageBackupError.sessionNotUnlocked
		}
		return result
	}

	func restoreBackup(fileData: Data, password: String, now: Date) throws -> (restoredCount: Int, newVault: VaultData) {
		restoreBackupCallCount += 1
		if let error = shouldThrowRestore {
			throw error
		}
		guard let result = restoreResultToReturn else {
			throw KeyManageBackupError.backupDecryptFailed
		}
		return result
	}
}

@MainActor
final class SettingsDependencyMock: SettingsDependency {
	let lockManager: VaultLockManagerProtocol

	init(lockManager: VaultLockManagerProtocol = MockVaultLockManager()) {
		self.lockManager = lockManager
	}
}

// MARK: - SettingsTests Suite

@Suite("SettingsFeature microRIBs 단위 테스트")
@MainActor
struct SettingsTests {

	// MARK: - 1. 타임아웃 동기화 및 갱신 (SEC-003)

	@Test("interactor.didBecomeActive() 시 현재 lockManager.autoLockTimeout을 presenter에 전달 검증")
	func interactor_didBecomeActive_syncsAutoLockTimeout() {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.autoLockTimeout = .oneHour
		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)

		// When
		interactor.didBecomeActive()

		// Then
		#expect(presenter.displayAutoLockTimeoutCallCount == 1)
		#expect(presenter.receivedTimeout == .oneHour)
	}

	@Test("interactor.didSelectAutoLockTimeout(.fifteenMinutes) 호출 시 lockManager.autoLockTimeout 변경 및 presenter 1회 전달 검증")
	func interactor_didSelectAutoLockTimeout_updatesLockManagerAndPresenter() {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.autoLockTimeout = .oneMinute
		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)

		// When
		interactor.didSelectAutoLockTimeout(.fifteenMinutes)

		// Then
		#expect(lockManager.autoLockTimeout == .fifteenMinutes)
		#expect(presenter.displayAutoLockTimeoutCallCount == 1)
		#expect(presenter.receivedTimeout == .fifteenMinutes)
	}

	// MARK: - 2. 암호화 백업 내보내기 (VLT-004)

	@Test("interactor.didRequestExportBackup() 성공 시 lockManager.exportBackup() 1회 호출 및 올바른 데이터 전달 검증")
	func interactor_didRequestExportBackup_success_presentsExportResult() {
		// Given
		let lockManager = MockVaultLockManager()
		let expectedFileName = "keymanage-backup-20261102.json"
		let expectedData = Data("{\"version\":1,\"backup\":true}".utf8)
		lockManager.exportResultToReturn = (expectedFileName, expectedData)

		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)

		// When
		interactor.didRequestExportBackup()

		// Then
		#expect(lockManager.exportBackupCallCount == 1)
		#expect(presenter.showExportResultCallCount == 1)
		#expect(presenter.receivedFileName == expectedFileName)
		#expect(presenter.receivedData == expectedData)
		#expect(presenter.showToastCallCount == 0)
	}

	@Test("interactor.didRequestExportBackup() 실패(sessionNotUnlocked) 시 presenter.showToast로 에러 메시지 전달 검증")
	func interactor_didRequestExportBackup_failure_showsToast() {
		// Given
		let lockManager = MockVaultLockManager()
		let expectedError = KeyManageBackupError.sessionNotUnlocked
		lockManager.shouldThrowExport = expectedError

		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)

		// When
		interactor.didRequestExportBackup()

		// Then
		#expect(lockManager.exportBackupCallCount == 1)
		#expect(presenter.showExportResultCallCount == 0)
		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.receivedToastMessage == expectedError.localizedDescription)
	}

	// MARK: - 3. 암호화 백업 복원 (VLT-004)

	@Test("interactor.didRequestRestoreBackup() 성공 시 lockManager.restoreBackup() 1회 호출 및 성공 토스트 전달 검증")
	func interactor_didRequestRestoreBackup_success_showsSuccessToast() {
		// Given
		let lockManager = MockVaultLockManager()
		let restoredCount = 5
		lockManager.restoreResultToReturn = (restoredCount, VaultData(updatedAt: "2026-11-02T00:00:00Z", items: []))

		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)
		let dummyData = Data("backup-data".utf8)
		let password = "validMasterPassword123!"

		// When
		interactor.didRequestRestoreBackup(fileData: dummyData, password: password)

		// Then
		#expect(lockManager.restoreBackupCallCount == 1)
		#expect(presenter.showToastCallCount == 1)
		let expectedMessage = KeyManageBackupStrings.restoreCompletedMessage(count: restoredCount)
		#expect(presenter.receivedToastMessage == expectedMessage)
	}

	@Test("interactor.didRequestRestoreBackup() 실패(backupDecryptFailed) 시 presenter.showToast로 에러 메시지 전달 검증")
	func interactor_didRequestRestoreBackup_failure_showsToast() {
		// Given
		let lockManager = MockVaultLockManager()
		let expectedError = KeyManageBackupError.backupDecryptFailed
		lockManager.shouldThrowRestore = expectedError

		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)
		let dummyData = Data("corrupted-data".utf8)
		let wrongPassword = "wrongPassword!"

		// When
		interactor.didRequestRestoreBackup(fileData: dummyData, password: wrongPassword)

		// Then
		#expect(lockManager.restoreBackupCallCount == 1)
		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.receivedToastMessage == expectedError.localizedDescription)
	}

	// MARK: - 4. 로그아웃 연동 (SEC-003, VLT-002)

	@Test("interactor.didRequestLogout() 호출 시 lockManager.logout() 1회 및 listener.settingsDidRequestLogout() 1회 호출 검증")
	func interactor_didRequestLogout_callsLockManagerAndListener() {
		// Given
		let lockManager = MockVaultLockManager()
		let listener = MockSettingsListener()
		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)
		interactor.listener = listener

		// When
		interactor.didRequestLogout()

		// Then
		#expect(lockManager.logoutCallCount == 1)
		#expect(lockManager.isLocked == true)
		#expect(listener.logoutCallCount == 1)
	}

	// MARK: - 5. Router & Builder 바인딩

	@Test("SettingsRouter 초기화 시 interactor.router === self 양방향 바인딩 검증")
	func router_initialization_bindsInteractorRouter() {
		// Given
		let interactor = MockSettingsInteractable()
		let viewController = SettingsViewControllableSpy()

		// When
		let router = SettingsRouter(interactor: interactor, viewController: viewController)

		// Then
		#expect(interactor.router === router)
		#expect(router.viewControllable === viewController)
	}

	@Test("SettingsBuilder.build(withListener:) 호출 시 SettingsRouting, SettingsViewControllable, SettingsInteractor 정상 조립 검증")
	func builder_build_assemblesRIBHierarchy() {
		// Given
		let lockManager = MockVaultLockManager()
		let dependency = SettingsDependencyMock(lockManager: lockManager)
		let builder = SettingsBuilder(dependency: dependency)
		let listener = MockSettingsListener()

		// When
		let routing = builder.build(withListener: listener)

		// Then
		guard let router = routing as? SettingsRouter else {
			Issue.record("Expected SettingsRouter instance")
			return
		}
		#expect(router.interactable is SettingsInteractor)
		#expect(router.viewControllable is SettingsViewController)

		guard let interactor = router.interactable as? SettingsInteractor else {
			Issue.record("Expected SettingsInteractor instance")
			return
		}
		#expect(interactor.router === router)
		#expect(interactor.listener === listener)
		#expect(interactor.lockManager === lockManager)
	}

	// MARK: - 6. ViewModel 단위 테스트

	@Test("SettingsViewModel.selectTimeout() 호출 시 selectedTimeout 상태 갱신 및 onSelectTimeout 클로저 호출 검증")
	func viewModel_selectTimeout_updatesStateAndTriggersClosure() {
		// Given
		let viewModel = SettingsViewModel()
		var receivedTimeout: AutoLockTimeout?
		viewModel.onSelectTimeout = { timeout in
			receivedTimeout = timeout
		}

		// When
		viewModel.selectTimeout(.oneHour)

		// Then
		#expect(viewModel.selectedTimeout == .oneHour)
		#expect(receivedTimeout == .oneHour)
	}

	@Test("SettingsViewModel의 exportBackup, restoreBackup, confirmLogout 호출 시 해당 핸들러 클로저 정상 트리거 검증")
	func viewModel_actions_triggerClosures() {
		// Given
		let viewModel = SettingsViewModel()
		var exportCalled = false
		var restoreCalled = false
		var receivedData: Data?
		var receivedPassword: String?
		var logoutCalled = false

		viewModel.onRequestExport = { exportCalled = true }
		viewModel.onRequestRestore = { data, password in
			restoreCalled = true
			receivedData = data
			receivedPassword = password
		}
		viewModel.onRequestLogout = { logoutCalled = true }

		let testData = Data("backup-bytes".utf8)
		let testPassword = "securePassword123!"

		// When
		viewModel.exportBackup()
		viewModel.restoreBackup(fileData: testData, password: testPassword)
		viewModel.confirmLogout()

		// Then
		#expect(exportCalled == true)
		#expect(restoreCalled == true)
		#expect(receivedData == testData)
		#expect(receivedPassword == testPassword)
		#expect(logoutCalled == true)
	}

	@Test("SettingsViewModel의 showToast, clearToast 호출 시 toastMessage 상태 정상 갱신 검증")
	func viewModel_toastManagement_updatesState() {
		// Given
		let viewModel = SettingsViewModel()
		#expect(viewModel.toastMessage == nil)

		// When: showToast
		viewModel.showToast("안전하게 복원되었습니다.")

		// Then
		#expect(viewModel.toastMessage == "안전하게 복원되었습니다.")

		// When: clearToast
		viewModel.clearToast()

		// Then
		#expect(viewModel.toastMessage == nil)
	}

	// MARK: - SEC-004 마스터 비밀번호 변경 테스트

	@Test("didRequestChangePassword 호출 시 유효한 정보면 lockManager.changeMasterPassword 호출 및 완료 토스트 검증")
	func didRequestChangePassword_withValidPasswords_callsLockManagerAndShowsSuccessToast() async {
		// Given
		let lockManager = MockVaultLockManager()
		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)

		// When
		interactor.didRequestChangePassword(
			current: "Current1234!",
			new: "NewPassword1234!",
			confirm: "NewPassword1234!"
		)
		// 비동기 Task 완료 대기
		try? await Task.sleep(nanoseconds: 50_000_000)

		// Then
		#expect(lockManager.changeMasterPasswordCallCount == 1)
		#expect(lockManager.receivedCurrentPassword == "Current1234!")
		#expect(lockManager.receivedNewPassword == "NewPassword1234!")
		#expect(presenter.dismissChangePasswordModalCallCount == 1)
		#expect(presenter.receivedToastMessage == MasterPasswordChangeStrings.completedMessage)
	}

	@Test("didRequestChangePassword 호출 시 새 비밀번호 확인 불일치면 거절 토스트 발송 및 lockManager 미호출 검증")
	func didRequestChangePassword_withMismatchConfirmation_showsErrorToastWithoutCallingLockManager() {
		// Given
		let lockManager = MockVaultLockManager()
		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)

		// When
		interactor.didRequestChangePassword(
			current: "Current1234!",
			new: "NewPassword1234!",
			confirm: "MismatchPassword123!"
		)

		// Then
		#expect(lockManager.changeMasterPasswordCallCount == 0)
		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.receivedToastMessage == MasterPasswordChangeStrings.confirmationMismatchMessage)
	}

	@Test("didRequestChangePassword 호출 시 현재 비밀번호와 동일하면 거절 토스트 발송 및 lockManager 미호출 검증")
	func didRequestChangePassword_withSamePassword_showsErrorToastWithoutCallingLockManager() {
		// Given
		let lockManager = MockVaultLockManager()
		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)

		// When
		interactor.didRequestChangePassword(
			current: "Current1234!",
			new: "Current1234!",
			confirm: "Current1234!"
		)

		// Then
		#expect(lockManager.changeMasterPasswordCallCount == 0)
		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.receivedToastMessage == MasterPasswordChangeStrings.samePasswordMessage)
	}

	@Test("didRequestChangePassword 호출 시 새 비밀번호가 12자 미만이면 거절 토스트 발송 및 lockManager 미호출 검증")
	func didRequestChangePassword_withShortPassword_showsErrorToastWithoutCallingLockManager() {
		// Given
		let lockManager = MockVaultLockManager()
		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)

		// When
		interactor.didRequestChangePassword(
			current: "Current1234!",
			new: "Short1!",
			confirm: "Short1!"
		)

		// Then
		#expect(lockManager.changeMasterPasswordCallCount == 0)
		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.receivedToastMessage == MasterPasswordChangeStrings.tooShortMessage)
	}

	@Test("didRequestChangePassword 호출 시 lockManager에서 에러 발생 시 에러 토스트 메시지 노출 검증")
	func didRequestChangePassword_whenLockManagerThrows_showsErrorToast() async {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.shouldThrowChangePassword = MasterPasswordChangeError.conflict
		let presenter = SettingsPresenterSpy()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)

		// When
		interactor.didRequestChangePassword(
			current: "Current1234!",
			new: "NewPassword1234!",
			confirm: "NewPassword1234!"
		)
		// 비동기 Task 완료 대기
		try? await Task.sleep(nanoseconds: 50_000_000)

		// Then
		#expect(lockManager.changeMasterPasswordCallCount == 1)
		#expect(presenter.dismissChangePasswordModalCallCount == 0)
		#expect(presenter.receivedToastMessage == MasterPasswordChangeStrings.conflictMessage)
	}

	// MARK: - SEC-005 계정 삭제 테스트

	@Test("didRequestDeleteAccount 호출 시 lockManager에 정상 전달되고 성공 토스트 및 로그아웃 요청 검증")
	func didRequestDeleteAccount_whenSuccessful_dismissesModalAndShowsSuccessToastAndRequestsLogout() async {
		// Given
		let lockManager = MockVaultLockManager()
		let presenter = SettingsPresenterSpy()
		let listener = MockSettingsListener()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)
		interactor.listener = listener

		// When
		interactor.didRequestDeleteAccount(
			password: "MasterPassword123!",
			confirmationPhrase: AccountDeletionStrings.confirmationPhrase
		)
		try? await Task.sleep(nanoseconds: 50_000_000)

		// Then
		#expect(lockManager.deleteAccountCallCount == 1)
		#expect(lockManager.receivedDeletePassword == "MasterPassword123!")
		#expect(lockManager.receivedConfirmationPhrase == "계정 삭제")
		#expect(presenter.dismissDeleteAccountModalCallCount == 1)
		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.receivedToastMessage == AccountDeletionStrings.successMessage)
		#expect(listener.logoutCallCount == 1)
	}

	@Test("didRequestDeleteAccount 호출 시 lockManager에서 에러 발생 시 프레젠터 에러 노출 및 로그아웃 미발생 검증")
	func didRequestDeleteAccount_whenLockManagerThrows_showsErrorMessageWithoutLogout() async {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.shouldThrowDeleteAccount = AccountDeletionError.invalidPassword
		let presenter = SettingsPresenterSpy()
		let listener = MockSettingsListener()
		let interactor = SettingsInteractor(lockManager: lockManager, presenter: presenter)
		interactor.listener = listener

		// When
		interactor.didRequestDeleteAccount(
			password: "WrongPassword123!",
			confirmationPhrase: AccountDeletionStrings.confirmationPhrase
		)
		try? await Task.sleep(nanoseconds: 50_000_000)

		// Then
		#expect(lockManager.deleteAccountCallCount == 1)
		#expect(presenter.dismissDeleteAccountModalCallCount == 0)
		#expect(presenter.showDeleteAccountErrorCallCount == 1)
		#expect(presenter.receivedDeleteAccountErrorMessage == AccountDeletionStrings.invalidPasswordMessage)
		#expect(listener.logoutCallCount == 0)
	}

	@Test("SettingsViewModel 계정 삭제 폼 상태 및 canExecuteDeleteAccount 불변식 검증")
	func settingsViewModel_deleteAccountFormValidation_ensuresExactConfirmationPhrase() {
		// Given
		let vm = SettingsViewModel()

		// When 1: 초기 상태
		#expect(vm.isDeleteAccountPresented == false)
		#expect(vm.canExecuteDeleteAccount == false)

		// When 2: 시작
		vm.startAccountDeletion()
		#expect(vm.isDeleteAccountPresented == true)

		// When 3: 비밀번호만 입력된 경우
		vm.deleteAccountPasswordInput = "Password123!"
		#expect(vm.canExecuteDeleteAccount == false)

		// When 4: 확인 문구 공백 누락("계정삭제")
		vm.deleteAccountConfirmationInput = "계정삭제"
		#expect(vm.canExecuteDeleteAccount == false)

		// When 5: 확인 문구 앞뒤 공백(" 계정 삭제")
		vm.deleteAccountConfirmationInput = " 계정 삭제"
		#expect(vm.canExecuteDeleteAccount == false)

		// When 6: 정확한 확인 문구("계정 삭제") 일치 시에만 활성화
		vm.deleteAccountConfirmationInput = AccountDeletionStrings.confirmationPhrase
		#expect(vm.canExecuteDeleteAccount == true)

		// When 7: 취소 시 초기화
		vm.cancelAccountDeletion()
		#expect(vm.isDeleteAccountPresented == false)
		#expect(vm.deleteAccountPasswordInput.isEmpty)
		#expect(vm.deleteAccountConfirmationInput.isEmpty)
	}
}
