import KeyManageShared
import Testing
@testable import SettingsExample

/// 뷰는 "상태를 그리는가, 입력을 보내는가"만 확인한다. 동작은 공유 Kotlin 테스트가 맡는다
@MainActor
struct SwiftUISettingsViewTests {
	@Test
	func scenarios_launchEach_rendersState() {
		for index in AppleExampleFeature.settings.scenarios.indices {
			let view = SwiftUISettingsView()
			let mount = AppleExamples.shared.launchSettings(index: Int32(index), view: view) { _ in }

			#expect(view.state != nil)
			mount.unmount()
		}
	}

	@Test
	func selectAutoLock_withChoiceTimeout_marksItSelected() throws {
		let view = SwiftUISettingsView()
		let mount = AppleExamples.shared.launchSettings(index: 0, view: view) { _ in }
		let choice = try #require(view.state?.autoLockChoices.last)

		view.send(SettingsViewActionSelectAutoLock(timeout: choice.timeout))

		#expect(view.state?.autoLockTimeoutLabel == choice.label)
		mount.unmount()
	}

	@Test
	func startDeleteAccount_opensDeleteModal() {
		let view = SwiftUISettingsView()
		let mount = AppleExamples.shared.launchSettings(index: 0, view: view) { _ in }

		view.send(SettingsViewActionStartDeleteAccount.shared)

		#expect(view.state?.modal == .deleteAccount)
		mount.unmount()
	}
}
