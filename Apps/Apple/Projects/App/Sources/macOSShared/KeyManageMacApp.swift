import AppKit
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// macOS 앱 진입점. 공유 Kotlin의 `AppleKeyManageApp`이 세션과 RIB 트리를 맡고, 여기서는 루트 화면을 창에 붙이고 생명주기를 알린다
/// (modularization-plan F단계 ④ macOS, iOS KeyManageIOSApp의 짝)
@main
struct KeyManageMacApp: App {
	@State private var launcher = MacAppLauncher()
	@Environment(\.scenePhase) private var scenePhase

	var body: some Scene {
		WindowGroup {
			RIBSlotView(slot: launcher.windowSlot)
				.frame(minWidth: 480, minHeight: 600)
				.onAppear { launcher.start() }
		}
		.onChange(of: scenePhase) { _, phase in
			switch phase {
			case .active: launcher.onForeground()
			case .background: launcher.onBackground()
			default: break
			}
		}
	}
}

/// 공유 코드의 `AppleKeyManageApp`을 만들고, 루트 뷰를 창에 붙이는 `ViewHost` 역할을 한다
@MainActor
@Observable
final class MacAppLauncher: NSObject, @preconcurrency ViewHost {
	let windowSlot = RIBScreenSlot()
	@ObservationIgnored private var app: AppleKeyManageApp?
	@ObservationIgnored private var isStarted = false
	@ObservationIgnored private var eventMonitor: Any?

	func start() {
		guard !isStarted else { return }
		isStarted = true
		RibThreadingConfig.shared.mainThreadChecker = FoundationMainThreadChecker()
		let created = AppleKeyManageApp(
			serverUrl: Self.serverUrl,
			viewFactory: SwiftUIViewFactory(),
			platform: MacosPortsKt.macosPlatformPorts()
		)
		app = created
		created.start(host: self)
		// 키 입력·클릭마다 자동 잠금 시간을 다시 잰다 (SEC-003 규칙 2). 이벤트는 보기만 하고 그대로 넘긴다
		eventMonitor = NSEvent.addLocalMonitorForEvents(matching: [.keyDown, .leftMouseDown, .rightMouseDown, .scrollWheel]) { [weak self] event in
			self?.app?.onUserInteraction()
			return event
		}
	}

	func onForeground() { app?.onForeground() }
	func onBackground() { app?.onBackground() }

	func show(view: any ViewControllable) {
		windowSlot.show(anyView: view)
	}

	func hide(view: any ViewControllable) {
		windowSlot.hide(anyView: view)
	}

	/// 서버 주소. Info.plist의 `KeyManageServerURL`(빌드 설정 `KEYMANAGE_SERVER_URL`)에서 읽는다
	private static var serverUrl: String {
		(Bundle.main.object(forInfoDictionaryKey: "KeyManageServerURL") as? String).flatMap { $0.isEmpty ? nil : $0 }
			?? "http://localhost:8080"
	}
}
