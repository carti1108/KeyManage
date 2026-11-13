import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 볼트 화면 RIB의 뷰. 고른 탭의 자식 화면(볼트·생성기·설정)을 슬롯에 보이고, 잠그기·로그아웃·재연결·상태 배너를 그린다
@MainActor
@Observable
final class SharedMainScreen: NSObject, @preconcurrency MainViewContract, SwiftUIRenderable {
	@ObservationIgnored weak var listener: (any MainPresentableListener)?
	@ObservationIgnored private let activity: @MainActor () -> Void

	init(activity: @escaping @MainActor () -> Void) {
		self.activity = activity
	}

	@ObservationIgnored let slot = RIBScreenSlot()
	private(set) var state = MainViewState(
		email: "", selectedTab: .vault, readOnly: false, offline: false, syncError: nil,
		showsDeviceStorageWarning: false, needsReconnect: false, reconnectPassword: "",
		isReconnecting: false, reconnectError: nil, isConfirmingReconnectRollback: false,
		isConfirmingLogout: false, isLoggingOut: false
	)

	func renderMain(state: MainViewState) {
		self.state = state
	}

	func showScreen(view: any ViewControllable) {
		slot.show(anyView: view)
	}

	func hideScreen(view: any ViewControllable) {
		slot.hide(anyView: view)
	}

	func send(_ action: any MainViewAction) {
		activity()
		listener?.handleMain(action: action)
	}

	func makeView() -> AnyView {
		AnyView(SharedMainView(screen: self))
	}
}

private struct SharedMainView: View {
	let screen: SharedMainScreen

	private var state: MainViewState { screen.state }

	var body: some View {
		VStack(alignment: .leading, spacing: 12) {
			HStack {
				Text(state.email).font(.headline).accessibilityIdentifier("mainEmail")
				Spacer()
				Button("잠그기") { screen.send(MainViewActionRequestLock.shared) }
					.accessibilityIdentifier("mainLockButton")
				Button("로그아웃") { screen.send(MainViewActionRequestLogout.shared) }
					.disabled(state.isLoggingOut)
					.accessibilityIdentifier("mainLogoutButton")
			}
			banners
			if state.isConfirmingLogout {
				SharedLogoutPrompt(
					confirm: { screen.send(MainViewActionConfirmLogout.shared) },
					cancel: { screen.send(MainViewActionCancelLogout.shared) }
				)
			}
			if state.needsReconnect {
				reconnect
			}
			Picker("탭", selection: Binding(
				get: { state.selectedTab },
				set: { screen.send(MainViewActionSelectTab(tab: $0)) }
			)) {
				Text("볼트").tag(MainTab.vault)
				Text("생성기").tag(MainTab.generator)
				Text("설정").tag(MainTab.settings)
			}
			.pickerStyle(.segmented)
			.accessibilityIdentifier("mainTabPicker")
			RIBSlotView(slot: screen.slot)
				.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
		}
		.padding(16)
	}

	@ViewBuilder private var banners: some View {
		if let notice = state.offlineNotice {
			Text(notice).font(.footnote).accessibilityIdentifier("offlineBanner")
		}
		if let notice = state.readOnlyNotice {
			Text(notice).font(.footnote).accessibilityIdentifier("readOnlyBanner")
		}
		if let warning = state.deviceStorageWarning {
			Text(warning).font(.footnote).foregroundStyle(.red)
		}
		if let error = state.syncError {
			SharedErrorText(message: error)
		}
	}

	@ViewBuilder private var reconnect: some View {
		VStack(alignment: .leading, spacing: 8) {
			SharedTextInput(title: "마스터 비밀번호", value: state.reconnectPassword, isSecure: true) {
				screen.send(MainViewActionUpdateReconnectPassword(password: $0))
			}
			.accessibilityIdentifier("reconnectPasswordField")
			if let error = state.reconnectError {
				SharedErrorText(message: error)
			}
			if state.isConfirmingReconnectRollback {
				SharedRollbackPrompt(
					accept: { screen.send(MainViewActionAcceptReconnectRollback.shared) },
					cancel: { screen.send(MainViewActionCancelReconnectRollback.shared) }
				)
			}
			Button("다시 연결") { screen.send(MainViewActionReconnect.shared) }
				.disabled(state.isReconnecting)
				.accessibilityIdentifier("reconnectButton")
		}
	}
}
