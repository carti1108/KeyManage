import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

// 볼트 화면의 탭 RIB 뷰. 2단계는 목록·생성·잠그기처럼 읽고 누르는 기본 동작만 그린다.
// 항목 편집·검색, 생성 옵션, 백업·비밀번호 변경·계정 삭제 화면은 RIB마다 3단계에서 기존 배치대로 옮긴다 (domain-migration-plan 12.3)

/// 볼트 목록 RIB의 뷰
@MainActor
@Observable
final class SharedVaultListScreen: NSObject, @preconcurrency VaultListViewContract, SwiftUIRenderable {
	@ObservationIgnored weak var listener: (any VaultListPresentableListener)?
	@ObservationIgnored let generatorSlot = RIBScreenSlot()
	private(set) var state: VaultListViewState?

	func renderVaultList(state: VaultListViewState) {
		self.state = state
	}

	func showGenerator(view: any ViewControllable) {
		generatorSlot.show(anyView: view)
	}

	func hideGenerator(view: any ViewControllable) {
		generatorSlot.hide(anyView: view)
	}

	func makeView() -> AnyView {
		AnyView(SharedVaultListView(screen: self))
	}
}

private struct SharedVaultListView: View {
	let screen: SharedVaultListScreen

	var body: some View {
		VStack(alignment: .leading, spacing: 8) {
			if let state = screen.state {
				if let message = state.emptyMessage {
					Text(message).foregroundStyle(.secondary).accessibilityIdentifier("vaultEmptyMessage")
				}
				List(state.items, id: \.id) { item in
					Text(item.serviceName ?? "").accessibilityIdentifier("vaultItem")
				}
				.listStyle(.plain)
			}
			RIBSlotView(slot: screen.generatorSlot)
		}
	}
}

/// 비밀번호 생성기 RIB의 뷰
@MainActor
@Observable
final class SharedPasswordGeneratorScreen: NSObject, @preconcurrency PasswordGeneratorViewContract, SwiftUIRenderable {
	@ObservationIgnored weak var listener: (any PasswordGeneratorPresentableListener)?
	@ObservationIgnored private let activity: @MainActor () -> Void

	init(activity: @escaping @MainActor () -> Void) {
		self.activity = activity
	}

	private(set) var state: PasswordGeneratorViewState?

	func renderPasswordGenerator(state: PasswordGeneratorViewState) {
		self.state = state
	}

	func send(_ action: any PasswordGeneratorViewAction) {
		activity()
		listener?.handlePasswordGenerator(action: action)
	}

	func makeView() -> AnyView {
		AnyView(SharedPasswordGeneratorView(screen: self))
	}
}

private struct SharedPasswordGeneratorView: View {
	let screen: SharedPasswordGeneratorScreen

	var body: some View {
		VStack(alignment: .leading, spacing: 12) {
			if let state = screen.state {
				Text(state.generated?.text ?? "")
					.font(.system(.body, design: .monospaced))
					.textSelection(.disabled)
					.accessibilityIdentifier("generatedPassword")
				if let problem = state.problemMessage {
					Text(problem).foregroundStyle(.red)
				}
				if let error = state.copyError {
					Text(error).foregroundStyle(.red)
				}
				HStack {
					Button("다시 만들기") { screen.send(PasswordGeneratorViewActionRegenerate.shared) }
						.accessibilityIdentifier("regenerateButton")
					if state.showsCopy {
						Button("복사") { screen.send(PasswordGeneratorViewActionCopy.shared) }
							.accessibilityIdentifier("copyGeneratedButton")
					}
				}
			}
		}
	}
}

/// 설정 RIB의 뷰
@MainActor
@Observable
final class SharedSettingsScreen: NSObject, @preconcurrency SettingsViewContract, SwiftUIRenderable {
	@ObservationIgnored weak var listener: (any SettingsPresentableListener)?
	@ObservationIgnored private let activity: @MainActor () -> Void

	init(activity: @escaping @MainActor () -> Void) {
		self.activity = activity
	}

	private(set) var state: SettingsViewState?

	func renderSettings(state: SettingsViewState) {
		self.state = state
	}

	func send(_ action: any SettingsViewAction) {
		activity()
		listener?.handleSettings(action: action)
	}

	func makeView() -> AnyView {
		AnyView(SharedSettingsView(screen: self))
	}
}

private struct SharedSettingsView: View {
	let screen: SharedSettingsScreen

	var body: some View {
		VStack(alignment: .leading, spacing: 12) {
			if let state = screen.state {
				LabeledContent("계정", value: state.email)
				LabeledContent("서버", value: state.serverAddress)
				if let error = state.error {
					Text(error).foregroundStyle(.red)
				}
				if let notice = state.notice {
					Text(notice)
				}
				Button("지금 잠그기") { screen.send(SettingsViewActionRequestLock.shared) }
					.accessibilityIdentifier("settingsLockButton")
				Button("로그아웃") { screen.send(SettingsViewActionRequestLogout.shared) }
					.accessibilityIdentifier("settingsLogoutButton")
			}
		}
	}
}
