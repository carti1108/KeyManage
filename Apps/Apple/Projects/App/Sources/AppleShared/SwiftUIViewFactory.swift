import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 공유 코드가 RIB마다 요청하는 뷰를 SwiftUI 구현으로 만든다. 공유 코드는 메인 스레드에서만 부른다
final class SwiftUIViewFactory: NSObject, KeyManageViewFactory {
	func makeRootView() -> any RootViewControllable { MainActor.assumeIsolated { RootScreen() } }
	func makeAuthView() -> any AuthViewContract { MainActor.assumeIsolated { SwiftUIAuthView() } }
	func makeUnlockView() -> any UnlockViewContract { MainActor.assumeIsolated { SwiftUIUnlockView() } }
	func makeMainView() -> any MainViewContract { MainActor.assumeIsolated { SwiftUIMainView() } }
	func makeVaultListView() -> any VaultListViewContract { MainActor.assumeIsolated { SwiftUIVaultListView() } }
	func makePasswordGeneratorView() -> any PasswordGeneratorViewContract { MainActor.assumeIsolated { SwiftUIPasswordGeneratorView() } }
	func makeSettingsView() -> any SettingsViewContract { MainActor.assumeIsolated { SwiftUISettingsView() } }
}

/// 루트 뷰. 그릴 상태 없이 지금 단계의 자식 화면(로그인, 잠금, 볼트) 하나만 보인다
@MainActor
final class RootScreen: NSObject, @preconcurrency RootViewControllable, SwiftUIRenderable {
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

/// Foundation으로 메인 스레드 여부를 알려 주는 검사기. 어느 스레드에서 불려도 안전하다
final class FoundationMainThreadChecker: NSObject, MainThreadChecker {
	func isMainThread() -> Bool {
		Thread.isMainThread
	}
}
