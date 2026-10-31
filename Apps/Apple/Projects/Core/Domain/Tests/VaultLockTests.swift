import Foundation
import XCTest
@testable import KeyManageDomain

final class VaultLockTests: XCTestCase {

	private final class Box<T>: @unchecked Sendable {
		var value: T
		init(_ value: T) {
			self.value = value
		}
	}

	private struct TestFixture {
		let masterPassword: String
		let email: String
		let sessionToken: String
		let salt: Data
		let iterations: Int
		let encryptedVault: EncryptedVaultPayload
		let decryptedVault: VaultData
	}

	private func makeFixture(iterations: Int = PBKDF2KeyDeriver.defaultIterations) throws -> TestFixture {
		let masterPassword = "MasterPassword123!"
		let email = "testuser@keymanage.local"
		let sessionToken = "session_token_example_12345"
		let salt = Data([
			0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
			0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
		])

		let normalized = try PasswordPreprocessor.preprocess(masterPassword)
		let masterKey = try PBKDF2KeyDeriver.deriveMasterKey(
			password: normalized,
			salt: salt,
			iterations: iterations
		)
		let encryptionKey = try HKDFKeySeparator.deriveEncryptionKey(masterKey: masterKey)

		let testVault = VaultData(
			version: 1,
			updatedAt: "2026-10-31T10:00:00Z",
			items: [
				VaultItem(
					id: "550e8400-e29b-41d4-a716-446655440000",
					serviceName: "GitHub",
					username: "octocat",
					password: "SamplePassword456!",
					createdAt: "2026-10-31T09:00:00Z",
					updatedAt: "2026-10-31T10:00:00Z"
				)
			]
		)
		let plaintext = try JSONEncoder().encode(testVault)
		let encryptedVault = try VaultCipher.encrypt(plaintext: plaintext, key: encryptionKey)

		return TestFixture(
			masterPassword: masterPassword,
			email: email,
			sessionToken: sessionToken,
			salt: salt,
			iterations: iterations,
			encryptedVault: encryptedVault,
			decryptedVault: testVault
		)
	}

	// MARK: - 1. 자동 잠금 타임아웃 옵션 및 초(sec) 검증 (SEC-003)
	func testAutoLockTimeoutOptionsAndValues() {
		// 옵션 5가지 검증: 즉시(0s), 1분(60s), 5분(300s, 기본값), 15분(900s), 1시간(3600s)
		XCTAssertEqual(AutoLockTimeout.allCases.count, 5)
		XCTAssertEqual(AutoLockTimeout.immediate.timeInterval, 0)
		XCTAssertEqual(AutoLockTimeout.oneMinute.timeInterval, 60)
		XCTAssertEqual(AutoLockTimeout.fiveMinutes.timeInterval, 300)
		XCTAssertEqual(AutoLockTimeout.fifteenMinutes.timeInterval, 900)
		XCTAssertEqual(AutoLockTimeout.oneHour.timeInterval, 3600)
		XCTAssertEqual(AutoLockTimeout.default, .fiveMinutes)

		// SEC-003: "잠그지 않음(never)" 케이스는 절대 허용되지 않음
		let allRawValues = AutoLockTimeout.allCases.map(\.rawValue)
		XCTAssertFalse(allRawValues.contains("never"))
		XCTAssertFalse(allRawValues.contains("none"))
	}

	// MARK: - 2. 유휴 타임아웃 경과 시 자동 잠금 검증
	func testIdleTimeoutLocksSession() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		manager.autoLockTimeout = .fiveMinutes
		let t0 = Date(timeIntervalSince1970: 1000)
		manager.recordInteraction(at: t0)

		// 299초 경과 (300초 미만) -> 잠기지 않음
		let t299 = t0.addingTimeInterval(299)
		let lockedAt299 = manager.checkIdleTimeout(at: t299)
		XCTAssertFalse(lockedAt299)
		XCTAssertFalse(manager.isLocked)

		// 300초 경과 (타임아웃 도달) -> 자동 잠금 격발
		let t300 = t0.addingTimeInterval(300)
		let lockedAt300 = manager.checkIdleTimeout(at: t300)
		XCTAssertTrue(lockedAt300)
		XCTAssertTrue(manager.isLocked)
	}

	// MARK: - 3. 사용자 조작 시 유휴 타이머 리셋 검증
	func testInteractionResetsIdleTimer() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		manager.autoLockTimeout = .fiveMinutes
		let t0 = Date(timeIntervalSince1970: 1000)
		manager.recordInteraction(at: t0)

		// 200초 후 조작 발생
		let t200 = t0.addingTimeInterval(200)
		manager.recordInteraction(at: t200)

		// t0 기준 350초 시점 (마지막 조작 t200 기준 150초 경과) -> 여전히 언락 유지
		let t350 = t0.addingTimeInterval(350)
		XCTAssertFalse(manager.checkIdleTimeout(at: t350))
		XCTAssertFalse(manager.isLocked)

		// t200 기준 300초 경과 (t0 기준 500초) -> 잠금 격발
		let t500 = t200.addingTimeInterval(300)
		XCTAssertTrue(manager.checkIdleTimeout(at: t500))
		XCTAssertTrue(manager.isLocked)
	}

	// MARK: - 4. 백그라운드 진입 시 가림 화면 활성화 검증
	func testBackgroundActivatesPrivacyCover() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		manager.autoLockTimeout = .fiveMinutes
		let t0 = Date(timeIntervalSince1970: 1000)
		manager.recordInteraction(at: t0)

		XCTAssertFalse(manager.isPrivacyCoverActive)

		manager.didEnterBackground(at: t0)

		// SEC-003: 백그라운드로 갈 때는 앱 전환 화면에 볼트가 비치지 않도록 가림 화면을 씌운다
		XCTAssertTrue(manager.isPrivacyCoverActive)
		XCTAssertFalse(manager.isLocked, "타임아웃(5분) 미만이므로 백그라운드 진입 즉시 잠기지는 않음")
	}

	// MARK: - 5. 즉시(immediate) 설정 시 백그라운드 진입 즉시 잠금 검증
	func testImmediateTimeoutLocksImmediatelyOnBackground() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		manager.autoLockTimeout = .immediate
		let t0 = Date(timeIntervalSince1970: 1000)

		manager.didEnterBackground(at: t0)

		// SEC-003: "즉시"는 백그라운드로 가는 순간 잠근다
		XCTAssertTrue(manager.isPrivacyCoverActive)
		XCTAssertTrue(manager.isLocked)
	}

	// MARK: - 6. 포그라운드 복귀 시 경과 시간 계산 및 지연 잠금/가림 화면 해제 검증
	func testForegroundCalculatesElapsedTime() throws {
		let fixture = try makeFixture()

		// Case A: 백그라운드 체류 중 타임아웃 경과(400초 >= 300초) 후 포그라운드 복귀
		let managerA = VaultLockManager()
		managerA.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		managerA.autoLockTimeout = .fiveMinutes
		let t0 = Date(timeIntervalSince1970: 1000)
		managerA.recordInteraction(at: t0)
		managerA.didEnterBackground(at: t0)

		let t400 = t0.addingTimeInterval(400)
		managerA.willEnterForeground(at: t400)

		// SEC-003: 백그라운드에서 타이머가 늦게 돌아도 다시 보일 때 경과 시간으로 잠김
		XCTAssertTrue(managerA.isLocked, "백그라운드에서 타임아웃(300초)을 초과 체류했으므로 복귀 시 지연 잠금되어야 합니다.")

		// Case B: 백그라운드 체류가 타임아웃 이내(100초 < 300초)인 경우 복귀
		let managerB = VaultLockManager()
		managerB.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		managerB.autoLockTimeout = .fiveMinutes
		managerB.recordInteraction(at: t0)
		managerB.didEnterBackground(at: t0)

		let t100 = t0.addingTimeInterval(100)
		managerB.willEnterForeground(at: t100)

		XCTAssertFalse(managerB.isLocked, "타임아웃 이내 복귀 시 볼트는 잠금 해제 상태를 유지해야 합니다.")
		XCTAssertFalse(managerB.isPrivacyCoverActive, "포그라운드로 복귀 시 가림 화면이 걷혀야 합니다.")
	}

	// MARK: - 7. 잠금 시 메모리의 민감 암호화 키 및 평문 볼트 소거(Zeroing) 검증
	func testLockZerosSensitiveMemory() throws {
		let fixture = try makeFixture()
		let manager = VaultLockManager()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)

		let unlockDate = Date(timeIntervalSince1970: 1000)
		try manager.unlockWithMasterPassword(password: fixture.masterPassword, now: unlockDate)

		XCTAssertNotNil(manager.masterKey)
		XCTAssertNotNil(manager.encryptionKey)
		XCTAssertNotNil(manager.decryptedVault)
		XCTAssertFalse(manager.isLocked)

		// 볼트 잠금 실행
		manager.lock()

		// SEC-003, ADR-0014: 메모리 내 볼트 암호화 키를 즉시 제로화하고 평문 볼트 파기
		XCTAssertTrue(manager.isLocked)
		XCTAssertNil(manager.masterKey, "잠금 시 마스터 키는 메모리에서 소거되어야 합니다.")
		XCTAssertNil(manager.encryptionKey, "잠금 시 암호화 키는 메모리에서 소거되어야 합니다.")
		XCTAssertNil(manager.decryptedVault, "잠금 시 평문 볼트는 메모리에서 소거되어야 합니다.")
	}

	// MARK: - 8. 볼트 잠금 전환 시 클립보드 즉시 비움 콜백 트리거 검증 (COM-001, SEC-003)
	func testLockTriggersClipboardClear() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)

		let clipboardCleared = Box(false)
		manager.onClipboardClearNeeded = {
			clipboardCleared.value = true
		}

		manager.lock()

		// COM-001: 앱이 잠기면(SEC-003) 30초를 기다리지 않고 즉시 비운다
		XCTAssertTrue(clipboardCleared.value, "볼트 잠금 시 클립보드 즉시 소각 콜백이 호출되어야 합니다.")
	}

	// MARK: - 9. 잠금 상태에서 세션 메타데이터(이메일, KDF 값, 세션 토큰, 암호화 볼트) 보존 검증
	func testLockPreservesSessionMetadata() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)

		manager.lock()

		// SEC-003: 잠긴 동안 클라이언트 메모리에는 이메일, KDF 값(salt·반복 횟수), 세션 토큰, 암호화된 볼트만 남긴다
		XCTAssertTrue(manager.isLocked)
		XCTAssertEqual(manager.email, fixture.email)
		XCTAssertEqual(manager.sessionToken, fixture.sessionToken)
		XCTAssertEqual(manager.salt, fixture.salt)
		XCTAssertEqual(manager.iterations, fixture.iterations)
		XCTAssertEqual(manager.encryptedVault, fixture.encryptedVault)
	}

	// MARK: - 10. 올바른 마스터 비밀번호로 잠금 해제 성공 검증
	func testUnlockSuccessWithCorrectMasterPassword() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		manager.lock()
		XCTAssertTrue(manager.isLocked)

		let unlockDate = Date(timeIntervalSince1970: 2000)
		try manager.unlockWithMasterPassword(password: fixture.masterPassword, now: unlockDate)

		XCTAssertFalse(manager.isLocked)
		XCTAssertFalse(manager.isPrivacyCoverActive)
		XCTAssertEqual(manager.lastMasterPasswordUnlockDate, unlockDate)
		XCTAssertNotNil(manager.masterKey)
		XCTAssertNotNil(manager.encryptionKey)
		XCTAssertEqual(manager.decryptedVault, fixture.decryptedVault)
	}

	// MARK: - 11. 잘못된 마스터 비밀번호로 잠금 해제 실패 검증
	func testUnlockFailureWithWrongMasterPassword() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		manager.lock()

		XCTAssertThrowsError(
			try manager.unlockWithMasterPassword(password: "IncorrectPassword999!", now: Date())
		) { error in
			guard let lockError = error as? LockError else {
				XCTFail("LockError가 발생해야 하지만 \(error)가 발생함")
				return
			}
			XCTAssertEqual(lockError, .invalidMasterPassword)
		}

		XCTAssertTrue(manager.isLocked)
		XCTAssertNil(manager.masterKey)
		XCTAssertNil(manager.decryptedVault)
	}

	// MARK: - 12. 14일 경과 후 생체 잠금 해제 시 비밀번호 요구 거절 검증 (SEC-003)
	func testBiometricUnlockRequiresPasswordAfter14Days() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		let biometryHash = Data([0x01, 0x02, 0x03, 0x04])
		let t0 = Date(timeIntervalSince1970: 1000)

		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault,
			isBiometricsEnabled: true,
			biometryStateHash: biometryHash
		)
		try manager.unlockWithMasterPassword(password: fixture.masterPassword, now: t0)
		manager.lock()

		// 14일 초과 시점 (14 * 86400 + 1초)
		let expiredDate = t0.addingTimeInterval(14 * 86400 + 1)

		// SEC-003: 생체 인증을 켜도 14일마다 한 번 마스터 비밀번호 요구
		XCTAssertThrowsError(
			try manager.unlockWithBiometrics(evaluatedStateHash: biometryHash, now: expiredDate)
		) { error in
			guard let lockError = error as? LockError else {
				XCTFail("LockError가 발생해야 하지만 \(error)가 발생함")
				return
			}
			XCTAssertEqual(lockError, .biometricsExpiredRequirePassword)
		}

		XCTAssertTrue(manager.isLocked)
	}

	// MARK: - 13. 기기 생체 정보 변경 감지 시 생체 잠금 해제 무효화 검증 (SEC-003)
	func testBiometricUnlockInvalidatedOnBiometryStateChange() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		let registeredHash = Data([0x01, 0x02, 0x03, 0x04])
		let alteredHash = Data([0xFF, 0xFE, 0xFD, 0xFC])
		let t0 = Date(timeIntervalSince1970: 1000)

		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault,
			isBiometricsEnabled: true,
			biometryStateHash: registeredHash
		)
		try manager.unlockWithMasterPassword(password: fixture.masterPassword, now: t0)
		manager.lock()

		let validDate = t0.addingTimeInterval(3600) // 1시간 경과 (14일 이내)

		// SEC-003: 기기에 생체 정보가 새로 등록되면 생체 해제를 무효화하고 마스터 비밀번호를 요구한다
		XCTAssertThrowsError(
			try manager.unlockWithBiometrics(evaluatedStateHash: alteredHash, now: validDate)
		) { error in
			guard let lockError = error as? LockError else {
				XCTFail("LockError가 발생해야 하지만 \(error)가 발생함")
				return
			}
			XCTAssertEqual(lockError, .biometricsInvalidatedRequirePassword)
		}

		XCTAssertTrue(manager.isLocked)
	}

	// MARK: - 14. 로그아웃 시 모든 세션 및 민감 데이터 완전 소거 검증
	func testLogoutScrubsAllSessionData() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault,
			isBiometricsEnabled: true,
			biometryStateHash: Data([0x12, 0x34])
		)

		// 로그아웃 호출
		manager.logout()

		// SEC-003: 로그아웃과 달리 잠금은 세션 토큰을 유지함. 역으로 로그아웃은 세션 토큰 및 메타데이터까지 전수 소거해야 함.
		XCTAssertNil(manager.email)
		XCTAssertNil(manager.sessionToken)
		XCTAssertNil(manager.salt)
		XCTAssertEqual(manager.iterations, 0)
		XCTAssertNil(manager.encryptedVault)
		XCTAssertNil(manager.masterKey)
		XCTAssertNil(manager.encryptionKey)
		XCTAssertNil(manager.decryptedVault)
		XCTAssertNil(manager.currentBiometryStateHash)
		XCTAssertNil(manager.lastMasterPasswordUnlockDate)
	}

	// MARK: - 15. 즉시(immediate) 옵션 시 포그라운드 유휴 체크에서 즉시 잠기지 않음 검증 (SEC-003, P2-1)
	func testCheckIdleTimeoutDoesNotLockInForegroundWhenImmediateTimeout() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		manager.autoLockTimeout = .immediate
		let t0 = Date(timeIntervalSince1970: 1000)
		manager.recordInteraction(at: t0)

		// SEC-003: "즉시" 옵션은 백그라운드로 가는 순간 잠근다.
		// 포그라운드에서 사용자가 화면을 보고 있을 때는 checkIdleTimeout이 호출되어도 즉시 잠기지 않아야 한다.
		let t10 = t0.addingTimeInterval(10)
		let didLock = manager.checkIdleTimeout(at: t10)

		XCTAssertFalse(didLock, "즉시(immediate) 옵션은 백그라운드 전환 시 잠그는 옵션이므로 포그라운드 유휴 체크 시 잠기지 않아야 합니다.")
		XCTAssertFalse(manager.isLocked, "포그라운드 체류 중에는 볼트가 잠금 상태가 되지 않아야 합니다.")
	}

	// MARK: - 16. 생체 잠금 해제 성공 시 decryptedVault 복원 검증 (SEC-003, P2-2)
	func testBiometricUnlockRestoresDecryptedVault() throws {
		let manager = VaultLockManager()
		let fixture = try makeFixture()
		let biometryHash = Data([0x01, 0x02, 0x03, 0x04])
		let t0 = Date(timeIntervalSince1970: 1000)

		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault,
			isBiometricsEnabled: true,
			biometryStateHash: biometryHash
		)

		// 최초 1회 마스터 패스워드로 언락하여 encryptionKey 획득 및 14일 타이머 시작
		try manager.unlockWithMasterPassword(password: fixture.masterPassword, now: t0)
		guard let recoveredKey = manager.encryptionKey else {
			XCTFail("unlockWithMasterPassword 성공 후 encryptionKey가 존재해야 합니다.")
			return
		}

		// 볼트 잠금
		manager.lock()
		XCTAssertTrue(manager.isLocked)
		XCTAssertNil(manager.decryptedVault)
		XCTAssertNil(manager.encryptionKey)

		// 1시간 뒤 키체인에서 복원된 암호화 키로 생체 인증 잠금 해제 (14일 이내)
		let unlockDate = t0.addingTimeInterval(3600)
		try manager.unlockWithBiometrics(
			evaluatedStateHash: biometryHash,
			recoveredEncryptionKey: recoveredKey,
			now: unlockDate
		)

		// SEC-003: 생체 인증으로 잠금 해제 성공 시 볼트가 복호화되어 decryptedVault가 복원되어야 함
		XCTAssertFalse(manager.isLocked, "생체 인증 성공 시 잠금이 해제되어야 합니다.")
		XCTAssertFalse(manager.isPrivacyCoverActive, "생체 인증 성공 시 가림 화면이 해제되어야 합니다.")
		XCTAssertEqual(manager.encryptionKey, recoveredKey, "복원된 암호화 키가 메모리에 설정되어야 합니다.")
		XCTAssertEqual(manager.decryptedVault, fixture.decryptedVault, "복원된 암호화 키로 복호화된 볼트 데이터가 유효하게 복원되어야 합니다.")
	}

	// MARK: - 17. 잠금 상태에서 포그라운드 복귀 시 가림 화면 해제 검증 (SEC-003, P2-3)
	func testWillEnterForegroundDeactivatesPrivacyCoverEvenWhenLocked() throws {
		let fixture = try makeFixture()

		// Case A: 이미 잠긴 상태에서 백그라운드 전환 후 포그라운드 복귀
		let managerA = VaultLockManager()
		managerA.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		let t0 = Date(timeIntervalSince1970: 1000)
		managerA.lock()
		XCTAssertTrue(managerA.isLocked)

		managerA.didEnterBackground(at: t0)
		XCTAssertTrue(managerA.isPrivacyCoverActive, "백그라운드 전환 시 앱 스위처 캡처 방지 가림막 활성화")

		let t10 = t0.addingTimeInterval(10)
		managerA.willEnterForeground(at: t10)

		// SEC-003: 앱이 포그라운드로 복귀할 때는 잠금 해제 폼 UI가 보여야 하므로 가림 화면이 걷혀야 함
		XCTAssertTrue(managerA.isLocked, "잠금 상태는 유지되어야 합니다.")
		XCTAssertFalse(managerA.isPrivacyCoverActive, "이미 잠긴 상태라도 포그라운드 복귀 시 잠금 해제 폼 UI 노출을 위해 가림 화면이 걷혀야 합니다.")

		// Case B: 백그라운드 체류 중 타임아웃 경과로 포그라운드 복귀 시 지연 잠금되는 경우
		let managerB = VaultLockManager()
		managerB.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault
		)
		managerB.autoLockTimeout = .fiveMinutes
		managerB.recordInteraction(at: t0)
		managerB.didEnterBackground(at: t0)
		XCTAssertTrue(managerB.isPrivacyCoverActive)

		let t400 = t0.addingTimeInterval(400) // 300초 초과 체류
		managerB.willEnterForeground(at: t400)

		XCTAssertTrue(managerB.isLocked, "타임아웃 경과로 볼트는 잠금 상태가 되어야 합니다.")
		XCTAssertFalse(managerB.isPrivacyCoverActive, "타임아웃 경과로 잠기더라도 포그라운드 복귀 시 가림 화면은 걷혀야 합니다.")
	}
}
