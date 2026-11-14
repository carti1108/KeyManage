import AppKit
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

// macOS 앱 진입점. iOS와 같은 공유 Kotlin RIB 트리(IosKeyManageApp)를 루트 슬롯에 그린다 (domain-migration-plan 12.5 macOS 전환).
// 화면 SwiftUI는 Sources/SharedUI를 iOS와 함께 쓴다. 앱이 비활성(다른 앱으로 전환)·백그라운드가 되면 iOS 백그라운드와 같은 SEC-003 규칙을 따른다
@main
struct KeyManageMacApp: App {
	@State private var launcher = SharedAppLauncher(backupDocuments: MacBackupDocuments())
	@Environment(\.scenePhase) private var scenePhase

	var body: some Scene {
		WindowGroup {
			SharedAppRootView(launcher: launcher)
				.frame(minWidth: 800, minHeight: 600)
				.onAppear { launcher.start() }
				.onChange(of: scenePhase) { _, phase in launcher.sceneChanged(to: phase) }
		}
	}
}
