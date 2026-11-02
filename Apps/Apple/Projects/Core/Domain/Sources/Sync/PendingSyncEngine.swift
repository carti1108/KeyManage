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
	public var isSyncing: Bool

	public init(
		hasPendingSync: Bool = false,
		isAutoSyncPaused: Bool = false,
		isOffline: Bool = false,
		baseRevision: Int = 0,
		pendingVault: VaultData? = nil,
		offlineWarningMessage: String? = nil,
		isSyncing: Bool = false
	) {
		self.hasPendingSync = hasPendingSync
		self.isAutoSyncPaused = isAutoSyncPaused
		self.isOffline = isOffline
		self.baseRevision = baseRevision
		self.pendingVault = pendingVault
		self.offlineWarningMessage = offlineWarningMessage
		self.isSyncing = isSyncing
	}
}

public enum PendingSyncStrings {
	public static let offlineWarning = "오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다."
	public static let logoutConfirmTitle = "동기화되지 않은 변경 사항"
	public static let logoutConfirmMessage = "로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다."
	public static let logoutButton = "로그아웃"
	public static let cancelButton = "취소"

	// 호환성 별칭
	public static var logoutDialogTitle: String { logoutConfirmTitle }
	public static var logoutDialogMessage: String { logoutConfirmMessage }
	public static var logoutConfirmButton: String { logoutButton }
	public static var logoutCancelButton: String { cancelButton }
}

public protocol RemoteVaultSyncServiceProtocol: Sendable {
	func uploadVault(_ vault: VaultData, expectedRevision: Int) async throws -> Int
	func fetchVault() async throws -> (vault: VaultData, revision: Int)
}

public extension RemoteVaultSyncServiceProtocol {
	func saveVault(_ vault: VaultData, expectedRevision: Int) async throws -> Int {
		try await uploadVault(vault, expectedRevision: expectedRevision)
	}

	func getVault() async throws -> (vault: VaultData, revision: Int) {
		try await fetchVault()
	}
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
		if errorDescription.contains("readonly") ||
			errorDescription.contains("payload too large") ||
			errorDescription.contains("400") ||
			errorDescription.contains("bad request") {
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
	func discardPendingChanges()
}

public final class PendingSyncEngine: PendingSyncEngineProtocol, @unchecked Sendable {
	public static let defaultRetryInterval: TimeInterval = 30.0
	public static let resyncIntervalSeconds: TimeInterval = 30.0

	public var retryInterval: TimeInterval {
		Self.defaultRetryInterval
	}

	private let lock = NSLock()
	private var _status: PendingSyncStatus = PendingSyncStatus()
	private var _currentEmail: String?

	public var status: PendingSyncStatus {
		getStatus()
	}

	private let remoteService: RemoteVaultSyncServiceProtocol?
	private let deviceStore: DeviceVaultStoreProtocol?
	private let coordinator: VaultSyncCoordinator
	private let failureClassifier: SyncFailureClassifierProtocol

	public init(
		remoteService: RemoteVaultSyncServiceProtocol? = nil,
		deviceStore: DeviceVaultStoreProtocol? = nil,
		coordinator: VaultSyncCoordinator? = nil,
		failureClassifier: SyncFailureClassifierProtocol = DefaultSyncFailureClassifier(),
		currentEmail: String? = nil
	) {
		self.remoteService = remoteService
		self.deviceStore = deviceStore
		self.coordinator = coordinator ?? VaultSyncCoordinator()
		self.failureClassifier = failureClassifier
		self._currentEmail = currentEmail
	}

	private func getStatus() -> PendingSyncStatus {
		lock.lock()
		defer { lock.unlock() }
		return _status
	}

	private func mutateStatus<T>(_ mutate: (inout PendingSyncStatus) -> T) -> T {
		lock.lock()
		defer { lock.unlock() }
		return mutate(&_status)
	}

	public func getCurrentEmail() -> String? {
		lock.lock()
		defer { lock.unlock() }
		return _currentEmail
	}

	public func setCurrentEmail(_ email: String?) {
		lock.lock()
		defer { lock.unlock() }
		_currentEmail = email
	}

	public func discardPendingChanges() {
		mutateStatus { st in
			st.hasPendingSync = false
			st.pendingVault = nil
		}
	}

	public func save(vault: VaultData, expectedRevision: Int) async throws -> Bool {
		let (existingPending, rev, isOffline) = mutateStatus { st -> (VaultData?, Int, Bool) in
			st.isAutoSyncPaused = false
			let baseRev = st.hasPendingSync ? st.baseRevision : expectedRevision
			return (st.pendingVault, baseRev, st.isOffline)
		}

		// 비재진입 락 밖에서 병합 수행 (P2 데드락 방지)
		let accumulatedVault: VaultData
		if let existing = existingPending {
			accumulatedVault = (try? coordinator.merger.merge(local: existing, server: vault)) ?? vault
		} else {
			accumulatedVault = vault
		}

		guard !accumulatedVault.isReadOnly else {
			mutateStatus { st in
				st.hasPendingSync = false
				st.pendingVault = nil
				st.isAutoSyncPaused = true
			}
			throw VaultError.vaultIsReadOnly
		}

		if isOffline {
			mutateStatus { st in
				st.hasPendingSync = true
				st.pendingVault = accumulatedVault
				st.baseRevision = rev
			}
			return true
		}

		// 상태에 누적본을 즉시 반영 (TOCTOU 방지)
		mutateStatus { st in
			st.hasPendingSync = true
			st.pendingVault = accumulatedVault
			st.baseRevision = rev
		}

		guard let remote = remoteService else {
			return true
		}

		do {
			let newRev = try await remote.uploadVault(accumulatedVault, expectedRevision: rev)

			if newRev != rev + 1 {
				mutateStatus { st in
					st.hasPendingSync = false
					st.pendingVault = nil
				}
				throw PendingSyncError.serverProtocolViolation(expected: rev + 1, actual: newRev)
			}

			mutateStatus { st in
				// 통신 도중 추가 수정이 없었을 때만 대기열 해제 (P1 레이스 컨디션 방지)
				if st.pendingVault == accumulatedVault {
					st.hasPendingSync = false
					st.pendingVault = nil
				}
				st.baseRevision = newRev
				st.isAutoSyncPaused = false
			}
			return true
		} catch {
			let failureType = failureClassifier.classify(error)
			switch failureType {
			case .transient:
				mutateStatus { st in
					st.hasPendingSync = true
					st.baseRevision = rev
				}
				return true
			case .definitiveRejection:
				mutateStatus { st in
					st.hasPendingSync = false
					st.pendingVault = nil
					st.isAutoSyncPaused = true
				}
				throw error
			case .serverProtocolViolation:
				mutateStatus { st in
					st.hasPendingSync = false
					st.pendingVault = nil
				}
				throw error
			}
		}
	}

	public func triggerNetworkReconnected() async throws {
		mutateStatus { st in
			st.isOffline = false
			st.offlineWarningMessage = nil
		}

		try await performResync()
	}

	public func triggerTimerTick() async throws {
		try await performResync()
	}

	private func performResync() async throws {
		let shouldStart = mutateStatus { st -> (shouldProceed: Bool, pending: VaultData?, baseRev: Int) in
			guard st.hasPendingSync, let pending = st.pendingVault else {
				return (false, nil, 0)
			}
			guard !st.isAutoSyncPaused, !st.isOffline, !st.isSyncing else {
				return (false, nil, 0)
			}
			st.isSyncing = true
			return (true, pending, st.baseRevision)
		}

		guard shouldStart.shouldProceed, let pending = shouldStart.pending else {
			return
		}

		defer {
			mutateStatus { $0.isSyncing = false }
		}

		guard let remote = remoteService else {
			return
		}

		do {
			let result = try await coordinator.syncWithRetry(
				local: pending,
				lastSeenRevision: shouldStart.baseRev,
				saveOperation: { vaultToSave, rev in
					try await remote.uploadVault(vaultToSave, expectedRevision: rev)
				},
				fetchOperation: {
					try await remote.fetchVault()
				}
			)

			mutateStatus { st in
				// 동기화 도중 신규 변경이 없었던 경우에만 대기열 초기화 (P1 동시성 데이터 유실 방지)
				if st.pendingVault == pending {
					st.hasPendingSync = false
					st.pendingVault = nil
				}
				st.baseRevision = result.newRevision
				st.isAutoSyncPaused = false
			}
		} catch {
			let failureType = failureClassifier.classify(error)
			switch failureType {
			case .definitiveRejection:
				mutateStatus { st in
					st.isAutoSyncPaused = true
				}
				throw error
			case .serverProtocolViolation:
				mutateStatus { st in
					st.hasPendingSync = false
					st.pendingVault = nil
				}
				throw error
			case .transient:
				throw error
			}
		}
	}

	public func triggerUnlock(serverVault: VaultData, serverRevision: Int) async throws -> VaultData {
		let currentStatus = mutateStatus { st -> PendingSyncStatus in
			st.isAutoSyncPaused = false
			return st
		}

		var serverVaultToUse: VaultData = serverVault
		var serverRevToUse: Int = serverRevision
		var fetchTransientlyFailed = false

		if let remote = remoteService {
			do {
				let fetched = try await remote.fetchVault()
				serverVaultToUse = fetched.vault
				serverRevToUse = fetched.revision
			} catch {
				let failureType = failureClassifier.classify(error)
				if failureType == .transient {
					// P1 오프라인 잠금 해제 지원: 일시적 네트워크 장애 시 전달받은 로컬 사본 사용
					fetchTransientlyFailed = true
				} else {
					throw error
				}
			}
		}

		// P2: 대기 변경 유무와 관계없이 되돌림 검증(Rollback Detection) 항상 수행
		try coordinator.validator.validateRevision(server: serverRevToUse, lastSeen: currentStatus.baseRevision)

		if currentStatus.hasPendingSync, let pendingVault = currentStatus.pendingVault {
			let mergedVault = try coordinator.merger.merge(local: pendingVault, server: serverVaultToUse)

			if !fetchTransientlyFailed, let remote = remoteService {
				do {
					let newRev = try await remote.uploadVault(mergedVault, expectedRevision: serverRevToUse)
					mutateStatus { st in
						if st.pendingVault == pendingVault {
							st.hasPendingSync = false
							st.pendingVault = nil
						}
						st.baseRevision = newRev
						st.isAutoSyncPaused = false
					}
					return mergedVault
				} catch {
					let uploadFailure = failureClassifier.classify(error)
					if uploadFailure == .transient {
						// 업로드 일시 실패 시 대기열에 유지하고 병합본 반환
						mutateStatus { st in
							st.hasPendingSync = true
							st.pendingVault = mergedVault
							st.baseRevision = serverRevToUse
						}
						return mergedVault
					} else {
						throw error
					}
				}
			} else {
				// 오프라인이거나 remote 없음: 대기열 유지하고 병합본 정상 반환 (잠금 해제 허용)
				mutateStatus { st in
					st.hasPendingSync = true
					st.pendingVault = mergedVault
					st.baseRevision = serverRevToUse
				}
				return mergedVault
			}
		} else {
			mutateStatus { st in
				st.baseRevision = serverRevToUse
			}
			return serverVaultToUse
		}
	}

	public func handleLock() async {
		// 기기 잠금 시 대기 내용 보존
	}

	public func handleSessionExpired() async {
		// 세션 만료(401) 시 대기 내용 보존
	}

	public func handleRelogin(email: String, serverVault: VaultData, serverRevision: Int) async throws -> VaultData? {
		let previousEmail = getCurrentEmail() ?? deviceStore?.currentRecord?.email
		let currentStatus = mutateStatus { st -> PendingSyncStatus in
			st.isAutoSyncPaused = false
			return st
		}

		if let prev = previousEmail, prev != email {
			// 다른 계정으로 로그인 성공: 대기 내용 폐기
			mutateStatus { st in
				st.hasPendingSync = false
				st.pendingVault = nil
				st.baseRevision = serverRevision
			}
			setCurrentEmail(email)
			return nil
		}

		setCurrentEmail(email)

		guard currentStatus.hasPendingSync, let pending = currentStatus.pendingVault else {
			mutateStatus { st in
				st.baseRevision = serverRevision
			}
			return nil
		}

		try coordinator.validator.validateRevision(server: serverRevision, lastSeen: currentStatus.baseRevision)
		let mergedVault = try coordinator.merger.merge(local: pending, server: serverVault)

		if let remote = remoteService {
			let newRev = try await remote.uploadVault(mergedVault, expectedRevision: serverRevision)
			mutateStatus { st in
				st.hasPendingSync = false
				st.pendingVault = nil
				st.baseRevision = newRev
				st.isAutoSyncPaused = false
			}
			return mergedVault
		} else {
			mutateStatus { st in
				st.hasPendingSync = false
				st.pendingVault = nil
				st.baseRevision = serverRevision
				st.isAutoSyncPaused = false
			}
			return mergedVault
		}
	}

	public func handleLogout() async throws -> Bool {
		mutateStatus { st in
			st.hasPendingSync = false
			st.pendingVault = nil
			st.baseRevision = 0
		}

		if let store = deviceStore {
			try await store.handleLogout()
		}
		return true
	}

	public func setOffline(_ isOffline: Bool) {
		mutateStatus { st in
			st.isOffline = isOffline
			st.offlineWarningMessage = isOffline ? PendingSyncStrings.offlineWarning : nil
		}
	}
}
