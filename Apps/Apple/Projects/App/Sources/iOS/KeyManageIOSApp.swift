import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

// iOS 앱 진입점. 화면은 공유 Kotlin RIB 트리(IosKeyManageApp)가 정하고, 이 앱은 루트 슬롯을 그린다 (domain-migration-plan 12.3의 2단계, carti ADR-0006).
// 옛 Swift RIB 경로(Sources/Shared, Features)는 macOS 앱이 계속 쓰고, iOS 쪽은 4단계에서 지운다
@main
struct KeyManageIOSApp: App {
	@State private var launcher = SharedAppLauncher()
	@Environment(\.scenePhase) private var scenePhase

	var body: some Scene {
		WindowGroup {
			SharedAppRootView(launcher: launcher, scenePhase: scenePhase)
				.onAppear { launcher.start() }
				.onChange(of: scenePhase) { _, phase in launcher.sceneChanged(to: phase) }
		}
	}
}

private struct SharedAppRootView: View {
	let launcher: SharedAppLauncher
	let scenePhase: ScenePhase

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
			// 앱 전환 화면에 볼트 내용이 비치지 않게 가린다 (SEC-003 3번)
			if scenePhase != .active {
				Color(uiColor: .systemBackground).ignoresSafeArea().accessibilityHidden(true)
			}
		}
	}
}
