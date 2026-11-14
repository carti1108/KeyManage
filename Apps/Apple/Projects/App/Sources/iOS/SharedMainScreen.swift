import KeyManageDesignSystem
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

/// 옛 Swift MainView의 iOS 배치: 탭 바(볼트·생성기·설정)와 탭마다 내비게이션 막대(잠그기). 고른 탭의 RIB 화면 하나만 슬롯에 보인다
private struct SharedMainView: View {
	let screen: SharedMainScreen

	private var state: MainViewState { screen.state }

	private static let tabs: [MainTab] = [.vault, .generator, .settings]

	var body: some View {
		TabView(selection: Binding(
			get: { state.selectedTab },
			// TabView가 같은 탭을 다시 정할 때 보내면 자식 화면을 내렸다 다시 붙이는 일이 되풀이된다
			set: { if $0 != state.selectedTab { screen.send(MainViewActionSelectTab(tab: $0)) } }
		)) {
			ForEach(Self.tabs, id: \.self) { tab in
				NavigationStack {
					tabContent(tab)
						.navigationTitle(title(of: tab))
						.navigationBarTitleDisplayMode(.inline)
						.toolbar { toolbar }
				}
				.tabItem { Label(title(of: tab), systemImage: icon(of: tab)) }
				.tag(tab)
				.accessibilityIdentifier("mainTab-\(tab.name.lowercased())")
			}
		}
		.alert(
			PendingLogoutConfirm.shared.TITLE,
			isPresented: Binding(
				get: { state.isConfirmingLogout },
				set: { if !$0, state.isConfirmingLogout { screen.send(MainViewActionCancelLogout.shared) } }
			)
		) {
			Button(PendingLogoutConfirm.shared.CANCEL, role: .cancel) { screen.send(MainViewActionCancelLogout.shared) }
				.accessibilityIdentifier("logoutCancelButton")
			Button(PendingLogoutConfirm.shared.CONFIRM, role: .destructive) { screen.send(MainViewActionConfirmLogout.shared) }
				.accessibilityIdentifier("logoutConfirmButton")
		} message: {
			Text(PendingLogoutConfirm.shared.BODY)
		}
	}

	@ToolbarContentBuilder private var toolbar: some ToolbarContent {
		ToolbarItem(placement: .topBarLeading) {
			Text(state.email)
				.font(.caption)
				.foregroundStyle(DesignToken.View.mutedForeground)
				.lineLimit(1)
				.accessibilityIdentifier("mainEmail")
		}
		ToolbarItemGroup(placement: .topBarTrailing) {
			Button { screen.send(MainViewActionRequestLogout.shared) } label: {
				Image(systemName: "rectangle.portrait.and.arrow.right")
			}
			.disabled(state.isLoggingOut)
			.accessibilityLabel("로그아웃")
			.accessibilityIdentifier("mainLogoutButton")
			Button { screen.send(MainViewActionRequestLock.shared) } label: {
				Image(systemName: "lock")
			}
			.accessibilityLabel("볼트 잠금")
			.accessibilityIdentifier("mainLockButton")
		}
	}

	@ViewBuilder private func tabContent(_ tab: MainTab) -> some View {
		VStack(spacing: 0) {
			banners
			if tab == state.selectedTab {
				RIBSlotView(slot: screen.slot)
					.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
			} else {
				Color.clear
			}
		}
		.background(DesignToken.View.background)
	}

	@ViewBuilder private var banners: some View {
		let hasBanner = state.offlineNotice != nil || state.readOnlyNotice != nil || state.deviceStorageWarning != nil
			|| state.syncError != nil || state.needsReconnect
		if hasBanner {
			VStack(spacing: 8) {
				if let notice = state.offlineNotice {
					SharedBanner(kind: .offline, text: notice).accessibilityIdentifier("offlineBanner")
				}
				if let notice = state.readOnlyNotice {
					SharedBanner(kind: .info, text: notice).accessibilityIdentifier("readOnlyBanner")
				}
				if let warning = state.deviceStorageWarning {
					SharedBanner(kind: .warning, text: warning).accessibilityIdentifier("deviceStorageBanner")
				}
				if let error = state.syncError {
					SharedErrorText(message: error).accessibilityIdentifier("syncErrorBanner")
				}
				if state.needsReconnect {
					reconnect
				}
			}
			.padding(.horizontal, 16)
			.padding(.vertical, 8)
		}
	}

	private var reconnect: some View {
		VStack(alignment: .leading, spacing: 8) {
			Text(ReconnectPrompt.shared.BODY).font(.footnote).foregroundStyle(DesignToken.View.foreground)
			HStack(spacing: 8) {
				SharedTextInput(title: "마스터 비밀번호", value: state.reconnectPassword, isSecure: true) {
					screen.send(MainViewActionUpdateReconnectPassword(password: $0))
				}
				.accessibilityIdentifier("reconnectPasswordField")
				KMButton(ReconnectPrompt.shared.ACTION, variant: .primary, size: .sm, isLoading: state.isReconnecting, isDisabled: state.isReconnecting) {
					screen.send(MainViewActionReconnect.shared)
				}
				.accessibilityIdentifier("reconnectButton")
			}
			if let error = state.reconnectError {
				SharedErrorText(message: error)
			}
			if state.isConfirmingReconnectRollback {
				SharedRollbackPrompt(
					accept: { screen.send(MainViewActionAcceptReconnectRollback.shared) },
					cancel: { screen.send(MainViewActionCancelReconnectRollback.shared) }
				)
			}
		}
		.padding(12)
		.background(Color.orange.opacity(0.1))
		.clipShape(RoundedRectangle(cornerRadius: 8))
	}

	private func title(of tab: MainTab) -> String {
		switch tab {
		case .vault: "볼트"
		case .generator: "생성기"
		case .settings: "설정"
		default: ""
		}
	}

	private func icon(of tab: MainTab) -> String {
		switch tab {
		case .vault: "lock.shield"
		case .generator: "key.horizontal"
		case .settings: "gearshape"
		default: "questionmark"
		}
	}
}
