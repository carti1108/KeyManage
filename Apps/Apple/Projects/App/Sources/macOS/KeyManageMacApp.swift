import AppKit
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

// macOS 앱 진입점. iOS와 같은 공유 Kotlin RIB 트리(AppleKeyManageApp)를 루트 슬롯에 그린다 (domain-migration-plan 12.5 macOS 전환).
// 화면 SwiftUI는 Sources/SharedUI를 iOS와 함께 쓴다.
// - 창은 하나다(Window). RIB 트리가 하나라 두 창이 같은 뷰를 나눠 그리지 않게 한다.
// - SEC-003 백그라운드는 앱 비활성(다른 앱으로 전환·숨김)이다. 장면 비활성은 저장·열기 패널이 키 창을 가져갈 때도 생겨 쓰지 않는다
@main
struct KeyManageMacApp: App {
	@State private var launcher = SharedAppLauncher(backupDocuments: MacBackupDocuments())

	init() {
		// 저장 패널이 열린 채 앱이 끝나 임시 폴더에 남은 백업 파일을 지운다 (VLT-004)
		MacPlatformKt.removeStagedBackups(directory: MacPlatformKt.backupStagingDirectory())
	}

	var body: some Scene {
		Window("KeyManage", id: "main") {
			SharedAppRootView(launcher: launcher)
				.frame(minWidth: 800, minHeight: 600)
				.onAppear { launcher.start() }
				.onReceive(NotificationCenter.default.publisher(for: NSApplication.didResignActiveNotification)) { _ in
					launcher.appActivityChanged(isActive: false)
				}
				.onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
					launcher.appActivityChanged(isActive: true)
				}
		}
	}
}
