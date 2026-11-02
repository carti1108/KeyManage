import Foundation
import Testing
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface
@testable import VaultListFeatureImplementation
@testable import VaultListFeatureTesting

#if os(macOS)
import AppKit
#elseif os(iOS)
import UIKit
#endif

// MARK: - Test Doubles

@MainActor
final class VaultListPresenterSpy: VaultListPresentable {
	weak var listener: VaultListPresentableListener?
	var displayItemsCallCount = 0
	var displayedItems: [VaultItem] = []
	var showToastCallCount = 0
	var toastMessage: String?

	func displayItems(_ items: [VaultItem]) {
		displayItemsCallCount += 1
		displayedItems = items
	}

	func showToast(message: String) {
		showToastCallCount += 1
		toastMessage = message
	}
}

@MainActor
final class VaultListInteractorSpy: VaultListInteractable {
	weak var router: VaultListRouting?
	weak var listener: VaultListListener?

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
final class VaultListViewControllableSpy: VaultListViewControllable {
#if os(macOS)
	var nsViewController: NSViewController = NSViewController()
#elseif os(iOS)
	var uiViewController: UIViewController = UIViewController()
#endif
}

final class VaultLockManagerMock: VaultLockManagerProtocol, @unchecked Sendable {
	var isLocked: Bool = false
	var isPrivacyCoverActive: Bool = false
	var autoLockTimeout: AutoLockTimeout = .fiveMinutes
	var lastInteractionTime: Date = Date()
	var email: String? = nil
	var sessionToken: String? = nil
	var salt: Data? = nil
	var iterations: Int = 100_000
	var encryptedVault: EncryptedVaultPayload? = nil
	var masterKey: Data? = nil
	var encryptionKey: Data? = nil
	var decryptedVault: VaultData? = nil
	var isBiometricsEnabled: Bool = false
	var lastMasterPasswordUnlockDate: Date? = nil
	var currentBiometryStateHash: Data? = nil
	var revision: Int = 1
	var onClipboardClearNeeded: (@Sendable () -> Void)? = nil

	init(decryptedVault: VaultData? = nil) {
		self.decryptedVault = decryptedVault
	}

	func setupSession(
		email: String,
		sessionToken: String,
		salt: Data,
		iterations: Int,
		encryptedVault: EncryptedVaultPayload,
		isBiometricsEnabled: Bool = false,
		biometryStateHash: Data? = nil
	) {}
	func recordInteraction(at date: Date) {}
	func checkIdleTimeout(at date: Date) -> Bool { false }
	func didEnterBackground(at date: Date) {}
	func willEnterForeground(at date: Date) {}
	func lock() { isLocked = true }
	func unlockWithMasterPassword(password: String, now: Date) throws {}
	func unlockWithBiometrics(evaluatedStateHash: Data, recoveredEncryptionKey: Data, now: Date) throws {}
	func logout() { isLocked = true }

	func changeMasterPassword(
		currentPassword: String,
		newPassword: String,
		networkClient: AuthNetworkClientProtocol,
		now: Date
	) async throws {}

	func checkIfPasswordChangedOnServer(
		networkClient: AuthNetworkClientProtocol
	) async throws -> Bool { false }
}

final class ClipboardManagerSpy: ClipboardManagerProtocol, @unchecked Sendable {
	var autoBurnInterval: TimeInterval = 30.0
	var isTimerActive: Bool = false
	var copyCallCount = 0
	var copiedTexts: [String] = []
	var shouldThrowOnCopy = false

	func copy(_ text: String) throws {
		if shouldThrowOnCopy {
			throw ClipboardError.copyFailed
		}
		copyCallCount += 1
		copiedTexts.append(text)
	}
	func handleLock() {}
	func clear() {}
}

@MainActor
final class VaultListDependencyMock: VaultListDependency {
	let lockManager: VaultLockManagerProtocol
	let clipboardManager: ClipboardManagerProtocol

	init(
		lockManager: VaultLockManagerProtocol = VaultLockManagerMock(),
		clipboardManager: ClipboardManagerProtocol = ClipboardManagerSpy()
	) {
		self.lockManager = lockManager
		self.clipboardManager = clipboardManager
	}
}

// MARK: - VaultListTests Suite

@Suite("VaultListFeature microRIBs 핵심 동작 및 조립 단위 테스트")
@MainActor
struct VaultListTests {

	// MARK: - 1. Interactor 활성화 시 볼트 아이템 로드 및 Presenter 표시 검증
	@Test("interactor.didBecomeActive() 호출 시 lockManager.decryptedVault?.items를 읽어 presenter.displayItems(_:) 1회 호출 및 아이템 목록 전달 검증")
	func vaultListInteractor_didBecomeActive_loadsAndDisplaysItems() {
		// Given
		let items = [
			VaultItem(id: "1", serviceName: "GitHub", username: "octocat", password: "p1", createdAt: "2026-11-02T00:00:00Z", updatedAt: "2026-11-02T00:00:00Z"),
			VaultItem(id: "2", serviceName: "Google", username: "googler", password: "p2", createdAt: "2026-11-02T00:00:00Z", updatedAt: "2026-11-02T00:00:00Z")
		]
		let lockManager = VaultLockManagerMock(decryptedVault: VaultData(updatedAt: "2026-11-02T00:00:00Z", items: items))
		let clipboardManager = ClipboardManagerSpy()
		let presenter = VaultListPresenterSpy()
		let interactor = VaultListInteractor(lockManager: lockManager, clipboardManager: clipboardManager, presenter: presenter)

		// When
		interactor.didBecomeActive()

		// Then
		#expect(presenter.displayItemsCallCount == 1)
		#expect(presenter.displayedItems == items)
	}

	// MARK: - 2. 검색 쿼리 필터링 검증
	@Test("interactor.didSearch(query: 'git') 호출 시 GitHub 아이템만 필터링되어 presenter.displayItems(_:)에 전달되는지 검증")
	func vaultListInteractor_didSearch_filtersItemsByServiceNameOrUsername() {
		// Given
		let githubItem = VaultItem(id: "1", serviceName: "GitHub", username: "octocat", password: "p1", createdAt: "2026-11-02T00:00:00Z", updatedAt: "2026-11-02T00:00:00Z")
		let googleItem = VaultItem(id: "2", serviceName: "Google", username: "googler", password: "p2", createdAt: "2026-11-02T00:00:00Z", updatedAt: "2026-11-02T00:00:00Z")
		let appleItem = VaultItem(id: "3", serviceName: "Apple", username: "developer", password: "p3", createdAt: "2026-11-02T00:00:00Z", updatedAt: "2026-11-02T00:00:00Z")
		let allItems = [githubItem, googleItem, appleItem]

		let lockManager = VaultLockManagerMock(decryptedVault: VaultData(updatedAt: "2026-11-02T00:00:00Z", items: allItems))
		let clipboardManager = ClipboardManagerSpy()
		let presenter = VaultListPresenterSpy()
		let interactor = VaultListInteractor(lockManager: lockManager, clipboardManager: clipboardManager, presenter: presenter)
		interactor.didBecomeActive()
		presenter.displayItemsCallCount = 0
		presenter.displayedItems = []

		// When
		interactor.didSearch(query: "git")

		// Then
		#expect(presenter.displayItemsCallCount == 1)
		#expect(presenter.displayedItems == [githubItem])
	}

	// MARK: - 3. 비밀번호 복사 및 토스트 알림 검증 (COM-001)
	@Test("interactor.didRequestCopyPassword(item:) 호출 시 clipboardManager.copy(item.password) 1회 호출 및 presenter.showToast(message:) 호출 검증")
	func vaultListInteractor_didRequestCopyPassword_copiesToClipboardAndShowsToast() {
		// Given
		let item = VaultItem(id: "1", serviceName: "GitHub", username: "octocat", password: "super-secret-password", createdAt: "2026-11-02T00:00:00Z", updatedAt: "2026-11-02T00:00:00Z")
		let lockManager = VaultLockManagerMock()
		let clipboardManager = ClipboardManagerSpy()
		let presenter = VaultListPresenterSpy()
		let interactor = VaultListInteractor(lockManager: lockManager, clipboardManager: clipboardManager, presenter: presenter)

		// When
		interactor.didRequestCopyPassword(item: item)

		// Then
		#expect(clipboardManager.copyCallCount == 1)
		#expect(clipboardManager.copiedTexts == ["super-secret-password"])
		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.toastMessage != nil)
	}

	// MARK: - 4. 유저네임 복사 및 토스트 알림 검증
	@Test("interactor.didRequestCopyUsername(item:) 호출 시 clipboardManager.copy(item.username) 1회 호출 및 presenter.showToast(message:) 검증")
	func vaultListInteractor_didRequestCopyUsername_copiesToClipboardAndShowsToast() {
		// Given
		let item = VaultItem(id: "1", serviceName: "GitHub", username: "octocat", password: "super-secret-password", createdAt: "2026-11-02T00:00:00Z", updatedAt: "2026-11-02T00:00:00Z")
		let lockManager = VaultLockManagerMock()
		let clipboardManager = ClipboardManagerSpy()
		let presenter = VaultListPresenterSpy()
		let interactor = VaultListInteractor(lockManager: lockManager, clipboardManager: clipboardManager, presenter: presenter)

		// When
		interactor.didRequestCopyUsername(item: item)

		// Then
		#expect(clipboardManager.copyCallCount == 1)
		#expect(clipboardManager.copiedTexts == ["octocat"])
		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.toastMessage != nil)
	}

	// MARK: - 5. Router 초기화 시 Interactor 바인딩 검증
	@Test("VaultListRouter 생성 시 interactor.router가 self로 바인딩되는지 검증")
	func vaultListRouter_init_bindsInteractorRouter() {
		// Given
		let interactor = VaultListInteractorSpy()
		let viewController = VaultListViewControllableSpy()

		// When
		let router = VaultListRouter(interactor: interactor, viewController: viewController)

		// Then
		#expect(interactor.router === router)
	}

	// MARK: - 6. Builder DI 조립 검증
	@Test("VaultListBuilder.build(withListener:) 호출 시 VaultListRouting 및 VaultListViewControllable이 올바르게 조립되어 반환되는지 검증")
	func vaultListBuilder_build_assemblesAndReturnsRouting() {
		// Given
		let lockManager = VaultLockManagerMock()
		let clipboardManager = ClipboardManagerSpy()
		let dependency = VaultListDependencyMock(lockManager: lockManager, clipboardManager: clipboardManager)
		let builder = VaultListBuilder(dependency: dependency)
		let listener = MockVaultListListener()

		// When
		let routing = builder.build(withListener: listener)

		// Then
		#expect(routing is VaultListRouter)
		#expect(routing.viewControllable is VaultListViewControllable)
		#expect(routing.interactable is VaultListInteractor)
		let interactor = routing.interactable as? VaultListInteractor
		#expect(interactor?.listener === listener)
		#expect(interactor?.router === routing)
		#expect(interactor?.lockManager === lockManager)
		#expect(interactor?.clipboardManager === clipboardManager)
	}

	// MARK: - 7. 삭제된 볼트 아이템(isDeleted == true) 필터링 검증
	@Test("interactor.didBecomeActive() 호출 시 isDeleted == true 인 툼스톤 아이템은 필터링되어 제외되는지 검증")
	func vaultListInteractor_didBecomeActive_filtersOutDeletedItems() {
		let activeItem = VaultItem(id: "1", serviceName: "GitHub", username: "octocat", password: "p1", createdAt: "2026-11-02T00:00:00Z", updatedAt: "2026-11-02T00:00:00Z")
		let deletedItem = VaultItem(id: "2", serviceName: "Old", username: "old", password: "p2", createdAt: "2026-11-02T00:00:00Z", updatedAt: "2026-11-02T00:00:00Z", deletedAt: "2026-11-02T01:00:00Z")
		let lockManager = VaultLockManagerMock(decryptedVault: VaultData(updatedAt: "2026-11-02T00:00:00Z", items: [activeItem, deletedItem]))
		let presenter = VaultListPresenterSpy()
		let interactor = VaultListInteractor(lockManager: lockManager, clipboardManager: ClipboardManagerSpy(), presenter: presenter)

		interactor.didBecomeActive()

		#expect(presenter.displayedItems == [activeItem])
	}

	// MARK: - 8. 클립보드 복사 에러 시 실패 토스트 검증
	@Test("클립보드 복사 실패 시 ClipboardStrings.copyFailedMessage 토스트가 호출되는지 검증")
	func vaultListInteractor_didRequestCopy_whenClipboardFails_showsErrorToast() {
		let item = VaultItem(id: "1", serviceName: "GitHub", username: "octocat", password: "p1", createdAt: "2026-11-02T00:00:00Z", updatedAt: "2026-11-02T00:00:00Z")
		let lockManager = VaultLockManagerMock()
		let clipboard = ClipboardManagerSpy()
		clipboard.shouldThrowOnCopy = true
		let presenter = VaultListPresenterSpy()
		let interactor = VaultListInteractor(lockManager: lockManager, clipboardManager: clipboard, presenter: presenter)

		interactor.didRequestCopyPassword(item: item)

		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.toastMessage == ClipboardStrings.copyFailedMessage)
	}

	// MARK: - 9. ViewModel 마스킹 토글 상태 관리 검증
	@Test("VaultListViewModel 비밀번호 노출 토글 상태 관리 검증")
	func vaultListViewModel_togglePasswordVisibility() {
		let vm = VaultListViewModel()
		#expect(!vm.isPasswordRevealed(id: "1"))
		vm.togglePasswordVisibility(id: "1")
		#expect(vm.isPasswordRevealed(id: "1"))
		vm.togglePasswordVisibility(id: "1")
		#expect(!vm.isPasswordRevealed(id: "1"))
	}
}
