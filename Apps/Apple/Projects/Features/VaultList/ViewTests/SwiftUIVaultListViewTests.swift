import KeyManageShared
import Testing
@testable import VaultListExample

/// 뷰는 "상태를 그리는가, 입력을 보내는가, 자식 화면을 붙이는가"만 확인한다. 동작은 공유 Kotlin 테스트가 맡는다
@MainActor
struct SwiftUIVaultListViewTests {
	@Test
	func scenarios_launchEach_rendersState() {
		for index in AppleExampleFeature.vaultList.scenarios.indices {
			let view = SwiftUIVaultListView()
			let mount = AppleExamples.shared.launchVaultList(index: Int32(index), view: view, generatorViews: GeneratorViews()) { _ in }

			#expect(view.state != nil)
			mount.unmount()
		}
	}

	@Test
	func openGenerator_inForm_showsChildInSlot() {
		let view = SwiftUIVaultListView()
		let mount = AppleExamples.shared.launchVaultList(index: 0, view: view, generatorViews: GeneratorViews()) { _ in }

		view.send(VaultListViewActionStartAdd.shared)
		view.send(VaultListViewActionOpenGenerator.shared)

		#expect(view.generatorSlot.current is SwiftUIPasswordGeneratorView)
		mount.unmount()
	}
}
