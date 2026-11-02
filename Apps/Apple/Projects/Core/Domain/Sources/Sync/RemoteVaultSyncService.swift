import Foundation

public final class RemoteVaultSyncService: RemoteVaultSyncServiceProtocol, @unchecked Sendable {
	public typealias CredentialsProvider = @Sendable () -> (token: String, encryptionKey: Data)?

	private let networkClient: AuthNetworkClientProtocol
	private let credentialsProvider: CredentialsProvider

	public init(
		networkClient: AuthNetworkClientProtocol,
		credentialsProvider: @escaping CredentialsProvider
	) {
		self.networkClient = networkClient
		self.credentialsProvider = credentialsProvider
	}

	public func uploadVault(_ vault: VaultData, expectedRevision: Int) async throws -> Int {
		guard let credentials = credentialsProvider() else {
			throw AuthError.sessionExpired
		}

		guard !vault.isReadOnly else {
			throw VaultError.vaultIsReadOnly
		}

		let encodedPlaintext = try vault.encode()
		try VaultSyncValidator().validatePayloadSize(encodedPlaintext.count)
		let encryptedPayload = try VaultCipher.encrypt(plaintext: encodedPlaintext, key: credentials.encryptionKey)

		return try await networkClient.saveVault(
			accessToken: credentials.token,
			expectedRevision: expectedRevision,
			payload: encryptedPayload
		)
	}

	public func saveVault(_ vault: VaultData, expectedRevision: Int) async throws -> Int {
		try await uploadVault(vault, expectedRevision: expectedRevision)
	}

	public func fetchVault() async throws -> (vault: VaultData, revision: Int) {
		guard let credentials = credentialsProvider() else {
			throw AuthError.sessionExpired
		}

		guard let remoteResponse = try await networkClient.getVault(accessToken: credentials.token) else {
			let nowString = VaultDateHelper.format(Date())
			return (VaultData(updatedAt: nowString, items: []), 0)
		}

		let decryptedData = try VaultCipher.decrypt(payload: remoteResponse.payload, key: credentials.encryptionKey)
		let vault = try VaultData.decode(from: decryptedData)

		return (vault, remoteResponse.revision)
	}

	public func getVault() async throws -> (vault: VaultData, revision: Int) {
		try await fetchVault()
	}
}
