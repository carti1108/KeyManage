import KeyManageFeatureExample
import KeyManageShared
import SwiftUI

/// Unlock 피처만 띄우는 Example 앱(UnlockExampleActivity의 짝).
/// 시나리오를 고르면 공유 Unlock RIB을 가짜 세션으로 만들어 실제 SwiftUI 뷰에 붙인다
@main
struct UnlockExampleApp: App {
	var body: some Scene {
		WindowGroup {
			FeatureExample(title: "Unlock 시나리오", scenarios: UnlockScenario.entries.map(\.title)) { index, onEvent in
				let scenario = UnlockScenario.entries[index]
				let view = SwiftUIUnlockView()
				let dependency = ExampleUnlockDependency(view: view, session: scenario.session())
				let router = UnlockBuilder(dependency: dependency).build(unlockListener: FakeUnlockListener(onEvent: onEvent))
				let mount = router.mount(savedInstanceState: nil)
				return RunningExample(screen: view) { mount.unmount() }
			}
		}
	}
}

private final class ExampleUnlockDependency: NSObject, UnlockDependency {
	let unlockViewFactory: any UnlockViewFactory
	let session: any SessionPort

	init(view: SwiftUIUnlockView, session: any SessionPort) {
		self.unlockViewFactory = FixedUnlockViewFactory(view: view)
		self.session = session
	}
}

private final class FixedUnlockViewFactory: NSObject, UnlockViewFactory {
	let view: SwiftUIUnlockView

	init(view: SwiftUIUnlockView) {
		self.view = view
	}

	func makeUnlockView() -> any UnlockViewContract { view }
}
