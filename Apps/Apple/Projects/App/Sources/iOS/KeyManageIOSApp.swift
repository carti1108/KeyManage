import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

// iOS 앱 진입점. 화면은 공유 Kotlin RIB 트리(IosKeyManageApp)가 정하고, 이 앱은 루트 슬롯을 그린다 (domain-migration-plan 12.3의 2단계, carti ADR-0006).
// 화면 SwiftUI는 Sources/SharedUI를 macOS 앱과 함께 쓴다. 옛 Swift RIB 경로(Sources/Shared, Features)는 4단계에서 지운다
@main
struct KeyManageIOSApp: App {
	@State private var launcher = SharedAppLauncher(backupDocuments: SharedBackupDocuments())
	@Environment(\.scenePhase) private var scenePhase

	var body: some Scene {
		WindowGroup {
			SharedAppRootView(launcher: launcher)
				.onAppear { launcher.start() }
				.onChange(of: scenePhase) { _, phase in launcher.sceneChanged(to: phase) }
		}
	}
}
