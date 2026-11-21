import KeyManageFeatureExample
import KeyManageShared
import SwiftUI

/// Unlock 피처만 띄우는 Example 앱(UnlockExampleActivity의 짝). 시나리오의 가짜 세션 조립은 공유 AppleExamples가 한다
@main
struct UnlockExampleApp: App {
	var body: some Scene {
		WindowGroup {
			FeatureExample(title: "Unlock 시나리오", scenarios: AppleExampleFeature.unlock.scenarios) { index, onEvent in
				let view = SwiftUIUnlockView()
				let mount = AppleExamples.shared.launchUnlock(index: Int32(index), view: view, onEvent: onEvent)
				return RunningExample(screen: view) { mount.unmount() }
			}
		}
	}
}
