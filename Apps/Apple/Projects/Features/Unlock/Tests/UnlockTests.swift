import Testing
@testable import UnlockFeatureImplementation
@testable import UnlockFeatureTesting
@testable import UnlockFeatureInterface

@Suite("UnlockFeature 단위 및 조립 테스트")
@MainActor
struct UnlockFeatureTests {

	@Test("잠금 해제 성공 시 Listener의 didUnlockVault가 1회 호출된다")
	func unlockSuccess_callsListener() {
		// Given
		let interactor = UnlockInteractor()
		let listener = UnlockListenerMock()
		interactor.listener = listener

		// When
		interactor.unlockSuccess()

		// Then
		#expect(listener.didUnlockVaultCallCount == 1)
	}

	@Test("UnlockBuilder가 Router, Interactor, Listener를 올바르게 결합하여 빌드한다")
	func builder_wiresComponentsSuccessfully() {
		// Given
		final class DummyDependency: UnlockDependency {}
		let builder = UnlockBuilder(dependency: DummyDependency())
		let listener = UnlockListenerMock()

		// When
		let routing = builder.build(withListener: listener)

		// Then
		#expect(routing.interactable is UnlockInteractor)
		let interactor = routing.interactable as? UnlockInteractor
		#expect(interactor?.listener === listener)
		#expect(interactor?.router === routing)
		#expect(routing.viewControllable is UnlockViewControllable)
	}
}
