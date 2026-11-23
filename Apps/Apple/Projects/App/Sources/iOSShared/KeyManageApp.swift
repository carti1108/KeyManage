import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI
import UIKit

/// iOS 앱 진입점. 공유 Kotlin의 `AppleKeyManageApp`이 세션과 RIB 트리를 맡고, 여기서는 루트 화면을 창에 붙이고 생명주기를 알린다
/// (Android MainActivity의 짝)
@main
struct KeyManageIOSApp: App {
	@State private var launcher = AppLauncher()
	@Environment(\.scenePhase) private var scenePhase

	var body: some Scene {
		WindowGroup {
			RIBSlotView(slot: launcher.windowSlot)
				.frame(maxWidth: .infinity, maxHeight: .infinity)
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
final class AppLauncher: NSObject, @preconcurrency ViewHost {
	let windowSlot = RIBScreenSlot()
	@ObservationIgnored private var app: AppleKeyManageApp?
	@ObservationIgnored private var isStarted = false

	func start() {
		guard !isStarted else { return }
		isStarted = true
		// 공유 코드의 생명주기 호출이 메인 스레드인지 검사한다(carti-RIBs-KMP ADR-0007)
		RibThreadingConfig.shared.mainThreadChecker = FoundationMainThreadChecker()
		let created = AppleKeyManageApp(
			serverUrl: Self.serverUrl,
			viewFactory: SwiftUIViewFactory(),
			platform: IosPlatformPortsKt.iosPlatformPorts(presenter: { Self.topViewController() })
		)
		app = created
		created.start(host: self)
		watchUserInteraction()
	}

	/// 화면을 만질 때마다 자동 잠금 시간을 다시 잰다 (SEC-003 규칙 2). SwiftUI 제스처를 루트에 걸면 List 버튼의 탭을
	/// 가로채므로, 창에 터치를 보기만 하는 인식기를 단다(Android의 onUserInteraction에 해당한다)
	private func watchUserInteraction() {
		let window = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first
		window?.addGestureRecognizer(TouchObserver { [weak self] in self?.onUserInteraction() })
	}

	func onForeground() { app?.onForeground() }
	func onBackground() { app?.onBackground() }
	func onUserInteraction() { app?.onUserInteraction() }

	func show(view: any ViewControllable) {
		windowSlot.show(anyView: view)
	}

	func hide(view: any ViewControllable) {
		windowSlot.hide(anyView: view)
	}

	/// 서버 주소. Info.plist의 `KeyManageServerURL`(빌드 설정 `KEYMANAGE_SERVER_URL`)에서 읽는다. 기본은 이 Mac의 로컬 백엔드다
	private static var serverUrl: String {
		(Bundle.main.object(forInfoDictionaryKey: "KeyManageServerURL") as? String).flatMap { $0.isEmpty ? nil : $0 }
			?? "http://localhost:8080"
	}

	/// 문서 선택 창을 띄울 맨 위 화면
	private static func topViewController() -> UIViewController? {
		let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
		var top = scene?.keyWindow?.rootViewController
		// 방금 누른 확인 알림처럼 닫히는 중인 화면 위에는 띄울 수 없다(띄우면 아무 일도 일어나지 않는다)
		while let presented = top?.presentedViewController, !presented.isBeingDismissed {
			top = presented
		}
		return top
	}
}


/// 터치가 시작되면 알리고 바로 실패해, 다른 제스처와 버튼의 터치를 막지 않는다
private final class TouchObserver: UIGestureRecognizer, UIGestureRecognizerDelegate {
	private let onTouch: () -> Void

	init(onTouch: @escaping () -> Void) {
		self.onTouch = onTouch
		super.init(target: nil, action: nil)
		cancelsTouchesInView = false
		delaysTouchesBegan = false
		delaysTouchesEnded = false
		delegate = self
	}

	override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
		onTouch()
		state = .failed
	}

	func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
		true
	}
}
