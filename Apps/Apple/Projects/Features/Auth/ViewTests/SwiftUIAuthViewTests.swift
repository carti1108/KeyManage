import KeyManageShared
import Testing
@testable import AuthExample

/// 뷰는 "상태를 그리는가, 입력을 보내는가"만 확인한다. 동작은 공유 Kotlin 테스트가 맡는다
@MainActor
struct SwiftUIAuthViewTests {
	@Test
	func scenarios_launchEach_rendersState() {
		for index in AppleExampleFeature.auth.scenarios.indices {
			let view = SwiftUIAuthView()
			let mount = AppleExamples.shared.launchAuth(index: Int32(index), view: view) { _ in }

			#expect(view.state != nil)
			mount.unmount()
		}
	}

	@Test
	func send_inputsAndSwitchMode_reachSharedState() {
		let view = SwiftUIAuthView()
		let mount = AppleExamples.shared.launchAuth(index: 0, view: view) { _ in }

		view.send(AuthViewActionUpdateEmail(email: "a@b.c"))
		view.send(AuthViewActionSwitchMode.shared)

		#expect(view.state?.email == "a@b.c")
		#expect(view.state?.isRegistering == true)
		mount.unmount()
	}
}
