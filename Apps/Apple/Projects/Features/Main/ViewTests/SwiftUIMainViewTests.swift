import KeyManageShared
import Testing
@testable import MainExample

/// 뷰는 "상태를 그리는가, 입력을 보내는가, 자식 화면을 붙이는가"만 확인한다. 동작은 공유 Kotlin 테스트가 맡는다
@MainActor
struct SwiftUIMainViewTests {
	@Test
	func scenarios_launchEach_showsVaultTab() {
		for index in AppleExampleFeature.main.scenarios.indices {
			let view = SwiftUIMainView()
			let mount = AppleExamples.shared.launchMain(index: Int32(index), view: view, children: childViews()) { _ in }

			#expect(view.state != nil)
			#expect(view.tabSlot.current is SwiftUIVaultListView)
			mount.unmount()
		}
	}

	@Test
	func selectTab_eachTab_swapsChildScreen() {
		let view = SwiftUIMainView()
		let mount = AppleExamples.shared.launchMain(index: 0, view: view, children: childViews()) { _ in }

		view.send(MainViewActionSelectTab(tab: .generator))
		#expect(view.tabSlot.current is SwiftUIPasswordGeneratorView)

		view.send(MainViewActionSelectTab(tab: .settings))
		#expect(view.tabSlot.current is SwiftUISettingsView)
		mount.unmount()
	}
}
