import KeyManageFeatureExample
import KeyManageShared
import SwiftUI

/// Settings 피처만 띄우는 Example 앱(SettingsExampleActivity의 짝)
@main
struct SettingsExampleApp: App {
	var body: some Scene {
		WindowGroup {
			FeatureExample(title: "Settings 시나리오", scenarios: AppleExampleFeature.settings.scenarios) { index, onEvent in
				let view = SwiftUISettingsView()
				let mount = AppleExamples.shared.launchSettings(index: Int32(index), view: view, onEvent: onEvent)
				return RunningExample(screen: view) { mount.unmount() }
			}
		}
	}
}
