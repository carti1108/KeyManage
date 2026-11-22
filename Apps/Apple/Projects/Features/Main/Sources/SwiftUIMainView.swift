import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 볼트를 연 뒤의 화면 틀(탭, 안내, 다시 연결)의 SwiftUI 뷰(ComposeMainView의 짝).
/// 탭 안의 화면은 자식 RIB의 화면이라 Router가 `showScreen`으로 붙이는 것을 `tabSlot`에 그린다
@MainActor
@Observable
public final class SwiftUIMainView: NSObject, @preconcurrency MainViewContract, SwiftUIRenderable {
	@ObservationIgnored public var listener: (any MainPresentableListener)?
	public private(set) var state: MainViewState?
	let tabSlot = RIBScreenSlot()

	override public init() {}

	public func render(mainState state: MainViewState) {
		self.state = state
	}

	public func showScreen(view: any ViewControllable) {
		tabSlot.show(anyView: view)
	}

	public func hideScreen(view: any ViewControllable) {
		tabSlot.hide(anyView: view)
	}

	/// macOS는 사이드바 아래 상자에 설정 화면을 늘 둔다(데스크톱 시안)
	public var usesSidebarSettings: Bool {
		#if os(macOS)
		true
		#else
		false
		#endif
	}

	private(set) var sidebarSettings: SwiftUISettingsView?

	public func showSidebarSettings(view: any ViewControllable) {
		sidebarSettings = view as? SwiftUISettingsView
	}

	func send(_ action: MainViewAction) {
		listener?.handle(mainAction: action)
	}

	public func makeView() -> AnyView {
		AnyView(MainScreen(view: self))
	}
}

/// 위에는 안내, 가운데는 고른 탭의 화면, 아래는 탭 바
private struct MainScreen: View {
	let view: SwiftUIMainView

	var body: some View {
		if let state = view.state {
			layout(state)
			.background(DesignToken.View.background)
			.alert(RollbackWarning.shared.TITLE, isPresented: sharedStateBinding(state.isConfirmingReconnectRollback) {
				view.send(MainViewActionCancelReconnectRollback.shared)
			}) {
				Button(RollbackWarning.shared.ACCEPT, role: .destructive) { view.send(MainViewActionAcceptReconnectRollback.shared) }
				Button(RollbackWarning.shared.CANCEL, role: .cancel) { view.send(MainViewActionCancelReconnectRollback.shared) }
			} message: {
				Text(RollbackWarning.shared.BODY)
			}
			// 가입 직후 생체 해제를 켤지 묻는다 (SEC-003 값 표)
			.alert(BiometricUnlockMessages.shared.OFFER_TITLE, isPresented: sharedStateBinding(state.isOfferingBiometricUnlock) {
				view.send(MainViewActionDeclineBiometricOffer.shared)
			}) {
				Button(BiometricUnlockMessages.shared.OFFER_ACCEPT) { view.send(MainViewActionAcceptBiometricOffer.shared) }
				Button(BiometricUnlockMessages.shared.OFFER_DECLINE, role: .cancel) { view.send(MainViewActionDeclineBiometricOffer.shared) }
			} message: {
				Text(BiometricUnlockMessages.shared.OFFER_BODY)
			}
			// 올리지 못한 변경 사항이 있을 때 로그아웃 전에 묻는다 (VLT-002)
			.alert(PendingLogoutConfirm.shared.TITLE, isPresented: sharedStateBinding(state.isConfirmingLogout) {
				view.send(MainViewActionCancelLogout.shared)
			}) {
				Button(PendingLogoutConfirm.shared.CONFIRM, role: .destructive) { view.send(MainViewActionConfirmLogout.shared) }
				Button(PendingLogoutConfirm.shared.CANCEL, role: .cancel) { view.send(MainViewActionCancelLogout.shared) }
			} message: {
				Text(PendingLogoutConfirm.shared.BODY)
			}
		}
	}
}

extension MainScreen {
	/// iPhone은 아래 탭 바, macOS는 왼쪽 사이드바(데스크톱 시안, 웹 MainRibScreen과 같은 틀)
	@ViewBuilder
	private func layout(_ state: MainViewState) -> some View {
		#if os(macOS)
		HStack(spacing: 0) {
			Sidebar(state: state, settings: view.sidebarSettings, send: view.send)
			Divider()
			VStack(spacing: 0) {
				MainNotices(state: state, send: view.send)
				RIBSlotView(slot: view.tabSlot)
					.frame(maxWidth: .infinity, maxHeight: .infinity)
			}
		}
		#else
		VStack(spacing: 0) {
			MainNotices(state: state, send: view.send)
			RIBSlotView(slot: view.tabSlot)
				.frame(maxWidth: .infinity, maxHeight: .infinity)
			TabBar(selected: state.selectedTab) { view.send(MainViewActionSelectTab(tab: $0)) }
		}
		#endif
	}
}

#if os(macOS)
/// 위에는 브랜드·이메일과 메뉴, 아래에는 잠그기·로그아웃
private struct Sidebar: View {
	static let width: CGFloat = 240
	let state: MainViewState
	let settings: SwiftUISettingsView?
	let send: (MainViewAction) -> Void

	var body: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
				Text("KeyManage")
					.font(DesignToken.Typography.heading.weight(.bold))
					.accessibilityAddTraits(.isHeader)
				Text(state.email)
					.font(DesignToken.Typography.caption)
					.foregroundColor(DesignToken.View.mutedForeground)
					.lineLimit(1)
					.truncationMode(.middle)
			}
			VStack(spacing: DesignToken.Spacing.space1) {
				ForEach(MainTab.entries, id: \.self) { tab in
					menuButton(tab)
				}
			}
			Spacer()
			Divider()
			if let settings {
				settings.makeSidebarView()
			}
			HStack(spacing: DesignToken.Spacing.space2) {
				KMButton("잠그기", variant: .outline, size: .sm, icon: "lock", isFullWidth: true) { send(MainViewActionRequestLock.shared) }
					.accessibilityIdentifier("main.lock")
				KMButton("로그아웃", variant: .ghost, size: .sm, icon: "rectangle.portrait.and.arrow.right", isFullWidth: true) { send(MainViewActionRequestLogout.shared) }
					.accessibilityIdentifier("main.logout")
			}
		}
		.padding(DesignToken.Spacing.space4)
		.frame(width: Self.width)
		.frame(maxHeight: .infinity, alignment: .top)
		.background(DesignToken.View.card)
	}

	private func menuButton(_ tab: MainTab) -> some View {
		let isSelected = tab == state.selectedTab
		return Button { send(MainViewActionSelectTab(tab: tab)) } label: {
			HStack(spacing: DesignToken.Spacing.space3) {
				Image(systemName: tab.icon).frame(width: 16)
				Text(tab.label).font(DesignToken.Typography.label.weight(isSelected ? .semibold : .medium))
				Spacer()
			}
			.padding(.horizontal, DesignToken.Spacing.space3)
			.frame(height: 33)
			.foregroundColor(isSelected ? DesignToken.View.accentForeground : DesignToken.View.mutedForeground)
			.background(isSelected ? DesignToken.View.accent : Color.clear)
			.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.md))
			.contentShape(Rectangle())
		}
		.buttonStyle(.plain)
		.accessibilityAddTraits(isSelected ? .isSelected : [])
		.accessibilityIdentifier("main.tab.\(tab.name.lowercased())")
	}
}
#endif

private struct MainNotices: View {
	let state: MainViewState
	let send: (MainViewAction) -> Void

	var body: some View {
		VStack(spacing: DesignToken.Spacing.space2) {
			if let offline = state.offlineNotice { NoticeBox(text: offline) }
			if let readOnly = state.readOnlyNotice { NoticeBox(text: readOnly) }
			if let storage = state.deviceStorageWarning { NoticeBox(text: storage, isError: true) }
			if let syncError = state.syncError { NoticeBox(text: syncError.joinedText, isError: true) }
			if let offerError = state.biometricOfferError { NoticeBox(text: offerError, isError: true) }
			if let burnAt = state.clipboardBurnAt, let notice = state.clipboardNotice {
				ClipboardNotice(burnAtMillis: burnAt.toEpochMilliseconds(), text: notice.joinedText)
			}
			if state.needsReconnect {
				ReconnectCard(state: state, send: send)
			}
		}
		.padding(.horizontal, DesignToken.Spacing.space4)
		.padding(.top, DesignToken.Spacing.space2)
	}
}

/// 복사 직후 안내와 비울 때까지 남은 초 (COM-001). 남은 초는 공유 상태의 비울 시각에서 화면이 센다
private struct ClipboardNotice: View {
	let burnAtMillis: Int64
	let text: String

	var body: some View {
		TimelineView(.periodic(from: .now, by: 0.25)) { context in
			let remainingMillis = burnAtMillis - Int64(context.date.timeIntervalSince1970 * 1000)
			NoticeBox(text: text) {
				KMBurnBadge(remainingSeconds: max(0, Int((remainingMillis + 999) / 1000)))
			}
		}
		.accessibilityIdentifier("main.clipboardNotice")
	}
}

/// 세션 없이 연 볼트를 마스터 비밀번호로 다시 연결한다 (SEC-003, ADR-0019)
private struct ReconnectCard: View {
	let state: MainViewState
	let send: (MainViewAction) -> Void

	var body: some View {
		NoticeBox(text: ReconnectPrompt.shared.BODY) {
			KMPasswordField(
				text: Binding(get: { state.reconnectPassword }, set: { send(MainViewActionUpdateReconnectPassword(password: $0)) }),
				placeholder: "마스터 비밀번호",
				showCopyAction: false
			)
			.disabled(state.isReconnecting)
			if let error = state.reconnectError {
				ErrorText(error: error, identifier: "main.reconnectError")
			}
			KMButton(
				ReconnectPrompt.shared.ACTION,
				isLoading: state.isReconnecting,
				isDisabled: state.reconnectPassword.isEmpty,
				isFullWidth: true
			) { send(MainViewActionReconnect.shared) }
				.accessibilityIdentifier("main.reconnect")
		}
	}
}

private struct NoticeBox<Extra: View>: View {
	let text: String
	var isError = false
	@ViewBuilder var extra: () -> Extra

	var body: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
			Text(text).font(DesignToken.Typography.caption)
			extra()
		}
		.padding(DesignToken.Spacing.space3)
		.frame(maxWidth: .infinity, alignment: .leading)
		.foregroundColor(isError ? DesignToken.View.destructive : DesignToken.View.foreground)
		.background(isError ? DesignToken.View.destructive.opacity(0.1) : DesignToken.View.muted)
		.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.md))
	}
}

extension NoticeBox where Extra == EmptyView {
	init(text: String, isError: Bool = false) {
		self.init(text: text, isError: isError) { EmptyView() }
	}
}

private struct TabBar: View {
	let selected: MainTab
	let onSelect: (MainTab) -> Void

	var body: some View {
		HStack {
			ForEach(MainTab.entries, id: \.self) { tab in
				Button { onSelect(tab) } label: {
					VStack(spacing: DesignToken.Spacing.space1) {
						Image(systemName: tab.icon)
						Text(tab.label).font(DesignToken.Typography.caption)
					}
					.frame(maxWidth: .infinity, minHeight: 49)
					.foregroundColor(tab == selected ? DesignToken.View.primary : DesignToken.View.mutedForeground)
				}
				.accessibilityAddTraits(tab == selected ? .isSelected : [])
				.accessibilityIdentifier("main.tab.\(tab.name.lowercased())")
			}
		}
		.background(DesignToken.View.card)
		.overlay(alignment: .top) { Divider() }
	}
}

/// 탭 이름과 아이콘(platform-bindings.md 아이콘 표)
private extension MainTab {
	var label: String {
		switch self {
		case .generator: "생성기"
		case .settings: "설정"
		default: "볼트"
		}
	}

	var icon: String {
		switch self {
		case .generator: "key"
		case .settings: "gearshape"
		default: DesignToken.Icon.vaultLock
		}
	}
}

private extension ErrorMessage {
	var joinedText: String {
		if let title { "\(title)\n\(body)" } else { body }
	}
}
