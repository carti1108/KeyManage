import KeyManageShared
import Testing
@testable import PasswordGeneratorExample

/// 뷰는 "상태를 그리는가, 입력을 보내는가"만 확인한다. 동작은 공유 Kotlin 테스트가 맡는다
@MainActor
struct SwiftUIPasswordGeneratorViewTests {
	@Test
	func scenarios_launchEach_rendersGeneratedPassword() {
		for index in AppleExampleFeature.generator.scenarios.indices {
			let view = SwiftUIPasswordGeneratorView()
			let mount = AppleExamples.shared.launchGenerator(index: Int32(index), view: view) { _ in }

			#expect(view.state != nil)
			mount.unmount()
		}
	}

	@Test
	func send_regenerate_changesPassword() {
		let view = SwiftUIPasswordGeneratorView()
		let mount = AppleExamples.shared.launchGenerator(index: 0, view: view) { _ in }
		let before = view.state?.generated?.text

		view.send(PasswordGeneratorViewActionRegenerate.shared)

		#expect(view.state?.generated?.text != before)
		mount.unmount()
	}

	@Test
	func send_changeLength_updatesLengthText() {
		let view = SwiftUIPasswordGeneratorView()
		let mount = AppleExamples.shared.launchGenerator(index: 0, view: view) { _ in }

		view.send(PasswordGeneratorViewActionChangeLength(text: "32"))

		#expect(view.state?.lengthText == "32")
		#expect(view.state?.generated?.text.count == 32)
		mount.unmount()
	}
}
