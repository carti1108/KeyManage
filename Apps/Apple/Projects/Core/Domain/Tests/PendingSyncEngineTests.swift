import Foundation
import XCTest
@testable import KeyManageDomain

final class PendingSyncEngineTests: XCTestCase {

	private final class MockRemoteVaultSyncService: RemoteVaultSyncServiceProtocol, @unchecked Sendable {
		var uploadCallCount: Int = 0
		var fetchCallCount: Int = 0
		var lastUploadedVault: VaultData?
		var lastExpectedRevision: Int?
		var uploadError: Error?
		var fetchError: Error?
		var nextRevisionToReturn: Int = 1
		var vaultToFetch: VaultData?
		var revisionToFetch: Int = 1

		func uploadVault(_ vault: VaultData, expectedRevision: Int) async throws -> Int {
			uploadCallCount += 1
			lastUploadedVault = vault
			lastExpectedRevision = expectedRevision
			if let uploadError {
				throw uploadError
			}
			return nextRevisionToReturn
		}

		func fetchVault() async throws -> (vault: VaultData, revision: Int) {
			fetchCallCount += 1
			if let fetchError {
				throw fetchError
			}
			let vault = vaultToFetch ?? VaultData(updatedAt: "2026-11-01T00:00:00Z", items: [])
			return (vault, revisionToFetch)
		}
	}

	private final class MockDeviceStore: DeviceVaultStoreProtocol, @unchecked Sendable {
		var hasStorageSaveError: Bool = false
		var currentRecord: DeviceVaultRecord?
		var clearCallCount: Int = 0
		var saveCallCount: Int = 0

		func loadRecord() async throws -> DeviceVaultRecord? {
			return currentRecord
		}

		func saveRecord(_ record: DeviceVaultRecord) async throws {
			saveCallCount += 1
			currentRecord = record
		}

		func handleAuthenticationSuccess(email: String) async throws {
			if currentRecord?.email != email {
				currentRecord = nil
				clearCallCount += 1
			}
		}

		func handleAuthenticationFailure(email: String) async {}

		func handleLogout() async throws {
			currentRecord = nil
			clearCallCount += 1
		}

		func evaluateKdfChange(newSalt: Data, newIterations: Int) async throws -> Bool {
			return false
		}
	}

	private func makeSampleVault(isReadOnly: Bool = false, itemCount: Int = 1) -> VaultData {
		var items: [VaultItem] = []
		for i in 0..<itemCount {
			items.append(
				VaultItem(
					id: "item-\(i)",
					serviceName: "Service \(i)",
					username: "user\(i)",
					password: "password\(i)",
					createdAt: "2026-11-01T00:00:00Z",
					updatedAt: "2026-11-01T00:00:00Z"
				)
			)
		}
		return VaultData(
			version: 1,
			updatedAt: "2026-11-01T00:00:00Z",
			items: items,
			isReadOnly: isReadOnly
		)
	}

	// 1. 일시적 실패 (네트워크 에러) 시: 대기로 두고 성공 반환
	func test_save_whenNetworkError_storesPendingAndReturnsSuccess() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.notConnectedToInternet)
		let engine = PendingSyncEngine(remoteService: remoteService)
		let vault = makeSampleVault()

		let success = try await engine.save(vault: vault, expectedRevision: 2)

		XCTAssertTrue(success, "일시적 네트워크 에러 시 저장은 성공으로 처리되어야 합니다.")
		XCTAssertTrue(engine.status.hasPendingSync)
		XCTAssertEqual(engine.status.pendingVault?.items.count, 1)
		XCTAssertEqual(engine.status.baseRevision, 2)
	}

	// 2. 일시적 실패 (서버 5xx 또는 429) 시: 대기로 두고 성공 반환
	func test_save_whenServerError5xxOr429_storesPendingAndReturnsSuccess() async throws {
		let remoteService = MockRemoteVaultSyncService()
		struct Server500Error: Error {}
		remoteService.uploadError = Server500Error()
		let engine = PendingSyncEngine(remoteService: remoteService)
		let vault = makeSampleVault()

		let success = try await engine.save(vault: vault, expectedRevision: 3)

		XCTAssertTrue(success)
		XCTAssertTrue(engine.status.hasPendingSync)
		XCTAssertEqual(engine.status.baseRevision, 3)
	}

	// 3. 일시적 실패 (타임아웃) 시: 대기로 두고 성공 반환
	func test_save_whenTimeout_storesPendingAndReturnsSuccess() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.timedOut)
		let engine = PendingSyncEngine(remoteService: remoteService)
		let vault = makeSampleVault()

		let success = try await engine.save(vault: vault, expectedRevision: 4)

		XCTAssertTrue(success)
		XCTAssertTrue(engine.status.hasPendingSync)
	}

	// 4. 분명한 거절 (읽기 전용, 용량 초과 등) 시: 즉시 throw 및 자동 재전송 중단
	func test_save_whenDefinitiveRejection_throwsErrorAndPausesAutoSync() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = VaultError.vaultIsReadOnly
		let engine = PendingSyncEngine(remoteService: remoteService)
		let readOnlyVault = makeSampleVault(isReadOnly: true)

		do {
			_ = try await engine.save(vault: readOnlyVault, expectedRevision: 1)
			XCTFail("분명한 거절 시에는 즉시 에러가 발생해야 합니다.")
		} catch {
			// Expected rejection
		}

		XCTAssertFalse(engine.status.hasPendingSync)
		XCTAssertTrue(engine.status.isAutoSyncPaused, "분명한 거절 후 자동 재전송은 중단되어야 합니다.")
	}

	// 5. 서버 규약 위반 (응답 revision 불일치) 시: 대기로 숨기지 않고 즉시 에러
	func test_save_whenServerRevisionMismatch_throwsProtocolViolationError() async throws {
		let remoteService = MockRemoteVaultSyncService()
		// 기대 revision은 2(기존 1 -> 새 2 기대)인데 서버가 999 반환 등 규약 위반 시뮬레이션
		remoteService.nextRevisionToReturn = 999
		let engine = PendingSyncEngine(remoteService: remoteService)
		let vault = makeSampleVault()

		do {
			_ = try await engine.save(vault: vault, expectedRevision: 1)
			XCTFail("서버 규약 위반(revision 불일치) 시 에러가 throw되어야 합니다.")
		} catch {
			// Expected protocol violation error
		}

		XCTAssertFalse(engine.status.hasPendingSync)
	}

	// 6. 대기 중 추가 수정 누적: 대기 내용 위에 쌓고 baseRevision은 서버 맞춘 값 유지
	func test_save_multipleConsecutiveEditsWhilePending_accumulatesOnPendingAndKeepsBaseRevision() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.networkConnectionLost)
		let engine = PendingSyncEngine(remoteService: remoteService)

		let initialVault = makeSampleVault(itemCount: 1)
		_ = try await engine.save(vault: initialVault, expectedRevision: 5)
		XCTAssertEqual(engine.status.baseRevision, 5)

		// 대기 중 2번째 수정 누적
		let updatedVault = makeSampleVault(itemCount: 3)
		_ = try await engine.save(vault: updatedVault, expectedRevision: 5)

		XCTAssertTrue(engine.status.hasPendingSync)
		XCTAssertEqual(engine.status.pendingVault?.items.count, 3)
		XCTAssertEqual(engine.status.baseRevision, 5, "baseRevision은 마지막으로 서버와 맞춘 기준값을 유지해야 합니다.")
	}

	// 7. 오프라인 상태 경고 문구 표준 일치
	func test_offlineWarning_displaysStandardMessageWhenOffline() {
		let engine = PendingSyncEngine()
		engine.setOffline(true)

		XCTAssertTrue(engine.status.isOffline)
		XCTAssertEqual(
			engine.status.offlineWarningMessage,
			"오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다."
		)
		XCTAssertEqual(PendingSyncStrings.offlineWarning, "오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다.")
	}

	// 8. 네트워크 재연결 시 즉시 재전송 트리거
	func test_resync_triggeredOnNetworkReconnection() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.notConnectedToInternet)
		let engine = PendingSyncEngine(remoteService: remoteService)
		let vault = makeSampleVault()

		_ = try await engine.save(vault: vault, expectedRevision: 1)
		XCTAssertTrue(engine.status.hasPendingSync)

		// 네트워크 정상 복구 후 재연결 트리거
		remoteService.uploadError = nil
		remoteService.nextRevisionToReturn = 2
		try await engine.triggerNetworkReconnected()

		XCTAssertFalse(engine.status.hasPendingSync, "재연결 성공 후 대기 내용은 해제되어야 합니다.")
		XCTAssertEqual(remoteService.uploadCallCount, 2)
	}

	// 9. 주기 타이머 간격 30초 검증
	func test_resync_timerIntervalIs30Seconds() {
		let engine = PendingSyncEngine()
		XCTAssertEqual(engine.retryInterval, 30.0)
		XCTAssertEqual(PendingSyncEngine.defaultRetryInterval, 30.0)
	}

	// 10. 재전송 중 분명한 거절 시 자동 재전송 중단
	func test_resync_definitiveRejectionPausesAutoSyncUntilUserAction() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.notConnectedToInternet)
		let engine = PendingSyncEngine(remoteService: remoteService)
		let vault = makeSampleVault()
		_ = try await engine.save(vault: vault, expectedRevision: 1)

		// 재전송 시도 중 서버가 영구 거절(예: 용량 초과) 응답
		remoteService.uploadError = VaultSyncError.payloadTooLarge(6_000_000)

		do {
			try await engine.triggerTimerTick()
		} catch {}

		XCTAssertTrue(engine.status.isAutoSyncPaused, "분명한 거절 후에는 재전송 타이머가 중단되어야 합니다.")
	}

	// 11. 잠금 해제 직후: 서버 볼트 먼저 가져와 되돌림 검증 후 병합 업로드
	func test_unlock_fetchesServerVaultFirstAndValidatesRollbackBeforeUploadingPending() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.notConnectedToInternet)
		let engine = PendingSyncEngine(remoteService: remoteService)
		let localVault = makeSampleVault(itemCount: 2)
		_ = try await engine.save(vault: localVault, expectedRevision: 3)

		// 잠금 해제 시나리오: 서버 볼트는 revision 4로 앞서 있음
		remoteService.uploadError = nil
		let serverVault = makeSampleVault(itemCount: 3)
		remoteService.vaultToFetch = serverVault
		remoteService.revisionToFetch = 4
		remoteService.nextRevisionToReturn = 5

		let unlockedMergedVault = try await engine.triggerUnlock(serverVault: serverVault, serverRevision: 4)

		XCTAssertEqual(remoteService.fetchCallCount, 1, "잠금 해제 직후 서버 볼트를 먼저 가져와야 합니다.")
		XCTAssertFalse(engine.status.hasPendingSync)
		XCTAssertNotNil(unlockedMergedVault)
	}

	// 12. 기기 잠금 시 대기 내용 보존
	func test_lock_preservesPendingChanges() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.notConnectedToInternet)
		let engine = PendingSyncEngine(remoteService: remoteService)
		let vault = makeSampleVault()
		_ = try await engine.save(vault: vault, expectedRevision: 1)

		await engine.handleLock()

		XCTAssertTrue(engine.status.hasPendingSync, "기기가 잠겨도 대기 내용은 유지되어야 합니다.")
		XCTAssertNotNil(engine.status.pendingVault)
	}

	// 13. 세션 만료(401) 후 동일 계정 재로그인: 대기 내용 병합 업로드
	func test_sessionExpired401_reloginSameAccount_mergesAndUploads() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.notConnectedToInternet)
		let engine = PendingSyncEngine(remoteService: remoteService, currentEmail: "current_user@example.com")
		let vault = makeSampleVault(itemCount: 2)
		_ = try await engine.save(vault: vault, expectedRevision: 1)

		await engine.handleSessionExpired()
		XCTAssertTrue(engine.status.hasPendingSync)

		// 동일 계정 재로그인 성공
		remoteService.uploadError = nil
		remoteService.nextRevisionToReturn = 3
		let serverVault = makeSampleVault(itemCount: 2)
		let merged = try await engine.handleRelogin(email: "current_user@example.com", serverVault: serverVault, serverRevision: 2)

		XCTAssertNotNil(merged)
		XCTAssertFalse(engine.status.hasPendingSync)
	}

	// 14. 세션 만료(401) 후 다른 계정 로그인 성공: 대기 내용 폐기
	func test_sessionExpired401_loginDifferentAccount_discardsPendingChanges() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.notConnectedToInternet)
		let engine = PendingSyncEngine(remoteService: remoteService, currentEmail: "current_user@example.com")
		let vault = makeSampleVault()
		_ = try await engine.save(vault: vault, expectedRevision: 1)

		await engine.handleSessionExpired()

		// 다른 계정으로 로그인 성공
		let newServerVault = makeSampleVault(itemCount: 5)
		_ = try await engine.handleRelogin(email: "different_user@example.com", serverVault: newServerVault, serverRevision: 10)

		XCTAssertFalse(engine.status.hasPendingSync, "다른 계정 로그인 성공 시 대기 내용은 폐기되어야 합니다.")
		XCTAssertNil(engine.status.pendingVault)
	}

	// 15. 대기 중 로그아웃 확인 표준 문구 검증
	func test_logoutConfirmationDialog_standardStrings() {
		XCTAssertEqual(PendingSyncStrings.logoutDialogTitle, "동기화되지 않은 변경 사항")
		XCTAssertEqual(PendingSyncStrings.logoutDialogMessage, "로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다.")
		XCTAssertEqual(PendingSyncStrings.logoutConfirmButton, "로그아웃")
		XCTAssertEqual(PendingSyncStrings.logoutCancelButton, "취소")
	}

	// 16. 잠금 해제 시 원격 조회 일시 실패해도 오프라인 잠금 해제 허용
	func test_offlineUnlock_transientFetchFailure_allowsUnlockAndRetainsPendingVault() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.notConnectedToInternet)
		remoteService.fetchError = URLError(.notConnectedToInternet)
		let engine = PendingSyncEngine(remoteService: remoteService)

		let initialVault = makeSampleVault(itemCount: 1)
		_ = try await engine.save(vault: initialVault, expectedRevision: 1)
		XCTAssertTrue(engine.status.hasPendingSync)

		let localFallback = makeSampleVault(itemCount: 2)
		let unlocked = try await engine.triggerUnlock(serverVault: localFallback, serverRevision: 1)

		XCTAssertNotNil(unlocked)
		XCTAssertTrue(engine.status.hasPendingSync, "네트워크 장애 시 잠금은 해제되되 대기 내용은 보존되어야 합니다.")
	}

	// 17. 대기 변경사항 수동 폐기 검증 (KDF 변경 등 연동용)
	func test_discardPendingChanges_resetsStatus() async throws {
		let remoteService = MockRemoteVaultSyncService()
		remoteService.uploadError = URLError(.timedOut)
		let engine = PendingSyncEngine(remoteService: remoteService)

		_ = try await engine.save(vault: makeSampleVault(), expectedRevision: 1)
		XCTAssertTrue(engine.status.hasPendingSync)

		engine.discardPendingChanges()
		XCTAssertFalse(engine.status.hasPendingSync)
		XCTAssertNil(engine.status.pendingVault)
	}

	// 18. 대기 변경사항이 없을 때도 되돌림 검증(Rollback Detection) 항상 수행
	func test_unlockWithoutPending_validatesRevisionRollback() async {
		let remoteService = MockRemoteVaultSyncService()
		let engine = PendingSyncEngine(remoteService: remoteService)

		// baseRevision이 5인 상태에서 서버 revision이 3으로 역행 시 예외 발생
		_ = try? await engine.save(vault: makeSampleVault(), expectedRevision: 4)
		// 성공 시 baseRevision = 5

		remoteService.nextRevisionToReturn = 3
		remoteService.revisionToFetch = 3
		do {
			_ = try await engine.triggerUnlock(serverVault: makeSampleVault(), serverRevision: 3)
			XCTFail("서버 revision이 역행하면 되돌림 감지 예외를 던져야 합니다.")
		} catch let error as VaultSyncError {
			XCTAssertEqual(error, .revisionRollbackDetected(lastSeen: 4, server: 3))
		} catch {
			XCTFail("예상치 못한 에러: \(error)")
		}
	}
}
