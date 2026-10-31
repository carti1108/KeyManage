import Foundation
import XCTest
@testable import KeyManageDomain

final class VaultModuleTests: XCTestCase {

	private let isoFormatter: ISO8601DateFormatter = {
		let formatter = ISO8601DateFormatter()
		formatter.formatOptions = [.withInternetDateTime]
		return formatter
	}()

	private func date(from string: String) -> Date {
		guard let date = isoFormatter.date(from: string) else {
			fatalError("Invalid ISO8601 date string in test: \(string)")
		}
		return date
	}

	// MARK: - 1. 모르는 필드 보존 검증 (Preserve Unknown Fields)

	func testPreserveUnknownFieldsInVaultAndItems() throws {
		let jsonString = """
		{
			"version": 1,
			"updatedAt": "2026-10-27T11:20:00Z",
			"customTopField": "topLevelExtra",
			"futureConfig": { "enabled": true },
			"items": [
				{
					"id": "550e8400-e29b-41d4-a716-446655440000",
					"serviceName": "GitHub",
					"username": "octocat",
					"password": "Password123!",
					"createdAt": "2026-10-27T10:00:00Z",
					"updatedAt": "2026-10-27T11:20:00Z",
					"customTag": "developer",
					"securityScore": 98
				}
			]
		}
		"""
		let data = try XCTUnwrap(jsonString.data(using: .utf8))

		// 디코딩 검증
		let vault = try VaultData.decode(from: data)
		XCTAssertEqual(vault.unknownFields["customTopField"], .string("topLevelExtra"))
		XCTAssertEqual(
			vault.unknownFields["futureConfig"],
			.dictionary(["enabled": .bool(true)])
		)

		let item = try XCTUnwrap(vault.items.first)
		XCTAssertEqual(item.unknownFields["customTag"], .string("developer"))
		XCTAssertEqual(item.unknownFields["securityScore"], .int(98))

		// 재인코딩 검증: 미지 필드가 인코딩 결과 JSON에 그대로 유지되는지 확인
		let encodedData = try vault.encode()
		let jsonObject = try XCTUnwrap(
			JSONSerialization.jsonObject(with: encodedData) as? [String: Any]
		)

		XCTAssertEqual(jsonObject["customTopField"] as? String, "topLevelExtra")
		let futureConfig = jsonObject["futureConfig"] as? [String: Any]
		XCTAssertEqual(futureConfig?["enabled"] as? Bool, true)

		let itemsArray = try XCTUnwrap(jsonObject["items"] as? [[String: Any]])
		let firstItem = try XCTUnwrap(itemsArray.first)
		XCTAssertEqual(firstItem["customTag"] as? String, "developer")
		XCTAssertEqual(firstItem["securityScore"] as? Int, 98)
	}

	// MARK: - 2. 스키마 버전 호환성 검증 (Schema Version Compatibility)

	func testHigherSchemaVersionOpensAsReadOnly() throws {
		let futureVersionJson = """
		{
			"version": 2,
			"updatedAt": "2026-10-27T11:20:00Z",
			"items": []
		}
		"""
		let data = try XCTUnwrap(futureVersionJson.data(using: .utf8))

		let vault = try VaultData.decode(from: data)
		XCTAssertEqual(vault.version, 2)
		XCTAssertTrue(vault.isReadOnly, "앱이 아는 버전(1)보다 높은 버전(2)은 읽기 전용이어야 합니다.")
	}

	func testInvalidSchemaVersionRejectsDecoding() throws {
		let invalidVersions = [0, -1, -99]
		for ver in invalidVersions {
			let json = """
			{
				"version": \(ver),
				"updatedAt": "2026-10-27T11:20:00Z",
				"items": []
			}
			"""
			let data = try XCTUnwrap(json.data(using: .utf8))

			XCTAssertThrowsError(try VaultData.decode(from: data)) { error in
				guard case VaultError.invalidVaultFormat = error else {
					XCTFail("version \(ver)에 대해 invalidVaultFormat 에러가 발생해야 합니다. 실제 에러: \(error)")
					return
				}
			}
		}
	}

	// MARK: - 3. 시각 형식 엄격 검증 (Strict ISO 8601 UTC Validation)

	func testStrictIso8601UtcTimeFormatValidation() throws {
		// UTC 오프셋(Z 또는 +09:00 등)이 없는 형식은 거절되어야 함
		let invalidTimeJsons = [
			"""
			{
				"version": 1,
				"updatedAt": "2026-10-27 11:20:00",
				"items": []
			}
			""",
			"""
			{
				"version": 1,
				"updatedAt": "2026-10-27T11:20:00",
				"items": []
			}
			""",
			"""
			{
				"version": 1,
				"updatedAt": "2026-10-27T11:20:00Z",
				"items": [
					{
						"id": "550e8400-e29b-41d4-a716-446655440000",
						"serviceName": "GitHub",
						"username": "octocat",
						"password": "pwd",
						"createdAt": "2026-10-27 10:00:00",
						"updatedAt": "2026-10-27T11:20:00Z"
					}
				]
			}
			"""
		]

		for json in invalidTimeJsons {
			let data = try XCTUnwrap(json.data(using: .utf8))
			XCTAssertThrowsError(try VaultData.decode(from: data)) { error in
				guard case VaultError.invalidVaultFormat = error else {
					XCTFail("UTC 오프셋 없는 시각에 대해 invalidVaultFormat 에러가 발생해야 합니다. 실제: \(error)")
					return
				}
			}
		}

		// 유효한 UTC 시각(Z 및 +09:00 오프셋)은 통과해야 함
		let validTimeJson = """
		{
			"version": 1,
			"updatedAt": "2026-10-27T11:20:00Z",
			"items": [
				{
					"id": "550e8400-e29b-41d4-a716-446655440000",
					"serviceName": "GitHub",
					"username": "octocat",
					"password": "pwd",
					"createdAt": "2026-10-27T20:00:00+09:00",
					"updatedAt": "2026-10-27T11:20:00Z"
				}
			]
		}
		"""
		let validData = try XCTUnwrap(validTimeJson.data(using: .utf8))
		XCTAssertNoThrow(try VaultData.decode(from: validData))
	}

	// MARK: - 4. null 선택 필드 처리 (Null Optional Fields Handling)

	func testNullOptionalFieldsDecodeAsNil() throws {
		let jsonString = """
		{
			"version": 1,
			"updatedAt": "2026-10-27T11:20:00Z",
			"items": [
				{
					"id": "550e8400-e29b-41d4-a716-446655440000",
					"serviceName": "GitHub",
					"username": "octocat",
					"password": "Password123!",
					"url": null,
					"notes": null,
					"createdAt": "2026-10-27T10:00:00Z",
					"updatedAt": "2026-10-27T11:20:00Z",
					"deletedAt": null
				}
			]
		}
		"""
		let data = try XCTUnwrap(jsonString.data(using: .utf8))
		let vault = try VaultData.decode(from: data)
		let item = try XCTUnwrap(vault.items.first)

		XCTAssertNil(item.url)
		XCTAssertNil(item.notes)
		XCTAssertNil(item.deletedAt)
		XCTAssertFalse(item.isDeleted)
	}

	// MARK: - 5. 톰스톤 삭제 검증 (Tombstone Deletion & Scrubbing)

	func testTombstoneDeletionScrubsContentFields() throws {
		var vault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T11:00:00Z",
			items: [
				VaultItem(
					id: "item-tombstone-1",
					serviceName: "GitHub",
					username: "octocat@github.com",
					password: "superSecretPassword",
					url: "https://github.com",
					notes: "private developer account",
					createdAt: "2026-10-27T10:00:00Z",
					updatedAt: "2026-10-27T11:00:00Z",
					deletedAt: nil,
					unknownFields: ["legacyTag": .string("oldData")]
				)
			]
		)

		let deleteDate = date(from: "2026-10-27T12:00:00Z")
		try vault.deleteItem(id: "item-tombstone-1", now: deleteDate)

		let deletedItem = try XCTUnwrap(vault.items.first(where: { $0.id == "item-tombstone-1" }))

		// 톰스톤 소거 규격: id, createdAt, updatedAt, deletedAt만 남기고 내용은 완전 소거
		XCTAssertTrue(deletedItem.isDeleted)
		XCTAssertEqual(deletedItem.deletedAt, "2026-10-27T12:00:00Z")
		XCTAssertEqual(deletedItem.serviceName, "")
		XCTAssertEqual(deletedItem.username, "")
		XCTAssertEqual(deletedItem.password, "")
		XCTAssertNil(deletedItem.url)
		XCTAssertNil(deletedItem.notes)
		XCTAssertTrue(deletedItem.unknownFields.isEmpty, "삭제 시 미지의 필드도 소거되어야 합니다.")
		XCTAssertEqual(deletedItem.id, "item-tombstone-1")
		XCTAssertEqual(deletedItem.createdAt, "2026-10-27T10:00:00Z")
	}

	// MARK: - 6. 시각 단조 증가 검증 (Monotonic Timestamp Increase)

	func testMonotonicTimestampIncreaseOnUpdateAndDelete() throws {
		var vault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T12:00:00Z",
			items: [
				VaultItem(
					id: "item-mono-1",
					serviceName: "Google",
					username: "user@gmail.com",
					password: "pwd",
					createdAt: "2026-10-27T10:00:00Z",
					updatedAt: "2026-10-27T12:00:00Z"
				)
			]
		)

		// 케이스 1: 현재 시각이 이전 시각보다 과거인 경우 -> previousUpdatedAt + 1초로 단조 증가
		let pastDate = date(from: "2026-10-27T11:30:00Z")
		var updatedItem = vault.items[0]
		updatedItem.password = "newPwd1"
		try vault.updateItem(updatedItem, now: pastDate)

		let itemAfterPast = try XCTUnwrap(vault.items.first(where: { $0.id == "item-mono-1" }))
		XCTAssertEqual(itemAfterPast.updatedAt, "2026-10-27T12:00:01Z", "과거 시각 전달 시 previous + 1초가 되어야 합니다.")

		// 케이스 2: 현재 시각이 이전 updatedAt과 완전히 같은 경우 -> previousUpdatedAt + 1초로 증가
		let sameDate = date(from: "2026-10-27T12:00:01Z")
		var updatedItem2 = itemAfterPast
		updatedItem2.password = "newPwd2"
		try vault.updateItem(updatedItem2, now: sameDate)

		let itemAfterSame = try XCTUnwrap(vault.items.first(where: { $0.id == "item-mono-1" }))
		XCTAssertEqual(itemAfterSame.updatedAt, "2026-10-27T12:00:02Z", "동일 초 내 연속 수정 시 1초 단조 증가해야 합니다.")

		// 케이스 3: 현재 시각이 미래인 경우 -> max(now, previous + 1초)에 따라 now가 반영
		let futureDate = date(from: "2026-10-27T12:05:00Z")
		try vault.deleteItem(id: "item-mono-1", now: futureDate)

		let deletedItem = try XCTUnwrap(vault.items.first(where: { $0.id == "item-mono-1" }))
		XCTAssertEqual(deletedItem.updatedAt, "2026-10-27T12:05:00Z")
		XCTAssertEqual(deletedItem.deletedAt, "2026-10-27T12:05:00Z")
	}

	// MARK: - 7. LWW 항목 병합 검증 (Last-Write-Wins Merge)

	func testLastWriteWinsMergeResolution() throws {
		let merger = VaultMerger()

		let localItemNewer = VaultItem(
			id: "item-1",
			serviceName: "LocalService",
			username: "localUser",
			password: "localPassword",
			createdAt: "2026-10-27T10:00:00Z",
			updatedAt: "2026-10-27T12:10:00Z"
		)
		let serverItemOlder = VaultItem(
			id: "item-1",
			serviceName: "ServerService",
			username: "serverUser",
			password: "serverPassword",
			createdAt: "2026-10-27T10:00:00Z",
			updatedAt: "2026-10-27T12:00:00Z"
		)

		let localVault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T12:10:00Z",
			items: [localItemNewer]
		)
		let serverVault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T12:00:00Z",
			items: [serverItemOlder]
		)

		// 1) 로컬이 최신인 경우 로컬 항목이 선택되어야 함
		let merged1 = try merger.merge(local: localVault, server: serverVault)
		let resultItem1 = try XCTUnwrap(merged1.items.first(where: { $0.id == "item-1" }))
		XCTAssertEqual(resultItem1.serviceName, "LocalService")
		XCTAssertEqual(resultItem1.password, "localPassword")

		// 2) 서버가 최신인 경우 서버 항목이 선택되어야 함
		var serverItemNewer = serverItemOlder
		serverItemNewer.updatedAt = "2026-10-27T12:20:00Z"
		serverItemNewer.serviceName = "ServerServiceNewer"
		let serverVaultNewer = VaultData(
			version: 1,
			updatedAt: "2026-10-27T12:20:00Z",
			items: [serverItemNewer]
		)

		let merged2 = try merger.merge(local: localVault, server: serverVaultNewer)
		let resultItem2 = try XCTUnwrap(merged2.items.first(where: { $0.id == "item-1" }))
		XCTAssertEqual(resultItem2.serviceName, "ServerServiceNewer")

		// 3) 삭제 시각(deletedAt)과 수정 시각(updatedAt) 비교: 삭제가 더 최신이면 삭제 상태가 유지되어야 함
		var localItemDeleted = localItemNewer
		localItemDeleted.deletedAt = "2026-10-27T12:30:00Z"
		localItemDeleted.updatedAt = "2026-10-27T12:30:00Z"
		localItemDeleted.serviceName = ""
		let localVaultDeleted = VaultData(
			version: 1,
			updatedAt: "2026-10-27T12:30:00Z",
			items: [localItemDeleted]
		)

		let merged3 = try merger.merge(local: localVaultDeleted, server: serverVaultNewer)
		let resultItem3 = try XCTUnwrap(merged3.items.first(where: { $0.id == "item-1" }))
		XCTAssertTrue(resultItem3.isDeleted, "더 늦은 삭제 시각을 가진 항목이 선택되어야 합니다.")
	}

	// MARK: - 8. 동일 시각 서버 우선 검증 (Tie-break: Server Wins)

	func testTieBreakChoosesServerItemWhenTimestampsAreEqual() throws {
		let merger = VaultMerger()

		let tieTimestamp = "2026-10-27T12:00:00Z"

		let localItem = VaultItem(
			id: "tie-item",
			serviceName: "LocalService",
			username: "localUser",
			password: "localPassword",
			createdAt: "2026-10-27T10:00:00Z",
			updatedAt: tieTimestamp
		)
		let serverItem = VaultItem(
			id: "tie-item",
			serviceName: "ServerService",
			username: "serverUser",
			password: "serverPassword",
			createdAt: "2026-10-27T10:00:00Z",
			updatedAt: tieTimestamp
		)

		let localVault = VaultData(
			version: 1,
			updatedAt: tieTimestamp,
			items: [localItem]
		)
		let serverVault = VaultData(
			version: 1,
			updatedAt: tieTimestamp,
			items: [serverItem]
		)

		let merged = try merger.merge(local: localVault, server: serverVault)
		let chosen = try XCTUnwrap(merged.items.first(where: { $0.id == "tie-item" }))

		// VLT-001 명세: "항목 시간이 같으면 서버 쪽 우선"
		XCTAssertEqual(chosen.serviceName, "ServerService", "시각이 같을 때 서버 항목이 선택되어야 합니다.")
		XCTAssertEqual(chosen.username, "serverUser")
		XCTAssertEqual(chosen.password, "serverPassword")
	}

	// MARK: - 9. 비대칭 항목 보존 검증 (Preserve Asymmetric Items)

	func testPreservesAsymmetricItemsFromBothSides() throws {
		let merger = VaultMerger()

		let localOnlyItem = VaultItem(
			id: "item-local-only",
			serviceName: "LocalApp",
			username: "localUser",
			password: "pwd1",
			createdAt: "2026-10-27T10:00:00Z",
			updatedAt: "2026-10-27T11:00:00Z"
		)
		let sharedItemLocal = VaultItem(
			id: "item-shared",
			serviceName: "SharedApp",
			username: "sharedUser",
			password: "pwdShared",
			createdAt: "2026-10-27T10:00:00Z",
			updatedAt: "2026-10-27T11:00:00Z"
		)
		let serverOnlyItem = VaultItem(
			id: "item-server-only",
			serviceName: "ServerApp",
			username: "serverUser",
			password: "pwd2",
			createdAt: "2026-10-27T10:00:00Z",
			updatedAt: "2026-10-27T11:00:00Z"
		)

		let localVault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T11:00:00Z",
			items: [localOnlyItem, sharedItemLocal]
		)
		let serverVault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T11:00:00Z",
			items: [sharedItemLocal, serverOnlyItem]
		)

		let merged = try merger.merge(local: localVault, server: serverVault)

		let ids = Set(merged.items.map(\.id))
		XCTAssertEqual(ids, ["item-local-only", "item-shared", "item-server-only"])
		XCTAssertEqual(merged.items.count, 3)
	}

	// MARK: - 10. 5MB 페이로드 제한 검증 (5MB Payload Limit)

	func testPayloadSizeValidationThreshold() throws {
		let validator = VaultSyncValidator()

		// 정확히 5,242,880바이트 -> 허용 (VLT-001: "이 값과 같으면 허용")
		XCTAssertNoThrow(try validator.validatePayloadSize(5_242_880))

		// 5,242,881바이트 -> 거절 (1바이트 초과)
		XCTAssertThrowsError(try validator.validatePayloadSize(5_242_881)) { error in
			guard case let VaultSyncError.payloadTooLarge(size) = error else {
				XCTFail("5MB 초과 시 payloadTooLarge 에러가 발생해야 합니다. 실제: \(error)")
				return
			}
			XCTAssertEqual(size, 5_242_881)
		}
	}

	// MARK: - 11. revision 되돌림 감지 검증 (Revision Rollback Detection)

	func testRevisionRollbackDetection() throws {
		let validator = VaultSyncValidator()

		// 정상 케이스: 서버 revision이 lastSeen 이상인 경우
		XCTAssertNoThrow(try validator.validateRevision(server: 10, lastSeen: 10))
		XCTAssertNoThrow(try validator.validateRevision(server: 11, lastSeen: 10))

		// 되돌림 감지: 서버 revision이 lastSeen보다 낮은 경우
		XCTAssertThrowsError(try validator.validateRevision(server: 9, lastSeen: 10)) { error in
			guard case let VaultSyncError.revisionRollbackDetected(lastSeen, server) = error else {
				XCTFail("서버 revision 역행 시 revisionRollbackDetected 에러가 발생해야 합니다. 실제: \(error)")
				return
			}
			XCTAssertEqual(lastSeen, 10)
			XCTAssertEqual(server, 9)
		}
	}

	// MARK: - 12. 409 충돌 재시도 루프 검증 (409 Conflict Retry Loop)

	private final class RetryAttemptTracker: @unchecked Sendable {
		private let lock = NSLock()
		private var _attempts: Int = 0

		var count: Int {
			lock.lock()
			defer { lock.unlock() }
			return _attempts
		}

		func record() -> Int {
			lock.lock()
			defer { lock.unlock() }
			_attempts += 1
			return _attempts
		}
	}

	func testConflictRetryLoopExceedsLimit() async throws {
		let coordinator = VaultSyncCoordinator()

		let localVault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T12:00:00Z",
			items: []
		)

		let tracker = RetryAttemptTracker()

		// 409 Conflict를 4번 연속(초기 1회 + 재시도 3회) 반환하는 mock
		do {
			_ = try await coordinator.syncWithRetry(
				local: localVault,
				lastSeenRevision: 5,
				saveOperation: { _, _ in
					_ = tracker.record()
					throw VaultSyncError.syncFailed("409 Conflict")
				},
				fetchOperation: {
					return (localVault, 6)
				}
			)
			XCTFail("재시도 한도 초과 시 conflictLimitExceeded 에러가 발생해야 합니다.")
		} catch {
			guard case let VaultSyncError.conflictLimitExceeded(recordedAttempts) = error else {
				XCTFail("conflictLimitExceeded 에러가 발생해야 합니다. 실제: \(error)")
				return
			}
			XCTAssertEqual(recordedAttempts, 3, "재시도 횟수는 3회여야 합니다.")
			XCTAssertEqual(tracker.count, 4, "초기 시도 1회 + 재시도 3회로 총 4회 실행되어야 합니다.")
		}
	}

	func testConflictRetrySucceedsOnThirdAttempt() async throws {
		let coordinator = VaultSyncCoordinator()

		let localVault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T12:00:00Z",
			items: [
				VaultItem(
					id: "item-1",
					serviceName: "LocalApp",
					username: "user",
					password: "pwd",
					createdAt: "2026-10-27T10:00:00Z",
					updatedAt: "2026-10-27T12:00:00Z"
				)
			]
		)

		let serverVault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T12:05:00Z",
			items: [
				VaultItem(
					id: "item-2",
					serviceName: "ServerApp",
					username: "user2",
					password: "pwd2",
					createdAt: "2026-10-27T10:00:00Z",
					updatedAt: "2026-10-27T12:05:00Z"
				)
			]
		)

		let tracker = RetryAttemptTracker()

		// 2회 충돌 후 3회째에 성공하는 시뮬레이션
		let result = try await coordinator.syncWithRetry(
			local: localVault,
			lastSeenRevision: 5,
			saveOperation: { _, expectedRevision in
				let current = tracker.record()
				if current < 3 {
					throw VaultSyncError.syncFailed("409 Conflict")
				}
				// 3번째 시도에서 성공
				return expectedRevision + 1
			},
			fetchOperation: {
				let current = tracker.count
				return (serverVault, 5 + current)
			}
		)

		XCTAssertEqual(tracker.count, 3, "2회 실패 후 3번째 시도에서 성공해야 합니다.")
		XCTAssertEqual(result.newRevision, 8)
		// 병합 결과에 두 아이템이 모두 포함되어 있는지 확인
		XCTAssertEqual(result.savedVault.items.count, 2)
	}

	// MARK: - 13. 보안 리뷰 결함 검증 (Security Review Fix Verification)

	/// [P1-1] 톰스톤 인코딩 시 소거 불완전 및 개인정보 노출 검증
	/// deletedAt != nil인 VaultItem을 JSON으로 인코딩했을 때 오직 id, createdAt, updatedAt, deletedAt 4개 키만 존재해야 함
	func testTombstoneEncodingContainsOnlyFourKeysAndNoSensitiveData() throws {
		let tombstoneItem = VaultItem(
			id: "item-tombstone-scrub-1",
			serviceName: "SensitiveServiceName",
			username: "sensitive-username@example.com",
			password: "SuperSecretPassword123!",
			url: "https://vault.example.com/login",
			notes: "Top secret notes that should not leak",
			createdAt: "2026-10-27T10:00:00Z",
			updatedAt: "2026-10-27T12:00:00Z",
			deletedAt: "2026-10-27T12:00:00Z",
			unknownFields: ["leakField": .string("leakValue")]
		)

		let encoder = JSONEncoder()
		let data = try encoder.encode(tombstoneItem)
		let jsonObject = try XCTUnwrap(
			JSONSerialization.jsonObject(with: data) as? [String: Any]
		)

		let expectedKeys: Set<String> = ["id", "createdAt", "updatedAt", "deletedAt"]
		XCTAssertEqual(
			Set(jsonObject.keys),
			expectedKeys,
			"톰스톤 인코딩 시 오직 id, createdAt, updatedAt, deletedAt 4개 키만 존재해야 합니다."
		)
		XCTAssertEqual(jsonObject["id"] as? String, "item-tombstone-scrub-1")
		XCTAssertEqual(jsonObject["createdAt"] as? String, "2026-10-27T10:00:00Z")
		XCTAssertEqual(jsonObject["updatedAt"] as? String, "2026-10-27T12:00:00Z")
		XCTAssertEqual(jsonObject["deletedAt"] as? String, "2026-10-27T12:00:00Z")

		XCTAssertNil(jsonObject["serviceName"], "톰스톤에 serviceName 키가 포함되어서는 안 됩니다.")
		XCTAssertNil(jsonObject["username"], "톰스톤에 username 키가 포함되어서는 안 됩니다.")
		XCTAssertNil(jsonObject["password"], "톰스톤에 password 키가 포함되어서는 안 됩니다.")
		XCTAssertNil(jsonObject["url"], "톰스톤에 url 키가 포함되어서는 안 됩니다.")
		XCTAssertNil(jsonObject["notes"], "톰스톤에 notes 키가 포함되어서는 안 됩니다.")
		XCTAssertNil(jsonObject["leakField"], "톰스톤에 unknownFields 키가 포함되어서는 안 됩니다.")
	}

	/// [P2-1] VaultSyncCoordinator의 읽기 전용(isReadOnly) 볼트 동기화 시도 방어 검증
	/// currentVault.isReadOnly == true인 경우 saveOperation 호출 전 즉시 VaultError.vaultIsReadOnly 에러를 던져야 함
	func testSyncCoordinatorThrowsWhenVaultIsReadOnly() async throws {
		let coordinator = VaultSyncCoordinator()

		// 1) 초기 local 볼트가 isReadOnly == true인 경우
		let readOnlyLocalVault = VaultData(
			version: 2,
			updatedAt: "2026-10-27T12:00:00Z",
			items: [],
			isReadOnly: true
		)

		let localSaveTracker = RetryAttemptTracker()
		do {
			_ = try await coordinator.syncWithRetry(
				local: readOnlyLocalVault,
				lastSeenRevision: 1,
				saveOperation: { _, _ in
					_ = localSaveTracker.record()
					return 2
				},
				fetchOperation: {
					return (readOnlyLocalVault, 2)
				}
			)
			XCTFail("읽기 전용 볼트는 saveOperation 호출 전 즉시 VaultError.vaultIsReadOnly를 던져야 합니다.")
		} catch let error as VaultError {
			XCTAssertEqual(error, .vaultIsReadOnly, "발생한 에러는 vaultIsReadOnly여야 합니다.")
			XCTAssertEqual(localSaveTracker.count, 0, "읽기 전용 볼트는 saveOperation을 호출하지 않아야 합니다.")
		} catch {
			XCTFail("VaultError.vaultIsReadOnly가 발생해야 합니다. 실제: \(error)")
		}

		// 2) 409 병합 결과 상위 버전 볼트로 갱신되어 isReadOnly == true가 된 경우
		let normalLocalVault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T12:00:00Z",
			items: []
		)
		let futureServerVault = VaultData(
			version: 2,
			updatedAt: "2026-10-27T12:05:00Z",
			items: [],
			isReadOnly: true
		)

		let conflictSaveTracker = RetryAttemptTracker()
		do {
			_ = try await coordinator.syncWithRetry(
				local: normalLocalVault,
				lastSeenRevision: 1,
				saveOperation: { _, _ in
					let attempt = conflictSaveTracker.record()
					if attempt == 1 {
						// 첫 번째 시도에서 409 반환
						throw VaultSyncError.syncFailed("409 Conflict")
					} else {
						// 병합 후 두 번째 시도는 호출되어서는 안 됨
						XCTFail("상위 버전 병합으로 isReadOnly가 된 경우 두 번째 saveOperation이 호출되면 안 됩니다.")
						return 2
					}
				},
				fetchOperation: {
					return (futureServerVault, 2)
				}
			)
			XCTFail("병합 결과가 isReadOnly 볼트인 경우 재시도 saveOperation 호출 전 VaultError.vaultIsReadOnly를 던져야 합니다.")
		} catch let error as VaultError {
			XCTAssertEqual(error, .vaultIsReadOnly, "발생한 에러는 vaultIsReadOnly여야 합니다.")
			XCTAssertEqual(conflictSaveTracker.count, 1, "초기 1회 시도 후 병합 결과가 read-only이므로 2번째 saveOperation은 호출되지 않아야 합니다.")
		} catch {
			XCTFail("VaultError.vaultIsReadOnly가 발생해야 합니다. 실제: \(error)")
		}
	}

	/// [P2-2] VaultSyncCoordinator 내 5MB 페이로드 한계 사전 검증 누락 검증
	/// 볼트 인코딩 크기가 5,242,880바이트를 초과하는 경우 saveOperation 호출 전 즉시 VaultSyncError.payloadTooLarge 에러를 던져야 함
	func testSyncCoordinatorThrowsWhenPayloadExceeds5MBBeforeSave() async throws {
		let coordinator = VaultSyncCoordinator()

		// 볼트 인코딩 크기가 5,242,880바이트를 초과하도록 페이로드 구성
		let largePayload = String(repeating: "A", count: 5_242_881)
		let largeItem = VaultItem(
			id: "large-item-1",
			serviceName: "BigService",
			username: "bigUser",
			password: "bigPassword",
			notes: largePayload,
			createdAt: "2026-10-27T10:00:00Z",
			updatedAt: "2026-10-27T12:00:00Z"
		)
		let largeVault = VaultData(
			version: 1,
			updatedAt: "2026-10-27T12:00:00Z",
			items: [largeItem]
		)

		let saveTracker = RetryAttemptTracker()
		do {
			_ = try await coordinator.syncWithRetry(
				local: largeVault,
				lastSeenRevision: 1,
				saveOperation: { _, _ in
					_ = saveTracker.record()
					return 2
				},
				fetchOperation: {
					return (largeVault, 2)
				}
			)
			XCTFail("5MB 초과 볼트는 saveOperation 호출 전에 VaultSyncError.payloadTooLarge를 던져야 합니다.")
		} catch let VaultSyncError.payloadTooLarge(size) {
			XCTAssertGreaterThan(size, 5_242_880, "에러에 보고된 크기는 5MB(5,242,880바이트) 초과여야 합니다.")
			XCTAssertEqual(saveTracker.count, 0, "5MB 초과 시 saveOperation이 호출되지 않아야 합니다.")
		} catch {
			XCTFail("VaultSyncError.payloadTooLarge 에러가 발생해야 합니다. 실제: \(error)")
		}
	}
}
