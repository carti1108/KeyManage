import Foundation

public enum SyncFailureType: Equatable, Sendable {
	case transient
	case definitiveRejection
	case serverProtocolViolation
}

public enum PendingSyncError: Error, Equatable, Sendable {
	case serverProtocolViolation(expected: Int, actual: Int)
	case definitiveRejection(String)
	case autoSyncPaused
	case noPendingChanges
	case sessionExpired
}

public struct PendingSyncStatus: Equatable, Sendable {
	public var hasPendingSync: Bool
	public var isAutoSyncPaused: Bool
	public var isOffline: Bool
	public var baseRevision: Int
	public var pendingVault: VaultData?
	public var offlineWarningMessage: String?

	public init(
		hasPendingSync: Bool = false,
		isAutoSyncPaused: Bool = false,
		isOffline: Bool = false,
		baseRevision: Int = 0,
		pendingVault: VaultData? = nil,
		offlineWarningMessage: String? = nil
	) {
		self.hasPendingSync = hasPendingSync
		self.isAutoSyncPaused = isAutoSyncPaused
		self.isOffline = isOffline
		self.baseRevision = baseRevision
		self.pendingVault = pendingVault
		self.offlineWarningMessage = offlineWarningMessage
	}
}

public enum PendingSyncStrings {
	public static let offlineWarning = "오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다."
	public static let logoutDialogTitle = "동기화되지 않은 변경 사항"
	public static let logoutDialogMessage = "로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다."
	public static let logoutConfirmButton = "로그아웃"
	public static let logoutCancelButton = "취소"
}

public protocol RemoteVaultSyncServiceProtocol: Sendable {
	func uploadVault(_ vault: VaultData, expectedRevision: Int) async throws -> Int
	func fetchVault() async throws -> (vault: VaultData, revision: Int)
}

public protocol SyncFailureClassifierProtocol: Sendable {
	func classify(_ error: Error) -> SyncFailureType
}

public struct DefaultSyncFailureClassifier: SyncFailureClassifierProtocol, Sendable {
	public init() {}

	public func classify(_ error: Error) -> SyncFailureType {
		if let pendingError = error as? PendingSyncError {
			switch pendingError {
			case .serverProtocolViolation:
				return .serverProtocolViolation
			case .definitiveRejection:
				return .definitiveRejection
			default:
				return .transient
			}
		}

		if let syncError = error as? VaultSyncError {
			switch syncError {
			case .payloadTooLarge, .revisionRollbackDetected:
				return .definitiveRejection
			default:
				break
			}
		}

		if let vaultError = error as? VaultError {
			switch vaultError {
			case .vaultIsReadOnly, .invalidVaultFormat:
				return .definitiveRejection
			}
		}

		let errorDescription = String(describing: error).lowercased()
		if errorDescription.contains("readonly") || errorDescription.contains("payload too large") {
			return .definitiveRejection
		}

		return .transient
	}
}

public protocol PendingSyncEngineProtocol: Sendable {
	var status: PendingSyncStatus { get }
	var retryInterval: TimeInterval { get }

	func save(vault: VaultData, expectedRevision: Int) async throws -> Bool
	func triggerNetworkReconnected() async throws
	func triggerTimerTick() async throws
	func triggerUnlock(serverVault: VaultData, serverRevision: Int) async throws -> VaultData
	func handleLock() async
	func handleSessionExpired() async
	func handleRelogin(email: String, serverVault: VaultData, serverRevision: Int) async throws -> VaultData?
	func handleLogout() async throws -> Bool
	func setOffline(_ isOffline: Bool)
}

public final class PendingSyncEngine: PendingSyncEngineProtocol, @unchecked Sendable {
	public static let defaultRetryInterval: TimeInterval = 30.0

	public var retryInterval: TimeInterval {
		Self.defaultRetryInterval
	}

	public private(set) var status: PendingSyncStatus = PendingSyncStatus()

	private let remoteService: RemoteVaultSyncServiceProtocol?
	private let deviceStore: DeviceVaultStoreProtocol?
	private let coordinator: VaultSyncCoordinator?
	private let failureClassifier: SyncFailureClassifierProtocol

	public init(
		remoteService: RemoteVaultSyncServiceProtocol? = nil,
		deviceStore: DeviceVaultStoreProtocol? = nil,
		coordinator: VaultSyncCoordinator? = nil,
		failureClassifier: SyncFailureClassifierProtocol = DefaultSyncFailureClassifier()
	) {
		self.remoteService = remoteService
		self.deviceStore = deviceStore
		self.coordinator = coordinator
		self.failureClassifier = failureClassifier
	}

	public func save(vault: VaultData, expectedRevision: Int) async throws -> Bool {
		// RED 스텁: 항상 false 반환하여 성공 처리를 하지 않음
		return false
	}

	public func triggerNetworkReconnected() async throws {
		// RED 스텁
	}

	public func triggerTimerTick() async throws {
		// RED 스텁
	}

	public func triggerUnlock(serverVault: VaultData, serverRevision: Int) async throws -> VaultData {
		// RED 스텁: 병합 없이 서버 볼트 그대로 반환
		return serverVault
	}

	public func handleLock() async {
		// RED 스텁
	}

	public func handleSessionExpired() async {
		// RED 스텁
	}

	public func handleRelogin(email: String, serverVault: VaultData, serverRevision: Int) async throws -> VaultData? {
		// RED 스텁
		return nil
	}

	public func handleLogout() async throws -> Bool {
		// RED 스텁
		return false
	}

	public func setOffline(_ isOffline: Bool) {
		// RED 스텁
	}
}
