import KeyManageFeatureExample
import KeyManageShared
import SwiftUI

/// PasswordGenerator 피처만 띄우는 Example 앱(PasswordGeneratorExampleActivity의 짝)
@main
struct PasswordGeneratorExampleApp: App {
	var body: some Scene {
		WindowGroup {
			FeatureExample(title: "PasswordGenerator 시나리오", scenarios: AppleExampleFeature.generator.scenarios) { index, onEvent in
				let view = SwiftUIPasswordGeneratorView()
				let mount = AppleExamples.shared.launchGenerator(index: Int32(index), view: view, onEvent: onEvent)
				return RunningExample(screen: view) { mount.unmount() }
			}
		}
	}
}
