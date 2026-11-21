import KeyManageFeatureExample
import KeyManageShared
import SwiftUI

/// VaultList 피처만 띄우는 Example 앱(VaultListExampleActivity의 짝). 폼 안의 생성기는 실제 생성기 뷰로 띄운다
@main
struct VaultListExampleApp: App {
	var body: some Scene {
		WindowGroup {
			FeatureExample(title: "VaultList 시나리오", scenarios: AppleExampleFeature.vaultList.scenarios) { index, onEvent in
				let view = SwiftUIVaultListView()
				let mount = AppleExamples.shared.launchVaultList(
					index: Int32(index),
					view: view,
					generatorViews: GeneratorViews(),
					onEvent: onEvent
				)
				return RunningExample(screen: view) { mount.unmount() }
			}
		}
	}
}

final class GeneratorViews: NSObject, PasswordGeneratorViewFactory {
	func makePasswordGeneratorView() -> any PasswordGeneratorViewContract {
		MainActor.assumeIsolated { SwiftUIPasswordGeneratorView() }
	}
}
