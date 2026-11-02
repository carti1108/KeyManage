import Foundation
import Testing
@testable import KeyManageDomain

// MARK: - Mock URLProtocol for URLSessionAuthNetworkClient

private final class PipelineMockURLProtocol: URLProtocol, @unchecked Sendable {
	nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

	override class func canInit(with request: URLRequest) -> Bool {
		true
	}

	override class func canonicalRequest(for request: URLRequest) -> URLRequest {
		request
	}

	override func startLoading() {
		guard let handler = PipelineMockURLProtocol.requestHandler else {
			client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
			return
		}

		do {
			let (response, data) = try handler(request)
			client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
			client?.urlProtocol(self, didLoad: data)
			client?.urlProtocolDidFinishLoading(self)
		} catch {
			client?.urlProtocol(self, didFailWithError: error)
		}
	}

	override func stopLoading() {}
}

// MARK: - Pipeline Mock Network Client

private final class PipelineMockNetworkClient: AuthNetworkClientProtocol, @unchecked Sendable {
	var preloginCallCount = 0
	var registerCallCount = 0
	var loginCallCount = 0
	var getVaultCallCount = 0
	var saveVaultCallCount = 0
	var changePasswordCallCount = 0
	var deleteAccountCallCount = 0

	var receivedGetVaultToken: String?
	var receivedSaveVaultToken: String?
	var receivedSaveVaultExpectedRev: Int?
	var receivedSaveVaultPayload: EncryptedVaultPayload?

	var getVaultResponse: GetVaultResponsePayload?
	var getVaultError: Error?
	var getVaultHandler: ((String) throws -> GetVaultResponsePayload?)?

	var saveVaultResponse: Int?
	var saveVaultError: Error?
	var saveVaultHandler: ((String, Int, EncryptedVaultPayload) throws -> Int)?

	func prelogin(email: String) async throws -> PreloginResponse {
		preloginCallCount += 1
		return PreloginResponse(kdf: "PBKDF2", salt: "c2FsdA==", iterations: 600000)
	}

	func register(request: RegisterRequestPayload) async throws -> RegisterResponsePayload {
		registerCallCount += 1
		return RegisterResponsePayload(accessToken: "test_token", expiresAt: "2026-11-27T11:20:00Z")
	}

	func login(request: LoginRequestPayload) async throws -> LoginResponsePayload {
		loginCallCount += 1
		return LoginResponsePayload(accessToken: "test_token", expiresAt: "2026-11-27T11:20:00Z")
	}

	func getVault(accessToken: String) async throws -> GetVaultResponsePayload? {
		getVaultCallCount += 1
		receivedGetVaultToken = accessToken

		if let handler = getVaultHandler {
			return try handler(accessToken)
		}
		if let error = getVaultError {
			throw error
		}
		return getVaultResponse
	}

	func saveVault(
		accessToken: String,
		expectedRevision: Int,
		payload: EncryptedVaultPayload
	) async throws -> Int {
		saveVaultCallCount += 1
		receivedSaveVaultToken = accessToken
		receivedSaveVaultExpectedRev = expectedRevision
		receivedSaveVaultPayload = payload

		if let handler = saveVaultHandler {
			return try handler(accessToken, expectedRevision, payload)
		}
		if let error = saveVaultError {
			throw error
		}
		return saveVaultResponse ?? (expectedRevision + 1)
	}

	func changePassword(
		accessToken: String,
		request: ChangePasswordRequestPayload
	) async throws -> ChangePasswordResponsePayload {
		changePasswordCallCount += 1
		return ChangePasswordResponsePayload(
			accessToken: "new_token",
			expiresAt: "2026-11-27T11:20:00Z",
			revision: 2
		)
	}

	func deleteAccount(accessToken: String, authHash: String) async throws {
		deleteAccountCallCount += 1
	}
}

// MARK: - Test Fixture

private struct SyncFixture {
	let masterPassword: String
	let email: String
	let sessionToken: String
	let salt: Data
	let iterations: Int
	let revision: Int
	let encryptionKey: Data
	let encryptedVault: EncryptedVaultPayload
	let decryptedVault: VaultData
}

private func makeSampleItem(
	id: String = "660e8400-e29b-41d4-a716-446655440001",
	serviceName: String = "TestService",
	username: String = "tester",
	password: String = "Password123!",
	notes: String? = nil
) -> VaultItem {
	VaultItem(
		id: id,
		serviceName: serviceName,
		username: username,
		password: password,
		notes: notes,
		createdAt: "2026-11-02T10:00:00Z",
		updatedAt: "2026-11-02T10:00:00Z"
	)
}

private func makeSyncFixture(
	isReadOnly: Bool = false,
	items: [VaultItem] = [makeSampleItem()],
	revision: Int = 1
) throws -> SyncFixture {
	let masterPassword = "MasterPassword123!"
	let email = "sync_user@keymanage.local"
	let sessionToken = "session_token_sync_pipeline_999"
	let salt = Data([
		0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
		0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
	])
	let iterations = PBKDF2KeyDeriver.defaultIterations

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
	let encoded = try JSONEncoder().encode(testVault)
	let encrypted = try VaultCipher.encrypt(plaintext: encoded, key: encryptionKey)

	return SyncFixture(
		masterPassword: masterPassword,
		email: email,
		sessionToken: sessionToken,
		salt: salt,
		iterations: iterations,
		revision: revision,
		encryptionKey: encryptionKey,
		encryptedVault: encrypted,
		decryptedVault: testVault
	)
}

private func makeUnlockedManager(
	fixture: SyncFixture,
	now: Date = Date()
) throws -> VaultLockManager {
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
	return manager
}

private func encryptVault(_ vault: VaultData, key: Data) throws -> EncryptedVaultPayload {
	let encoded = try JSONEncoder().encode(vault)
	return try VaultCipher.encrypt(plaintext: encoded, key: key)
}

private func makeMockURLSessionNetworkClient() -> URLSessionAuthNetworkClient {
	let config = URLSessionConfiguration.ephemeral
	config.protocolClasses = [PipelineMockURLProtocol.self]
	let session = URLSession(configuration: config)
	return URLSessionAuthNetworkClient(
		baseURL: URL(string: "https://api.keymanage.local/api/v1")!,
		session: session
	)
}

// MARK: - VaultSyncPipelineTests Suite

@Suite("VaultSyncPipelineTests - VLT-001 & VLT-002 원격 볼트 동기화 파이프라인 단위 테스트", .serialized)
struct VaultSyncPipelineTests {

	// MARK: - 1. URLSessionAuthNetworkClient.saveVault 응답 및 오류 매핑 테스트

	@Test("URLSession.saveVault: 200 OK 응답 시 새 revision 정상 파싱 및 반환 검증")
	func urlSession_saveVault_when200OK_returnsNewRevision() async throws {
		let client = makeMockURLSessionNetworkClient()
		let dummyPayload = EncryptedVaultPayload(
			iv: "dGVzdF9pdg==",
			ciphertext: "dGVzdF9jaXBoZXJ0ZXh0",
			tag: "dGVzdF90YWc="
		)

		PipelineMockURLProtocol.requestHandler = { request in
			#expect(request.httpMethod == "POST")
			#expect(request.url?.path == "/api/v1/vault")
			#expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer valid_access_token")

			let responseJSON = #"{"revision": 7}"#
			let response = HTTPURLResponse(
				url: request.url!,
				statusCode: 200,
				httpVersion: nil,
				headerFields: ["Content-Type": "application/json"]
			)!
			return (response, Data(responseJSON.utf8))
		}

		let newRevision = try await client.saveVault(
			accessToken: "valid_access_token",
			expectedRevision: 6,
			payload: dummyPayload
		)

		#expect(newRevision == 7)
	}

	@Test("URLSession.saveVault: 409 Conflict 응답 시 VaultSyncError.conflict 로 올바르게 매핑 검증")
	func urlSession_saveVault_when409Conflict_mapsToVaultSyncErrorConflict() async throws {
		let client = makeMockURLSessionNetworkClient()
		let dummyPayload = EncryptedVaultPayload(
			iv: "dGVzdF9pdg==",
			ciphertext: "dGVzdF9jaXBoZXJ0ZXh0",
			tag: "dGVzdF90YWc="
		)

		PipelineMockURLProtocol.requestHandler = { request in
			let conflictJSON = #"{"code": "CONFLICT", "message": "Conflict", "currentRevision": 12}"#
			let response = HTTPURLResponse(
				url: request.url!,
				statusCode: 409,
				httpVersion: nil,
				headerFields: ["Content-Type": "application/json"]
			)!
			return (response, Data(conflictJSON.utf8))
		}

		await #expect(throws: VaultSyncError.conflict(currentRevision: 12)) {
			try await client.saveVault(
				accessToken: "token",
				expectedRevision: 5,
				payload: dummyPayload
			)
		}
	}

	@Test("URLSession.saveVault: 413 Payload Too Large 응답 시 VaultSyncError.payloadTooLarge 로 올바르게 매핑 검증")
	func urlSession_saveVault_when413PayloadTooLarge_mapsToVaultSyncErrorPayloadTooLarge() async throws {
		let client = makeMockURLSessionNetworkClient()
		let dummyPayload = EncryptedVaultPayload(
			iv: "dGVzdF9pdg==",
			ciphertext: "large_ciphertext_sample",
			tag: "dGVzdF90YWc="
		)

		PipelineMockURLProtocol.requestHandler = { request in
			let response = HTTPURLResponse(
				url: request.url!,
				statusCode: 413,
				httpVersion: nil,
				headerFields: [:]
			)!
			return (response, Data())
		}

		let expectedBytes = dummyPayload.ciphertext.utf8.count
		await #expect(throws: VaultSyncError.payloadTooLarge(expectedBytes)) {
			try await client.saveVault(
				accessToken: "token",
				expectedRevision: 1,
				payload: dummyPayload
			)
		}
	}

	@Test("URLSession.saveVault: 401 Unauthorized 응답 시 AuthError.sessionExpired 로 매핑 검증")
	func urlSession_saveVault_when401Unauthorized_mapsToSessionExpired() async throws {
		let client = makeMockURLSessionNetworkClient()
		let dummyPayload = EncryptedVaultPayload(
			iv: "aXY=",
			ciphertext: "dGVzdA==",
			tag: "dGFn"
		)

		PipelineMockURLProtocol.requestHandler = { request in
			let response = HTTPURLResponse(
				url: request.url!,
				statusCode: 401,
				httpVersion: nil,
				headerFields: [:]
			)!
			return (response, Data())
		}

		await #expect(throws: AuthError.sessionExpired) {
			try await client.saveVault(
				accessToken: "expired_token",
				expectedRevision: 1,
				payload: dummyPayload
			)
		}
	}

	@Test("URLSession.saveVault: 429 Too Many Requests 응답 시 AuthError.tooManyAttempts 로 매핑 검증")
	func urlSession_saveVault_when429TooManyRequests_mapsToTooManyAttempts() async throws {
		let client = makeMockURLSessionNetworkClient()
		let dummyPayload = EncryptedVaultPayload(
			iv: "aXY=",
			ciphertext: "dGVzdA==",
			tag: "dGFn"
		)

		PipelineMockURLProtocol.requestHandler = { request in
			let response = HTTPURLResponse(
				url: request.url!,
				statusCode: 429,
				httpVersion: nil,
				headerFields: [:]
			)!
			return (response, Data())
		}

		await #expect(throws: AuthError.tooManyAttempts) {
			try await client.saveVault(
				accessToken: "token",
				expectedRevision: 1,
				payload: dummyPayload
			)
		}
	}

	@Test("URLSession.saveVault: URLError (notConnectedToInternet, networkConnectionLost, timedOut) 시 AuthError.offline 으로 매핑 검증")
	func urlSession_saveVault_whenURLErrors_mapsToAuthErrorOffline() async throws {
		let client = makeMockURLSessionNetworkClient()
		let dummyPayload = EncryptedVaultPayload(
			iv: "aXY=",
			ciphertext: "dGVzdA==",
			tag: "dGFn"
		)

		// 1. notConnectedToInternet
		PipelineMockURLProtocol.requestHandler = { _ in
			throw URLError(.notConnectedToInternet)
		}
		await #expect(throws: AuthError.offline) {
			try await client.saveVault(accessToken: "tok", expectedRevision: 1, payload: dummyPayload)
		}

		// 2. networkConnectionLost
		PipelineMockURLProtocol.requestHandler = { _ in
			throw URLError(.networkConnectionLost)
		}
		await #expect(throws: AuthError.offline) {
			try await client.saveVault(accessToken: "tok", expectedRevision: 1, payload: dummyPayload)
		}

		// 3. timedOut
		PipelineMockURLProtocol.requestHandler = { _ in
			throw URLError(.timedOut)
		}
		await #expect(throws: AuthError.offline) {
			try await client.saveVault(accessToken: "tok", expectedRevision: 1, payload: dummyPayload)
		}
	}

	// MARK: - 2. RemoteVaultSyncService 암호화 업로드 및 복호화 다운로드 테스트

	@Test("RemoteVaultSyncService.saveVault: 로컬 볼트 인코딩 후 AES-256-GCM 암호화하여 saveVault 네트워크 호출 및 새 revision 반환 검증")
	func remoteVaultSyncService_saveVault_encryptsAndCallsNetwork() async throws {
		let fixture = try makeSyncFixture()
		let mockClient = PipelineMockNetworkClient()
		mockClient.saveVaultResponse = 5

		let service = RemoteVaultSyncService(networkClient: mockClient) {
			(token: fixture.sessionToken, encryptionKey: fixture.encryptionKey)
		}

		let returnedRev = try await service.saveVault(fixture.decryptedVault, expectedRevision: 4)

		#expect(returnedRev == 5)
		#expect(mockClient.saveVaultCallCount == 1)
		#expect(mockClient.receivedSaveVaultToken == fixture.sessionToken)
		#expect(mockClient.receivedSaveVaultExpectedRev == 4)

		// 네트워크로 전달된 페이로드가 AES-256-GCM 으로 정상 암호화되었는지 복호화하여 교차 검증
		let receivedPayload = try #require(mockClient.receivedSaveVaultPayload)
		let decryptedData = try VaultCipher.decrypt(payload: receivedPayload, key: fixture.encryptionKey)
		let decodedVault = try VaultData.decode(from: decryptedData)
		#expect(decodedVault.items == fixture.decryptedVault.items)
	}

	@Test("RemoteVaultSyncService.getVault: 서버에서 수신한 EncryptedVaultPayload를 복호화하여 VaultData 복원 검증")
	func remoteVaultSyncService_getVault_decryptsServerPayload() async throws {
		let fixture = try makeSyncFixture()
		let mockClient = PipelineMockNetworkClient()

		let serverItem1 = makeSampleItem(id: "srv-item-1", serviceName: "GitHub", username: "octocat")
		let serverItem2 = makeSampleItem(id: "srv-item-2", serviceName: "AWS", username: "cloudadmin")
		let serverVault = VaultData(
			version: 1,
			updatedAt: "2026-11-02T12:00:00Z",
			items: [serverItem1, serverItem2]
		)
		let serverEncrypted = try encryptVault(serverVault, key: fixture.encryptionKey)
		mockClient.getVaultResponse = GetVaultResponsePayload(revision: 10, payload: serverEncrypted)

		let service = RemoteVaultSyncService(networkClient: mockClient) {
			(token: fixture.sessionToken, encryptionKey: fixture.encryptionKey)
		}

		let (vault, revision) = try await service.getVault()

		#expect(revision == 10)
		#expect(vault.items.count == 2)
		#expect(vault.items[0].serviceName == "GitHub")
		#expect(vault.items[1].serviceName == "AWS")
		#expect(mockClient.getVaultCallCount == 1)
		#expect(mockClient.receivedGetVaultToken == fixture.sessionToken)
	}

	@Test("RemoteVaultSyncService: 자격 증명이 없는 경우(미로그인/세션 만료) AuthError.sessionExpired 발생 검증")
	func remoteVaultSyncService_whenNoCredentials_throwsSessionExpired() async throws {
		let mockClient = PipelineMockNetworkClient()
		let service = RemoteVaultSyncService(networkClient: mockClient) {
			nil
		}
		let dummyVault = VaultData(updatedAt: "2026-11-02T10:00:00Z", items: [])

		await #expect(throws: AuthError.sessionExpired) {
			try await service.saveVault(dummyVault, expectedRevision: 1)
		}

		await #expect(throws: AuthError.sessionExpired) {
			_ = try await service.getVault()
		}
	}

	// MARK: - 3. VaultLockManager.sync 동기화 파이프라인 전수 테스트

	@Test("VaultLockManager.sync: 잠금 상태에서 호출 시 VaultItemError.locked 에러 발생 검증")
	func sync_whenLocked_throwsVaultItemErrorLocked() async throws {
		let fixture = try makeSyncFixture()
		let mockClient = PipelineMockNetworkClient()

		// 1. 세션 미초기화 상태에서 sync 호출
		let uninitializedManager = VaultLockManager()
		await #expect(throws: VaultItemError.locked) {
			try await uninitializedManager.sync(networkClient: mockClient)
		}

		// 2. 잠금 해제 후 수동 잠금(lock())된 상태에서 sync 호출
		let manager = try makeUnlockedManager(fixture: fixture)
		manager.lock()
		#expect(manager.isLocked == true)

		await #expect(throws: VaultItemError.locked) {
			try await manager.sync(networkClient: mockClient)
		}
	}

	@Test("VaultLockManager.sync: 오프라인 상태(isOffline == true) 시 즉시 hasPendingSync = true 설정 및 로컬 볼트/revision 반환 검증")
	func sync_whenOffline_setsPendingSyncAndReturnsLocalVault() async throws {
		let fixture = try makeSyncFixture()
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		#expect(manager.hasPendingSync == false)
		manager.setOffline(true)
		#expect(manager.isOffline == true)

		let (vault, revision) = try await manager.sync(networkClient: mockClient)

		#expect(manager.hasPendingSync == true)
		#expect(revision == fixture.revision)
		#expect(vault.items == fixture.decryptedVault.items)
		#expect(mockClient.saveVaultCallCount == 0)
		#expect(mockClient.getVaultCallCount == 0)
	}

	@Test("VaultLockManager.sync: 대기 변경사항 없음 & 서버 revision 동일 시 네트워크 저장 없이 현재 로컬 상태 유지 검증")
	func sync_whenNoPending_andServerRevisionEqual_completesWithoutChanges() async throws {
		let fixture = try makeSyncFixture(revision: 3)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		#expect(manager.hasPendingSync == false)
		mockClient.getVaultResponse = GetVaultResponsePayload(
			revision: 3,
			payload: fixture.encryptedVault
		)

		let (vault, revision) = try await manager.sync(networkClient: mockClient)

		#expect(revision == 3)
		#expect(vault.items == fixture.decryptedVault.items)
		#expect(manager.hasPendingSync == false)
		#expect(mockClient.getVaultCallCount == 1)
		#expect(mockClient.saveVaultCallCount == 0)
	}

	@Test("VaultLockManager.sync: 대기 변경사항 없음 & 서버 revision 롤백 감지(remote.revision < localRevision) 시 revisionRollbackDetected 에러 발생 검증")
	func sync_whenNoPending_andServerRollback_throwsRevisionRollbackDetected() async throws {
		let fixture = try makeSyncFixture(revision: 5)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		#expect(manager.hasPendingSync == false)
		#expect(manager.revision == 5)

		// 서버가 이전 revision(3) 반환
		mockClient.getVaultResponse = GetVaultResponsePayload(
			revision: 3,
			payload: fixture.encryptedVault
		)

		await #expect(throws: VaultSyncError.revisionRollbackDetected(lastSeen: 5, server: 3)) {
			try await manager.sync(networkClient: mockClient)
		}
	}

	@Test("VaultLockManager.sync: 대기 변경사항 없음 & 서버 revision 앞섬 시 3-way 병합(VaultMerger) 수행 후 로컬 상태 갱신 검증")
	func sync_whenNoPending_andServerAhead_merges3WayAndUpdatesLocalState() async throws {
		let localItem = makeSampleItem(id: "item-local", serviceName: "LocalService")
		let fixture = try makeSyncFixture(items: [localItem], revision: 1)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		#expect(manager.hasPendingSync == false)

		// 원격 서버: revision 2, remoteItem 추가됨
		let remoteItem = makeSampleItem(id: "item-remote", serviceName: "RemoteService")
		let serverVault = VaultData(
			version: 1,
			updatedAt: "2026-11-02T12:00:00Z",
			items: [localItem, remoteItem]
		)
		let serverEncrypted = try encryptVault(serverVault, key: fixture.encryptionKey)
		mockClient.getVaultResponse = GetVaultResponsePayload(revision: 2, payload: serverEncrypted)

		let (mergedVault, newRev) = try await manager.sync(networkClient: mockClient)

		#expect(newRev == 2)
		#expect(manager.revision == 2)
		#expect(manager.hasPendingSync == false)
		#expect(mergedVault.items.contains { $0.id == "item-local" })
		#expect(mergedVault.items.contains { $0.id == "item-remote" })
		#expect(manager.decryptedVault?.items.contains { $0.id == "item-remote" } == true)
	}

	@Test("VaultLockManager.sync: 대기 변경사항 있음(hasPendingSync == true) & 정상 저장 성공 시 hasPendingSync = false 및 새 revision 갱신 검증")
	func sync_whenPendingSyncTrue_savesSuccessfully_resetsPendingSyncAndUpdatesRevision() async throws {
		let fixture = try makeSyncFixture(revision: 1)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		// 아이템 추가로 hasPendingSync = true 설정
		try manager.addVaultItem(
			serviceName: "NewSite",
			username: "newUser",
			password: "newPassword123!"
		)
		#expect(manager.hasPendingSync == true)

		mockClient.saveVaultResponse = 2

		let (vault, newRev) = try await manager.sync(networkClient: mockClient)

		#expect(newRev == 2)
		#expect(manager.revision == 2)
		#expect(manager.hasPendingSync == false)
		#expect(mockClient.saveVaultCallCount == 1)
		#expect(vault.items.contains { $0.serviceName == "NewSite" })
		#expect(manager.decryptedVault?.items.contains { $0.serviceName == "NewSite" } == true)
	}

	@Test("VaultLockManager.sync: 대기 변경사항 있음 & 409 충돌 발생 시 원격 볼트 재수신 후 3-way 병합 재시도 성공 검증")
	func sync_whenPendingSyncTrue_and409Conflict_fetchesRemoteAndRetries3WayMerge() async throws {
		let initialItem = makeSampleItem(id: "item-common", serviceName: "CommonService")
		let fixture = try makeSyncFixture(items: [initialItem], revision: 1)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		// 로컬에서 신규 아이템 추가
		try manager.addVaultItem(serviceName: "LocalAdded", username: "local", password: "pw1")
		#expect(manager.hasPendingSync == true)

		// 원격 서버: 다른 기기에서 "RemoteAdded" 아이템이 추가되어 revision 2로 올라감
		let remoteItem = makeSampleItem(id: "item-remote-added", serviceName: "RemoteAdded")
		let serverVault = VaultData(
			version: 1,
			updatedAt: "2026-11-02T12:00:00Z",
			items: [initialItem, remoteItem]
		)
		let serverEncrypted = try encryptVault(serverVault, key: fixture.encryptionKey)

		var saveAttempts = 0
		mockClient.saveVaultHandler = { token, expectedRev, payload in
			saveAttempts += 1
			if saveAttempts == 1 {
				// 1번째 시도: 충돌 발생 (서버는 revision 2)
				throw VaultSyncError.conflict(currentRevision: 2)
			}
			// 2번째 시도: 병합 후 재저장 성공 (새 revision 3)
			#expect(expectedRev == 2)
			return 3
		}

		mockClient.getVaultHandler = { token in
			GetVaultResponsePayload(revision: 2, payload: serverEncrypted)
		}

		let (mergedVault, finalRev) = try await manager.sync(networkClient: mockClient)

		#expect(saveAttempts == 2)
		#expect(mockClient.getVaultCallCount == 1)
		#expect(finalRev == 3)
		#expect(manager.revision == 3)
		#expect(manager.hasPendingSync == false)

		// 3-way 병합 결과 로컬 추가분과 원격 추가분 모두 존재 확인
		#expect(mergedVault.items.contains { $0.serviceName == "LocalAdded" })
		#expect(mergedVault.items.contains { $0.serviceName == "RemoteAdded" })
		#expect(mergedVault.items.contains { $0.serviceName == "CommonService" })
	}

	@Test("VaultLockManager.sync: 409 충돌 3회 초과 발생 시 conflictLimitExceeded 발생 및 hasPendingSync = true 유지 검증")
	func sync_whenConflictExceeds3Times_throwsConflictLimitExceeded_andRetainsPendingSync() async throws {
		let fixture = try makeSyncFixture(revision: 1)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		try manager.addVaultItem(serviceName: "ConflictItem", username: "u", password: "p")
		#expect(manager.hasPendingSync == true)

		// 서버가 매번 409 충돌을 반환
		var serverRev = 2
		mockClient.saveVaultHandler = { token, expectedRev, payload in
			serverRev += 1
			throw VaultSyncError.conflict(currentRevision: serverRev)
		}

		mockClient.getVaultHandler = { token in
			let serverVault = VaultData(
				version: 1,
				updatedAt: "2026-11-02T12:00:00Z",
				items: [fixture.decryptedVault.items[0]]
			)
			let enc = try encryptVault(serverVault, key: fixture.encryptionKey)
			return GetVaultResponsePayload(revision: serverRev, payload: enc)
		}

		// 재시도 3회 초과 시 conflictLimitExceeded(attempts: 4) 에러 throw 검증
		await #expect(throws: VaultSyncError.conflictLimitExceeded(attempts: 4)) {
			try await manager.sync(networkClient: mockClient)
		}

		// 실패 후에도 로컬 변경사항 보존을 위해 hasPendingSync 는 true 로 유지되어야 함
		#expect(manager.hasPendingSync == true)
	}

	@Test("VaultLockManager.sync: 볼트 크기가 5MB(5,242,880바이트)를 초과할 경우 payloadTooLarge 에러 발생 검증")
	func sync_whenPayloadExceeds5MB_throwsPayloadTooLarge() async throws {
		let oversizedNotes = String(repeating: "A", count: 5_242_890)
		let oversizedItem = makeSampleItem(
			id: "oversized-1",
			serviceName: "GiantItem",
			notes: oversizedNotes
		)
		let fixture = try makeSyncFixture(items: [oversizedItem], revision: 1)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		await #expect(throws: VaultSyncError.self) {
			try await manager.sync(networkClient: mockClient)
		}
	}

	@Test("VaultLockManager.sync: 비동기 통신 중 lock() 호출 시 평문 볼트가 메모리에 복원되지 않음 검증 (보안 격리)")
	func sync_whenLockedDuringInFlightSync_doesNotRestoreDecryptedVaultInMemory() async throws {
		let fixture = try makeSyncFixture(revision: 1)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		try manager.addVaultItem(serviceName: "Pending1", username: "u", password: "p")

		mockClient.saveVaultHandler = { token, expectedRev, payload in
			// 네트워크 통신 도중 사용자/타이머에 의해 잠금 실행
			manager.lock()
			#expect(manager.isLocked == true)
			return expectedRev + 1
		}

		_ = try await manager.sync(networkClient: mockClient)

		// 동기화가 성공하더라도 잠금 상태는 유지되어야 하며, 잠긴 상태에서 복호화된 볼트 조작이 차단되어야 함
		#expect(manager.isLocked == true)
		#expect(manager.decryptedVault == nil)
		#expect(throws: VaultItemError.locked) {
			_ = try manager.addVaultItem(serviceName: "Blocked", username: "u", password: "p")
		}
	}

	@Test("VaultLockManager.sync: 대기 변경사항 없는 상태에서 401 세션 만료 시 로그아웃 수행 및 sessionExpired throw 검증")
	func sync_whenNoPendingAndSessionExpired_logsOutAndThrowsSessionExpired() async throws {
		let fixture = try makeSyncFixture(revision: 1)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		mockClient.getVaultError = AuthError.sessionExpired

		await #expect(throws: AuthError.sessionExpired) {
			try await manager.sync(networkClient: mockClient)
		}

		#expect(manager.hasActiveSession == false)
		#expect(manager.isLocked == true)
	}

	@Test("VaultLockManager.sync: 서버의 상위 스키마(읽기 전용) 볼트 수신 시 저장을 차단하고 vaultIsReadOnly throw 검증")
	func sync_whenMergedVaultIsReadOnly_throwsVaultIsReadOnly() async throws {
		let fixture = try makeSyncFixture(revision: 1)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		let higherSchemaVault = VaultData(
			version: 99,
			updatedAt: "2026-11-02T12:00:00Z",
			items: [fixture.decryptedVault.items[0]]
		)
		let serverEnc = try encryptVault(higherSchemaVault, key: fixture.encryptionKey)
		mockClient.getVaultResponse = GetVaultResponsePayload(revision: 2, payload: serverEnc)

		await #expect(throws: VaultError.vaultIsReadOnly) {
			try await manager.sync(networkClient: mockClient)
		}
	}

	@Test("VaultLockManager.sync: 서버가 규칙에 맞지 않는 revision 반환 시 syncFailed throw 및 hasPendingSync 유지 검증")
	func sync_whenSaveVaultReturnsUnexpectedRevision_throwsSyncFailed() async throws {
		let fixture = try makeSyncFixture(revision: 1)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = PipelineMockNetworkClient()

		try manager.addVaultItem(serviceName: "ItemToSave", username: "u", password: "p")
		#expect(manager.hasPendingSync == true)

		// expectedRevision(1) + 1 인 2가 아닌 999 반환
		mockClient.saveVaultResponse = 999

		await #expect(throws: VaultSyncError.self) {
			try await manager.sync(networkClient: mockClient)
		}
		#expect(manager.hasPendingSync == true)
	}

	@Test("RemoteVaultSyncService.uploadVault: 읽기 전용 볼트 또는 5MB 초과 시 업로드 차단 검증")
	func remoteVaultSyncService_uploadVault_guardsReadOnlyAndSize() async throws {
		let fixture = try makeSyncFixture()
		let mockClient = PipelineMockNetworkClient()
		let service = RemoteVaultSyncService(networkClient: mockClient) {
			(token: fixture.sessionToken, encryptionKey: fixture.encryptionKey)
		}

		// 1. 읽기 전용 볼트
		let readOnlyVault = VaultData(version: 99, updatedAt: "2026-11-02T12:00:00Z", items: [], isReadOnly: true)
		await #expect(throws: VaultError.vaultIsReadOnly) {
			try await service.uploadVault(readOnlyVault, expectedRevision: 1)
		}

		// 2. 5MB 초과 볼트
		let oversized = String(repeating: "X", count: 5_242_890)
		let oversizedVault = VaultData(
			version: 1,
			updatedAt: "2026-11-02T12:00:00Z",
			items: [makeSampleItem(id: "big", serviceName: "big", notes: oversized)]
		)
		await #expect(throws: VaultSyncError.self) {
			try await service.uploadVault(oversizedVault, expectedRevision: 1)
		}
	}
}
