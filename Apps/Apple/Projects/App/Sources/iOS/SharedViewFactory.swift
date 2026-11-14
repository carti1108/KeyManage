import KeyManageShared

/// 공유 RIB이 요청하는 화면마다 SwiftUI 구현을 만든다.
/// 화면 입력(`listener`로 보내는 동작)은 모두 사용자 조작이라 자동 잠금 시간을 다시 잰다 (SEC-003)
@MainActor
final class SharedViewFactory: NSObject, @preconcurrency KeyManageViewFactory {
	private let activity: @MainActor () -> Void

	init(activity: @escaping @MainActor () -> Void) {
		self.activity = activity
	}

	func makeRootView() -> any RootViewControllable { SharedRootScreen() }
	func makeAuthView() -> any AuthViewContract { SharedAuthScreen(activity: activity) }
	func makeUnlockView() -> any UnlockViewContract { SharedUnlockScreen(activity: activity) }
	func makeMainView() -> any MainViewContract { SharedMainScreen(activity: activity) }
	func makeVaultListView() -> any VaultListViewContract { SharedVaultListScreen(activity: activity) }
	func makePasswordGeneratorView() -> any PasswordGeneratorViewContract { SharedPasswordGeneratorScreen(activity: activity) }
	func makeSettingsView() -> any SettingsViewContract { SharedSettingsScreen(activity: activity) }
}
