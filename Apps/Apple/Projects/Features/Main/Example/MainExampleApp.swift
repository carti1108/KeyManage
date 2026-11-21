import KeyManageFeatureExample
import KeyManageShared
import SwiftUI

/// Main 피처만 띄우는 Example 앱(MainExampleActivity의 짝). 탭 안의 자식 RIB은 실제 뷰로 띄운다
@main
struct MainExampleApp: App {
	var body: some Scene {
		WindowGroup {
			FeatureExample(title: "Main 시나리오", scenarios: AppleExampleFeature.main.scenarios) { index, onEvent in
				let view = SwiftUIMainView()
				let mount = AppleExamples.shared.launchMain(index: Int32(index), view: view, children: childViews(), onEvent: onEvent)
				return RunningExample(screen: view) { mount.unmount() }
			}
		}
	}
}

@MainActor
func childViews() -> AppleExampleChildViews {
	AppleExampleChildViews(vaultList: VaultListViews(), generator: GeneratorViews(), settings: SettingsViews())
}

final class VaultListViews: NSObject, VaultListViewFactory {
	func makeVaultListView() -> any VaultListViewContract { MainActor.assumeIsolated { SwiftUIVaultListView() } }
}

final class GeneratorViews: NSObject, PasswordGeneratorViewFactory {
	func makePasswordGeneratorView() -> any PasswordGeneratorViewContract { MainActor.assumeIsolated { SwiftUIPasswordGeneratorView() } }
}

final class SettingsViews: NSObject, SettingsViewFactory {
	func makeSettingsView() -> any SettingsViewContract { MainActor.assumeIsolated { SwiftUISettingsView() } }
}
