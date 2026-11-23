import KeyManageShared
import Testing
@testable import UnlockExample

/// 뷰는 "상태를 그리는가, 입력을 보내는가"만 확인한다. 동작은 공유 Kotlin 테스트가 맡는다
@MainActor
struct SwiftUIUnlockViewTests {
	@Test
	func render_givenState_keepsItForDrawing() {
		let view = SwiftUIUnlockView()
		let state = UnlockViewState(
			email: "user@example.com", password: "pw", isLoading: true, error: nil, isConfirmingRollback: false,
			isConfirmingLogout: true, isBiometricsAvailable: true, biometricNotice: nil
		)

		view.render(unlockState: state)

		#expect(view.state == state)
	}

	@Test
	func send_withListener_forwardsAction() {
		let view = SwiftUIUnlockView()
		let listener = UnlockListenerSpy()
		view.listener = listener

		view.send(UnlockViewActionUpdatePassword(password: "secret"))
		view.send(UnlockViewActionUnlock.shared)

		#expect(listener.received.count == 2)
		#expect((listener.received.first as? UnlockViewActionUpdatePassword)?.password == "secret")
		#expect(listener.received.last is UnlockViewActionUnlock)
	}

	@Test
	func send_withoutListener_isIgnored() {
		let view = SwiftUIUnlockView()

		view.send(UnlockViewActionUnlock.shared)

		#expect(view.listener == nil)
	}

	@Test
	func scenarios_launchEachWithoutCrash() {
		for index in AppleExampleFeature.unlock.scenarios.indices {
			let view = SwiftUIUnlockView()
			let mount = AppleExamples.shared.launchUnlock(index: Int32(index), view: view) { _ in }

			#expect(view.state.email == "user@example.com")
			mount.unmount()
		}
	}
}

private final class UnlockListenerSpy: NSObject, UnlockPresentableListener {
	private(set) var received: [UnlockViewAction] = []

	func handle(unlockAction action: UnlockViewAction) {
		received.append(action)
	}
}
