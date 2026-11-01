import Foundation
import XCTest
@testable import KeyManageDomain

final class DeviceVaultStoreTests: XCTestCase {

	private final class MockDeviceVaultStorage: DeviceVaultStorageProtocol, @unchecked Sendable {
		var storedRecord: DeviceVaultRecord?
		var saveCallCount: Int = 0
		var loadCallCount: Int = 0
		var deleteCallCount: Int = 0
		var shouldThrowSaveError: Bool = false
		var shouldThrowDeleteError: Bool = false

		func load() async throws -> DeviceVaultRecord? {
			loadCallCount += 1
			return storedRecord
		}

		func save(_ record: DeviceVaultRecord) async throws {
			saveCallCount += 1
			if shouldThrowSaveError {
				throw DeviceVaultStoreError.storageSaveFailed("Disk full")
			}
			storedRecord = record
		}

		func delete() async throws {
			deleteCallCount += 1
			if shouldThrowDeleteError {
				throw DeviceVaultStoreError.storageDeleteFailed("Permission denied")
			}
			storedRecord = nil
		}
	}

	private func makeSamplePayload() -> EncryptedVaultPayload {
		return EncryptedVaultPayload(
			version: 1,
			cipher: "AES-256-GCM",
			iv: "MDEyMzQ1Njc4OWFi",
			ciphertext: "c2FtcGxlY2lwaGVydGV4dA==",
			tag: "MDEyMzQ1Njc4OWFiY2RlZg=="
		)
	}

	private func makeSampleRecord(
		email: String = "user1@example.com",
		baseRevision: Int = 5,
		lastSeenRevision: Int = 5,
		hasPendingSync: Bool = false,
		salt: Data = Data([0x01, 0x02, 0x03]),
		iterations: Int = 100_000
	) -> DeviceVaultRecord {
		return DeviceVaultRecord(
			email: email,
			encryptedVault: makeSamplePayload(),
			baseRevision: baseRevision,
			lastSeenRevision: lastSeenRevision,
			hasPendingSync: hasPendingSync,
			kdfSalt: salt,
			kdfIterations: iterations
		)
	}

	// 1. 기기 기록 저장 시 storage에 영속화
	func test_saveRecord_persistsRecordToStorage() async throws {
		let storage = MockDeviceVaultStorage()
		let store = DeviceVaultStore(storage: storage)
		let record = makeSampleRecord()

		try await store.saveRecord(record)

		XCTAssertEqual(storage.saveCallCount, 1)
		XCTAssertEqual(storage.storedRecord?.email, "user1@example.com")
		XCTAssertEqual(store.currentRecord?.email, "user1@example.com")
	}

	// 2. 기기 기록 로드 시 storage로부터 복원
	func test_loadRecord_restoresRecordFromStorage() async throws {
		let storage = MockDeviceVaultStorage()
		let expectedRecord = makeSampleRecord(email: "restored@example.com", baseRevision: 10)
		storage.storedRecord = expectedRecord
		let store = DeviceVaultStore(storage: storage)

		let loaded = try await store.loadRecord()

		XCTAssertEqual(storage.loadCallCount, 1)
		XCTAssertNotNil(loaded)
		XCTAssertEqual(loaded?.email, "restored@example.com")
		XCTAssertEqual(loaded?.baseRevision, 10)
		XCTAssertEqual(store.currentRecord?.email, "restored@example.com")
	}

	// 3. 기기 계정 수 1개 제한: 새 계정 저장 시 기존 계정 덮어쓰기
	func test_singleAccountLimit_overwritesPreviousAccount() async throws {
		let storage = MockDeviceVaultStorage()
		let store = DeviceVaultStore(storage: storage)

		let record1 = makeSampleRecord(email: "first@example.com")
		try await store.saveRecord(record1)

		let record2 = makeSampleRecord(email: "second@example.com")
		try await store.saveRecord(record2)

		XCTAssertEqual(storage.storedRecord?.email, "second@example.com")
		XCTAssertEqual(store.currentRecord?.email, "second@example.com")
	}

	// 4. 다른 계정 인증 성공 시 이전 기록 즉시 삭제
	func test_handleAuthenticationSuccess_differentAccount_deletesPreviousRecord() async throws {
		let storage = MockDeviceVaultStorage()
		let initialRecord = makeSampleRecord(email: "old_user@example.com")
		storage.storedRecord = initialRecord
		let store = DeviceVaultStore(storage: storage)
		_ = try await store.loadRecord()

		try await store.handleAuthenticationSuccess(email: "new_user@example.com")

		XCTAssertEqual(storage.deleteCallCount, 1)
		XCTAssertNil(storage.storedRecord)
		XCTAssertNil(store.currentRecord)
	}

	// 5. 다른 계정 인증 실패 시에는 이전 기록을 유지
	func test_handleAuthenticationFailure_doesNotDeleteRecord() async throws {
		let storage = MockDeviceVaultStorage()
		let initialRecord = makeSampleRecord(email: "current_user@example.com")
		storage.storedRecord = initialRecord
		let store = DeviceVaultStore(storage: storage)
		_ = try await store.loadRecord()

		await store.handleAuthenticationFailure(email: "attacker@example.com")

		XCTAssertEqual(storage.deleteCallCount, 0)
		XCTAssertNotNil(storage.storedRecord)
		XCTAssertEqual(store.currentRecord?.email, "current_user@example.com")
	}

	// 6. 동일 계정 인증 성공 시 기록 유지
	func test_handleAuthenticationSuccess_sameAccount_keepsRecord() async throws {
		let storage = MockDeviceVaultStorage()
		let initialRecord = makeSampleRecord(email: "same_user@example.com")
		storage.storedRecord = initialRecord
		let store = DeviceVaultStore(storage: storage)
		_ = try await store.loadRecord()

		try await store.handleAuthenticationSuccess(email: "same_user@example.com")

		XCTAssertEqual(storage.deleteCallCount, 0)
		XCTAssertNotNil(storage.storedRecord)
	}

	// 7. 로그아웃 시 기기 기록 즉시 삭제
	func test_handleLogout_deletesDeviceRecordImmediately() async throws {
		let storage = MockDeviceVaultStorage()
		let initialRecord = makeSampleRecord(email: "logout_user@example.com")
		storage.storedRecord = initialRecord
		let store = DeviceVaultStore(storage: storage)
		_ = try await store.loadRecord()

		try await store.handleLogout()

		XCTAssertEqual(storage.deleteCallCount, 1)
		XCTAssertNil(storage.storedRecord)
		XCTAssertNil(store.currentRecord)
	}

	// 8. 서버 KDF 변경 감지 시 대기 내용 폐기 대상 플래그 반환
	func test_evaluateKdfChange_differentKdf_returnsTrueToDiscardPendingSync() async throws {
		let storage = MockDeviceVaultStorage()
		let originalSalt = Data([0x01, 0x02, 0x03])
		let originalIterations = 100_000
		let initialRecord = makeSampleRecord(
			email: "user@example.com",
			hasPendingSync: true,
			salt: originalSalt,
			iterations: originalIterations
		)
		storage.storedRecord = initialRecord
		let store = DeviceVaultStore(storage: storage)
		_ = try await store.loadRecord()

		// 변경된 KDF 파라미터 (다른 솔트)
		let newSalt = Data([0x04, 0x05, 0x06])
		let isKdfChanged = try await store.evaluateKdfChange(newSalt: newSalt, newIterations: originalIterations)

		XCTAssertTrue(isKdfChanged)
		XCTAssertEqual(store.currentRecord?.hasPendingSync, false)
	}

	// 9. KDF 동일 시 대기 내용 보존
	func test_evaluateKdfChange_sameKdf_returnsFalsePreservesPendingSync() async throws {
		let storage = MockDeviceVaultStorage()
		let originalSalt = Data([0x01, 0x02, 0x03])
		let originalIterations = 100_000
		let initialRecord = makeSampleRecord(
			email: "user@example.com",
			hasPendingSync: true,
			salt: originalSalt,
			iterations: originalIterations
		)
		storage.storedRecord = initialRecord
		let store = DeviceVaultStore(storage: storage)
		_ = try await store.loadRecord()

		let isKdfChanged = try await store.evaluateKdfChange(newSalt: originalSalt, newIterations: originalIterations)

		XCTAssertFalse(isKdfChanged)
		XCTAssertEqual(store.currentRecord?.hasPendingSync, true)
	}

	// 10. 기기 저장 실패 시 hasStorageSaveError 경고 플래그 설정 및 유지
	func test_storageSaveFailure_setsWarningFlagUntilNextSuccess() async throws {
		let storage = MockDeviceVaultStorage()
		storage.shouldThrowSaveError = true
		let store = DeviceVaultStore(storage: storage)
		let record = makeSampleRecord()

		do {
			try await store.saveRecord(record)
			XCTFail("스토리지 저장 실패 에러가 throw되어야 합니다.")
		} catch {
			// Expected error
		}

		XCTAssertTrue(store.hasStorageSaveError)

		// 다음 저장 시도 실패 시에도 플래그 유지
		do {
			try await store.saveRecord(record)
		} catch {}
		XCTAssertTrue(store.hasStorageSaveError)

		// 저장 성공 시 플래그 해제
		storage.shouldThrowSaveError = false
		try await store.saveRecord(record)
		XCTAssertFalse(store.hasStorageSaveError)
	}

	// 11. 로그아웃 시 기록 삭제 실패 시 에러 알림
	func test_logoutDeletionFailure_throwsOrNotifiesError() async throws {
		let storage = MockDeviceVaultStorage()
		storage.shouldThrowDeleteError = true
		let store = DeviceVaultStore(storage: storage)

		do {
			try await store.handleLogout()
			XCTFail("로그아웃 삭제 실패 시 에러가 발생해야 합니다.")
		} catch {
			XCTAssertTrue(error is DeviceVaultStoreError)
		}
	}
}
