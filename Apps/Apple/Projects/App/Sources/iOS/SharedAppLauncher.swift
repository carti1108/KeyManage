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

	/// UI 테스트·다른 서버는 실행 인자 `-serverOrigin <출처>`로 바꾼다. 없으면 로컬 개발 서버
	private static var serverOrigin: String {
		UserDefaults.standard.string(forKey: "serverOrigin") ?? IosKeyManageAppKt.LOCAL_DEV_SERVER_ORIGIN
	}

	func start() {
		guard app == nil else { return }
		// 공유 코드의 생명주기 호출이 메인 스레드인지 검사한다 (carti ADR-0007)
		RibThreadingConfig.shared.mainThreadChecker = FoundationMainThreadChecker()
		let created: IosKeyManageApp
		do {
			created = try IosKeyManageApp.companion.create(
				serverOrigin: Self.serverOrigin,
				viewFactory: SharedViewFactory { [weak self] in self?.userActivity() }
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

	func sceneChanged(to phase: ScenePhase) {
		switch phase {
		case .background:
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
