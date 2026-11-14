import KeyManageShared
import Observation
import RIBsKMPSwiftUI
import SwiftUI

/// 공유 코드의 `IosKeyManageApp`을 만들고 시작한다. 루트 RIB 뷰를 창 슬롯에 붙이는 `ViewHost`이고,
/// SwiftUI 장면 상태를 Kotlin 수명주기 배선(자동 잠금·클립보드·대기 재전송·네트워크)에 넘긴다.
/// Kotlin 객체는 Sendable이 아니므로 모든 호출을 메인 액터에서 한다 (domain-migration-plan 12.4).
@MainActor
@Observable
final class SharedAppLauncher: NSObject, @preconcurrency ViewHost {
	let windowSlot = RIBScreenSlot()
	private(set) var startupError: String?
	@ObservationIgnored private var app: IosKeyManageApp?
	@ObservationIgnored private var wasInBackground = false
	@ObservationIgnored private let backupDocuments: any BackupDocumentPresenter

	/// - Parameter backupDocuments: 백업 파일 선택기. iOS는 문서 선택기, macOS는 저장·열기 패널
	init(backupDocuments: any BackupDocumentPresenter) {
		self.backupDocuments = backupDocuments
	}

	/// UI 테스트·다른 서버는 실행 인자 `-serverOrigin <출처>`로 바꾼다. 없으면 로컬 개발 서버
	private static var serverOrigin: String {
		UserDefaults.standard.string(forKey: "serverOrigin") ?? IosKeyManageAppKt.LOCAL_DEV_SERVER_ORIGIN
	}

	func start() {
		guard app == nil else { return }
		// 공유 코드의 생명주기 호출이 메인 스레드인지 검사한다 (carti ADR-0007)
		RibThreadingConfig.shared.mainThreadChecker = FoundationMainThreadChecker()
		let created: IosKeyManageApp
		#if DEBUG
		// UI 테스트는 이전 실행이 남긴 기기 기록·자동 잠금 설정 없이 시작한다
		if ProcessInfo.processInfo.arguments.contains("-resetForUITests") {
			do {
				try IosKeyManageApp.companion.resetForUITests()
			} catch {
				startupError = "테스트 상태를 초기화하지 못했습니다."
				return
			}
		}
		#endif
		do {
			created = try IosKeyManageApp.companion.create(
				serverOrigin: Self.serverOrigin,
				viewFactory: SharedViewFactory { [weak self] in self?.userActivity() },
				backupDocuments: backupDocuments
			)
		} catch {
			startupError = "앱을 준비하지 못했습니다. 다시 시도해 주세요."
			return
		}
		app = created
		startupError = nil
		created.start(host: self) { [weak self] message in
			// 시작에 실패한 앱은 버리고, 다시 시도하면 새로 만든다
			self?.app = nil
			self?.startupError = message
		}
	}

	/// 시작 실패 화면의 "다시 시도"
	func retry() {
		startupError = nil
		start()
	}

	/// iOS는 백그라운드만, macOS는 비활성(다른 앱으로 전환)도 SEC-003의 백그라운드로 넘긴다.
	/// iOS의 비활성은 제어 센터처럼 잠깐이라 넘기지 않는다
	func sceneChanged(to phase: ScenePhase) {
		switch phase {
		case .background where !wasInBackground, .inactive where Self.inactiveIsBackground && !wasInBackground:
			wasInBackground = true
			app?.didEnterBackground()
		case .active:
			guard wasInBackground else { return }
			wasInBackground = false
			app?.willEnterForeground()
		default:
			break
		}
	}

	#if os(macOS)
	private static let inactiveIsBackground = true
	#else
	private static let inactiveIsBackground = false
	#endif

	func userActivity() {
		app?.userActivity()
	}

	func show(view: any ViewControllable) {
		windowSlot.show(anyView: view)
	}

	func hide(view: any ViewControllable) {
		windowSlot.hide(anyView: view)
	}
}

/// Foundation으로 메인 스레드 여부를 알려 준다. 어느 스레드에서 불려도 안전하다
final class FoundationMainThreadChecker: NSObject, MainThreadChecker {
	func isMainThread() -> Bool {
		Thread.isMainThread
	}
}

/// 앱 창의 루트. 시작 실패 안내 또는 공유 RIB 루트 슬롯을 그리고, 활성이 아니면 볼트 내용을 가린다 (SEC-003 3번)
struct SharedAppRootView: View {
	let launcher: SharedAppLauncher
	@Environment(\.scenePhase) private var scenePhase

	var body: some View {
		ZStack {
			if let message = launcher.startupError {
				VStack(spacing: 16) {
					Text(message).accessibilityIdentifier("startupError")
					Button("다시 시도") { launcher.retry() }.accessibilityIdentifier("startupRetryButton")
				}
				.padding(24)
			} else {
				RIBSlotView(slot: launcher.windowSlot)
			}
			// 앱 전환 화면에 볼트 내용이 비치지 않게 가린다
			if scenePhase != .active {
				Self.privacyCover.ignoresSafeArea().accessibilityHidden(true)
			}
		}
	}

	#if os(macOS)
	private static let privacyCover = Color(nsColor: .windowBackgroundColor)
	#else
	private static let privacyCover = Color(uiColor: .systemBackground)
	#endif
}
