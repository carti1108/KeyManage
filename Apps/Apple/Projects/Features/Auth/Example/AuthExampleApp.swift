import KeyManageFeatureExample
import KeyManageShared
import SwiftUI

/// Auth 피처만 띄우는 Example 앱(AuthExampleActivity의 짝)
@main
struct AuthExampleApp: App {
	var body: some Scene {
		WindowGroup {
			FeatureExample(title: "Auth 시나리오", scenarios: AppleExampleFeature.auth.scenarios) { index, onEvent in
				let view = SwiftUIAuthView()
				let mount = AppleExamples.shared.launchAuth(index: Int32(index), view: view, onEvent: onEvent)
				return RunningExample(screen: view) { mount.unmount() }
			}
		}
	}
}
