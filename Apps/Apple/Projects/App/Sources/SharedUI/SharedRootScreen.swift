import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 루트 RIB의 뷰. 세션 상태에 맞는 자식 화면(로그인·잠금 해제·볼트) 하나를 보인다
@MainActor
final class SharedRootScreen: NSObject, @preconcurrency RootViewControllable, SwiftUIRenderable {
	private let slot = RIBScreenSlot()

	func showScreen(view: any ViewControllable) {
		slot.show(anyView: view)
	}

	func hideScreen(view: any ViewControllable) {
		slot.hide(anyView: view)
	}

	func makeView() -> AnyView {
		AnyView(RIBSlotView(slot: slot))
	}
}
