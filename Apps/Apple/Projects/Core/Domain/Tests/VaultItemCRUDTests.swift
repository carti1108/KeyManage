import Foundation
import Testing
@testable import KeyManageDomain

// MARK: - Fixture Helpers

private struct TestFixture {
	let masterPassword: String
	let email: String
	let sessionToken: String
	let salt: Data
	let iterations: Int
	let revision: Int
	let encryptedVault: EncryptedVaultPayload
	let decryptedVault: VaultData
}

private let sampleItemId = "550e8400-e29b-41d4-a716-446655440000"

private func makeSampleItem(
	id: String = sampleItemId,
	serviceName: String = "GitHub",
	username: String = "octocat",
	password: String = "SamplePassword456!",
	url: String? = "https://github.com",
	notes: String? = "Development account",
	createdAt: String = "2026-11-02T09:00:00Z",
	updatedAt: String = "2026-11-02T10:00:00Z"
) -> VaultItem {
	VaultItem(
		id: id,
		serviceName: serviceName,
		username: username,
		password: password,
		url: url,
		notes: notes,
		createdAt: createdAt,
		updatedAt: updatedAt
	)
}

private func makeFixture(
	isReadOnly: Bool = false,
	items: [VaultItem] = [makeSampleItem()],
	iterations: Int = PBKDF2KeyDeriver.defaultIterations
) throws -> TestFixture {
	let masterPassword = "TestMasterPassword123!"
	let email = "user@keymanage.local"
	let sessionToken = "session_token_crud_test_12345"
	let salt = Data([
		0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
		0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F
	])
	let revision = 1

	let normalized = try PasswordPreprocessor.preprocess(masterPassword)
	let masterKey = try PBKDF2KeyDeriver.deriveMasterKey(
		password: normalized,
		salt: salt,
		iterations: iterations
	)
	let encryptionKey = try HKDFKeySeparator.deriveEncryptionKey(masterKey: masterKey)

	let testVault = VaultData(
		version: isReadOnly ? VaultData.currentVersion + 1 : 1,
		updatedAt: "2026-11-02T10:00:00Z",
		items: items,
		isReadOnly: isReadOnly
	)
	let plaintext = try JSONEncoder().encode(testVault)
	let encryptedVault = try VaultCipher.encrypt(plaintext: plaintext, key: encryptionKey)

	return TestFixture(
		masterPassword: masterPassword,
		email: email,
		sessionToken: sessionToken,
		salt: salt,
		iterations: iterations,
		revision: revision,
		encryptedVault: encryptedVault,
		decryptedVault: testVault
	)
}

private func createUnlockedManager(
	isReadOnly: Bool = false,
	initialItems: [VaultItem]? = nil,
	now: Date = Date()
) throws -> (manager: VaultLockManager, fixture: TestFixture) {
	let items = initialItems ?? [makeSampleItem()]
	let fixture = try makeFixture(isReadOnly: isReadOnly, items: items)
	let manager = VaultLockManager()
	manager.setupSession(
		email: fixture.email,
		sessionToken: fixture.sessionToken,
		salt: fixture.salt,
		iterations: fixture.iterations,
		encryptedVault: fixture.encryptedVault,
		isBiometricsEnabled: false,
		biometryStateHash: nil,
		revision: fixture.revision
	)
	try manager.unlockWithMasterPassword(password: fixture.masterPassword, now: now)
	return (manager, fixture)
}

// MARK: - VaultItemCRUDTests Suite

@Suite("VaultItemCRUDTests - VLT-001 볼트 아이템 추가, 수정, 삭제(CRUD) 도메인 단위 테스트")
struct VaultItemCRUDTests {

	// MARK: - 1. 추가(Add) 테스트 시나리오

	@Test("추가 성공: 유효한 아이템 추가 시 decryptedVault에 반영되고 반환된 VaultItem 및 암호문 갱신 확인")
	func addVaultItem_success() throws {
		let (manager, _) = try createUnlockedManager(initialItems: [])
		let fixedDate = try VaultDateHelper.validateAndParse("2026-11-02T10:30:00Z")
		let formattedDate = VaultDateHelper.format(fixedDate)
		let originalEncrypted = manager.encryptedVault

		let added = try manager.addVaultItem(
			serviceName: "Google Workspace",
			username: "admin@company.com",
			password: "SecurePassword987#",
			url: "https://workspace.google.com",
			notes: "Primary company login",
			now: fixedDate
		)

		// 반환된 아이템 검증
		#expect(!added.id.isEmpty)
		#expect(added.serviceName == "Google Workspace")
		#expect(added.username == "admin@company.com")
		#expect(added.password == "SecurePassword987#")
		#expect(added.url == "https://workspace.google.com")
		#expect(added.notes == "Primary company login")
		#expect(added.createdAt == formattedDate)
		#expect(added.updatedAt == formattedDate)
		#expect(added.deletedAt == nil)
		#expect(!added.isDeleted)

		// decryptedVault 검증
		let items = try #require(manager.decryptedVault?.items)
		#expect(items.count == 1)
		#expect(items.first?.id == added.id)
		#expect(items.first?.serviceName == "Google Workspace")

		// 볼트 재암호화 검증 (암호문 갱신 확인)
		let newEncrypted = try #require(manager.encryptedVault)
		#expect(newEncrypted.ciphertext != originalEncrypted?.ciphertext)
	}

	@Test("추가 성공: 서비스 이름 앞뒤 공백이 정상적으로 트리밍되는지 확인")
	func addVaultItem_trimsServiceNameWhitespace() throws {
		let (manager, _) = try createUnlockedManager(initialItems: [])

		let added = try manager.addVaultItem(
			serviceName: "   Notion Workspace \t\n ",
			username: "writer@notion.so",
			password: "Pass",
			url: nil,
			notes: nil
		)

		#expect(added.serviceName == "Notion Workspace")
		let itemInVault = try #require(manager.decryptedVault?.items.first)
		#expect(itemInVault.serviceName == "Notion Workspace")
	}

	@Test("추가 성공: url 및 notes가 빈 문자열이면 nil로 정규화되는지 확인")
	func addVaultItem_normalizesEmptyOptionalFieldsToNil() throws {
		let (manager, _) = try createUnlockedManager(initialItems: [])

		let added = try manager.addVaultItem(
			serviceName: "Amazon AWS",
			username: "aws-admin",
			password: "AwsPassword123!",
			url: "",
			notes: ""
		)

		#expect(added.url == nil)
		#expect(added.notes == nil)
		let itemInVault = try #require(manager.decryptedVault?.items.first)
		#expect(itemInVault.url == nil)
		#expect(itemInVault.notes == nil)
	}

	@Test("추가 거절: 서비스명이 빈 문자열 또는 공백만 있는 경우 emptyServiceName 예외 발생 및 볼트 항목 유지")
	func addVaultItem_emptyServiceName_throwsEmptyServiceName() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(initialItems: [initial])

		// 1) 빈 문자열
		#expect(throws: VaultItemError.emptyServiceName) {
			try manager.addVaultItem(serviceName: "")
		}

		// 2) 스페이스 및 공백문자만 포함
		#expect(throws: VaultItemError.emptyServiceName) {
			try manager.addVaultItem(serviceName: "   \t\n  ")
		}

		// 볼트 항목 수 및 내용 유지 확인
		let items = try #require(manager.decryptedVault?.items)
		#expect(items.count == 1)
		#expect(items.first?.id == initial.id)
	}

	@Test("추가 거절: 볼트 잠금 상태(isLocked == true)에서 추가 시 locked 예외 발생")
	func addVaultItem_whenLocked_throwsLocked() throws {
		let (manager, _) = try createUnlockedManager()
		manager.lock()
		#expect(manager.isLocked == true)

		#expect(throws: VaultItemError.locked) {
			try manager.addVaultItem(
				serviceName: "GitHub",
				username: "octocat",
				password: "SamplePassword"
			)
		}
	}

	@Test("추가 거절: 상위 버전 읽기 전용 볼트(isReadOnly == true)에서 추가 시 vaultIsReadOnly 예외 발생")
	func addVaultItem_whenReadOnly_throwsVaultIsReadOnly() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(isReadOnly: true, initialItems: [initial])

		#expect(throws: VaultItemError.vaultIsReadOnly) {
			try manager.addVaultItem(
				serviceName: "Slack",
				username: "user@slack.com",
				password: "Pass"
			)
		}

		// 볼트 항목 변경 없음 검증
		let items = try #require(manager.decryptedVault?.items)
		#expect(items.count == 1)
		#expect(items.first?.id == initial.id)
	}

	@Test("추가 거절: 5MB 초과 페이로드 추가 시 vaultTooLarge 예외 발생")
	func addVaultItem_exceeding5MB_throwsVaultTooLarge() throws {
		let (manager, _) = try createUnlockedManager(initialItems: [])
		let hugeNotes = String(repeating: "A", count: BackupFileFormat.maxPayloadBytes + 10)

		#expect(throws: VaultItemError.vaultTooLarge) {
			try manager.addVaultItem(
				serviceName: "HeavyItem",
				username: "user",
				password: "password",
				url: nil,
				notes: hugeNotes
			)
		}

		#expect(manager.decryptedVault?.items.isEmpty == true)
	}

	// MARK: - 2. 수정(Update) 테스트 시나리오

	@Test("수정 성공: 기존 아이템 정보 변경 시 updatedAt 갱신 및 볼트 재암호화 검증")
	func updateVaultItem_success() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(initialItems: [initial])
		let updateDate = try VaultDateHelper.validateAndParse("2026-11-02T11:00:00Z")
		let originalEncrypted = manager.encryptedVault

		var modifiedItem = initial
		modifiedItem.serviceName = "GitHub Enterprise"
		modifiedItem.username = "enterprise_octocat"
		modifiedItem.password = "BrandNewPassword789!"
		modifiedItem.url = "https://ghe.mycompany.com"
		modifiedItem.notes = "Updated enterprise notes"

		try manager.updateVaultItem(item: modifiedItem, now: updateDate)

		let items = try #require(manager.decryptedVault?.items)
		#expect(items.count == 1)
		let saved = try #require(items.first)
		#expect(saved.id == initial.id)
		#expect(saved.serviceName == "GitHub Enterprise")
		#expect(saved.username == "enterprise_octocat")
		#expect(saved.password == "BrandNewPassword789!")
		#expect(saved.url == "https://ghe.mycompany.com")
		#expect(saved.notes == "Updated enterprise notes")
		#expect(saved.createdAt == initial.createdAt)
		#expect(saved.updatedAt == VaultDateHelper.format(updateDate))

		// 볼트 재암호화 확인
		let newEncrypted = try #require(manager.encryptedVault)
		#expect(newEncrypted.ciphertext != originalEncrypted?.ciphertext)
	}

	@Test("수정 성공: 수정 시 서비스명 공백 트리밍 및 url/notes 빈 문자열 nil 정규화")
	func updateVaultItem_trimsAndNormalizesFields() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(initialItems: [initial])

		var modifiedItem = initial
		modifiedItem.serviceName = "  GitHub Personal   "
		modifiedItem.url = ""
		modifiedItem.notes = ""

		try manager.updateVaultItem(item: modifiedItem)

		let saved = try #require(manager.decryptedVault?.items.first)
		#expect(saved.serviceName == "GitHub Personal")
		#expect(saved.url == nil)
		#expect(saved.notes == nil)
	}

	@Test("수정 거절: 수정 시 서비스명이 공백이면 emptyServiceName 예외 발생 및 볼트 유지")
	func updateVaultItem_emptyServiceName_throwsEmptyServiceName() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(initialItems: [initial])

		var invalidItem = initial
		invalidItem.serviceName = "   \n\t "

		#expect(throws: VaultItemError.emptyServiceName) {
			try manager.updateVaultItem(item: invalidItem)
		}

		// 기존 항목 유지 검증
		let saved = try #require(manager.decryptedVault?.items.first)
		#expect(saved.serviceName == initial.serviceName)
	}

	@Test("수정 거절: 존재하지 않는 id 수정 시 itemNotFound 예외 발생")
	func updateVaultItem_nonExistentId_throwsItemNotFound() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(initialItems: [initial])

		var nonExistentItem = makeSampleItem(id: "non-existent-uuid-9999")
		nonExistentItem.serviceName = "Non-existent Service"

		#expect(throws: VaultItemError.itemNotFound) {
			try manager.updateVaultItem(item: nonExistentItem)
		}

		let items = try #require(manager.decryptedVault?.items)
		#expect(items.count == 1)
		#expect(items.first?.id == initial.id)
	}

	@Test("수정 거절: 볼트 잠금 상태(isLocked == true)에서 수정 시 locked 예외 발생")
	func updateVaultItem_whenLocked_throwsLocked() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(initialItems: [initial])
		manager.lock()

		var updated = initial
		updated.serviceName = "Updated"

		#expect(throws: VaultItemError.locked) {
			try manager.updateVaultItem(item: updated)
		}
	}

	@Test("수정 거절: 읽기 전용 볼트(isReadOnly == true)에서 수정 시 vaultIsReadOnly 예외 발생")
	func updateVaultItem_whenReadOnly_throwsVaultIsReadOnly() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(isReadOnly: true, initialItems: [initial])

		var updated = initial
		updated.serviceName = "Updated"

		#expect(throws: VaultItemError.vaultIsReadOnly) {
			try manager.updateVaultItem(item: updated)
		}
	}

	// MARK: - 3. 삭제(Delete) 테스트 시나리오

	@Test("삭제 성공: 삭제 시 툼스톤(deletedAt 설정 및 민감 필드 소거) 처리 및 볼트 재암호화 검증")
	func deleteVaultItem_success_createsTombstone() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(initialItems: [initial])
		let deleteDate = try VaultDateHelper.validateAndParse("2026-11-02T12:00:00Z")
		let originalEncrypted = manager.encryptedVault

		try manager.deleteVaultItem(id: initial.id, now: deleteDate)

		let items = try #require(manager.decryptedVault?.items)
		#expect(items.count == 1)
		let tombstone = try #require(items.first)

		// 툼스톤 검증 (VLT-001 규칙 6: id, 생성/수정/삭제시각만 보존, 민감 필드 모두 소거)
		#expect(tombstone.id == initial.id)
		#expect(tombstone.isDeleted == true)
		#expect(tombstone.deletedAt == VaultDateHelper.format(deleteDate))
		#expect(tombstone.createdAt == initial.createdAt)
		#expect(tombstone.updatedAt == VaultDateHelper.format(deleteDate))

		// 민감 필드 소거 확인
		#expect(tombstone.serviceName.isEmpty)
		#expect(tombstone.username.isEmpty)
		#expect(tombstone.password.isEmpty)
		#expect(tombstone.url == nil)
		#expect(tombstone.notes == nil)
		#expect(tombstone.unknownFields.isEmpty)

		// 볼트 재암호화 검증
		let newEncrypted = try #require(manager.encryptedVault)
		#expect(newEncrypted.ciphertext != originalEncrypted?.ciphertext)
	}

	@Test("삭제 거절: 존재하지 않는 id 삭제 시 itemNotFound 예외 발생")
	func deleteVaultItem_nonExistentId_throwsItemNotFound() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(initialItems: [initial])

		#expect(throws: VaultItemError.itemNotFound) {
			try manager.deleteVaultItem(id: "non-existent-uuid-12345")
		}

		let items = try #require(manager.decryptedVault?.items)
		#expect(items.count == 1)
		#expect(items.first?.isDeleted == false)
	}

	@Test("삭제 거절: 볼트 잠금 상태(isLocked == true)에서 삭제 시 locked 예외 발생")
	func deleteVaultItem_whenLocked_throwsLocked() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(initialItems: [initial])
		manager.lock()

		#expect(throws: VaultItemError.locked) {
			try manager.deleteVaultItem(id: initial.id)
		}
	}

	@Test("삭제 거절: 읽기 전용 볼트(isReadOnly == true)에서 삭제 시 vaultIsReadOnly 예외 발생")
	func deleteVaultItem_whenReadOnly_throwsVaultIsReadOnly() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(isReadOnly: true, initialItems: [initial])

		#expect(throws: VaultItemError.vaultIsReadOnly) {
			try manager.deleteVaultItem(id: initial.id)
		}

		let items = try #require(manager.decryptedVault?.items)
		#expect(items.count == 1)
		#expect(items.first?.isDeleted == false)
	}

	@Test("수정 및 삭제 거절: 이미 삭제된 툼스톤 항목(isDeleted == true) 변조 및 재삭제 시 itemNotFound 예외 발생")
	func crud_alreadyDeletedTombstone_throwsItemNotFound() throws {
		let initial = makeSampleItem()
		let (manager, _) = try createUnlockedManager(initialItems: [initial])

		// 먼저 삭제 수행
		try manager.deleteVaultItem(id: initial.id)
		let tombstone = try #require(manager.decryptedVault?.items.first)
		#expect(tombstone.isDeleted == true)

		// 1) 툼스톤 재삭제 시도 -> itemNotFound
		#expect(throws: VaultItemError.itemNotFound) {
			try manager.deleteVaultItem(id: initial.id)
		}

		// 2) 툼스톤 수정 시도 -> itemNotFound
		var modified = tombstone
		modified.serviceName = "Resurrect"
		#expect(throws: VaultItemError.itemNotFound) {
			try manager.updateVaultItem(item: modified)
		}
	}

	// MARK: - 4. 정책 문구 일치 검증

	@Test("표준 에러 문구 검증: VaultItemError의 errorDescription이 VLT-001 정책 문구와 100% 일치하는지 확인")
	func vaultItemError_errorDescriptions_matchPolicy() {
		#expect(
			VaultItemError.emptyServiceName.errorDescription
			== "서비스 이름을 입력해 주세요."
		)
		#expect(
			VaultItemError.vaultIsReadOnly.errorDescription
			== "새 버전 앱에서 저장한 볼트입니다. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다."
		)
		#expect(
			VaultItemError.vaultTooLarge.errorDescription
			== "볼트 허용 용량(5MB)을 초과했거나 데이터 규격이 올바르지 않습니다."
		)
		#expect(
			VaultItemError.itemNotFound.errorDescription
			== "항목을 찾을 수 없습니다."
		)
		#expect(
			VaultItemError.locked.errorDescription
			== "볼트가 잠겨 있습니다."
		)
	}
}
