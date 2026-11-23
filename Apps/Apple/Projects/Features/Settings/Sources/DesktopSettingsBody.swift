#if os(macOS)
import KeyManageDesignSystem
import KeyManageShared
import SwiftUI

/// macOS 설정 본문. 웹 SettingsRibScreen과 같이 구역 카드(계정·보안 정책·서버), 백업·계정 관리 카드, 세션 제어 카드를 둔다.
/// 확인 창과 비밀번호 입력 시트는 SettingsScreen이 iPhone과 같이 띄운다
struct DesktopSettingsBody: View {
	let state: SettingsViewState
	let send: (SettingsViewAction) -> Void

	var body: some View {
		ScrollView {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space6) {
				VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
					Text("설정")
						.font(DesignToken.Typography.title.weight(.bold))
						.accessibilityAddTraits(.isHeader)
					Text("계정 및 보안 정책 상태를 확인합니다.")
						.font(DesignToken.Typography.label)
						.foregroundColor(DesignToken.View.mutedForeground)
				}
				VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
					section("계정", icon: "person") {
						row("이메일", state.email)
						Divider()
						row("동기화 상태", state.syncOffline ? "오프라인" : "최신 상태 (동기화 완료)")
					}
					section("보안 정책", icon: "shield") {
						autoLockRow
						if state.showsBiometricToggle {
							Divider()
							biometricRow
						}
						Divider()
						row("클립보드 자동 삭제", "30초")
						Divider()
						row("암호화 구조", "AES-256-GCM")
					}
					if !state.serverAddress.isEmpty {
						section("서버", icon: "globe") {
							row("서버 주소", state.serverAddress)
						}
					}
					entryCard
					sessionCard
				}
			}
			.padding(DesignToken.Spacing.space6)
			.frame(maxWidth: 576)
			.frame(maxWidth: .infinity)
		}
		.background(DesignToken.View.background)
	}

	private func section(_ title: String, icon: String, @ViewBuilder content: () -> some View) -> some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
			Label(title, systemImage: icon)
				.font(DesignToken.Typography.label.weight(.semibold))
				.labelStyle(TintedIconLabelStyle())
			VStack(spacing: 0) { content() }
				.background(DesignToken.View.card)
				.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.lg))
				.overlay(RoundedRectangle(cornerRadius: DesignToken.Radius.lg).stroke(DesignToken.View.border, lineWidth: 1))
		}
	}

	private func row(_ label: String, _ value: String) -> some View {
		HStack(spacing: DesignToken.Spacing.space4) {
			Text(label).foregroundColor(DesignToken.View.mutedForeground)
			Spacer()
			Text(value).fontWeight(.medium).lineLimit(1).truncationMode(.middle)
		}
		.font(DesignToken.Typography.label)
		.padding(DesignToken.Spacing.space4)
		.accessibilityElement(children: .combine)
	}

	private var autoLockRow: some View {
		HStack {
			Text("자동 잠금").foregroundColor(DesignToken.View.mutedForeground)
			Spacer()
			Picker("자동 잠금", selection: Binding(
				get: { state.autoLockChoices.firstIndex { $0.isSelected } ?? 0 },
				set: { send(SettingsViewActionSelectAutoLock(timeout: state.autoLockChoices[$0].timeout)) }
			)) {
				ForEach(Array(state.autoLockChoices.enumerated()), id: \.offset) { index, choice in
					Text(choice.label).tag(index)
				}
			}
			.labelsHidden()
			.fixedSize()
			.accessibilityIdentifier("settings.autoLock")
		}
		.font(DesignToken.Typography.label)
		.padding(DesignToken.Spacing.space4)
	}

	private var biometricRow: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
			Toggle(BiometricUnlockMessages.shared.ACTION, isOn: Binding(
				get: { state.biometricEnabled },
				set: { send(SettingsViewActionSetBiometricUnlock(enabled: $0)) }
			))
			.disabled(state.inProgress)
			.accessibilityIdentifier("settings.biometric")
			if let error = state.biometricError {
				DesktopAlert(text: error)
			}
		}
		.font(DesignToken.Typography.label)
		.padding(DesignToken.Spacing.space4)
	}

	/// 백업과 계정 관리 버튼. 결과 안내·오류는 모달이 닫혀 있을 때 이 카드 아래에 보인다
	private var entryCard: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space3) {
			VStack(spacing: DesignToken.Spacing.space2) {
				entry("백업 내보내기", icon: "square.and.arrow.down", enabled: state.canExportBackup, id: "export") {
					send(SettingsViewActionStartExport.shared)
				}
				entry("백업에서 복원", icon: "arrow.counterclockwise", enabled: state.canRestoreBackup, id: "restore") {
					send(SettingsViewActionStartRestore.shared)
				}
			}
			VStack(spacing: DesignToken.Spacing.space2) {
				entry("마스터 비밀번호 변경", icon: "key", enabled: state.canStartChangePassword, id: "changePassword") {
					send(SettingsViewActionStartChangePassword.shared)
				}
				entry("계정 삭제", icon: "trash", enabled: state.canStartDeleteAccount, isDestructive: true, id: "deleteAccount") {
					send(SettingsViewActionStartDeleteAccount.shared)
				}
			}
			if state.modal == .none, let error = state.error {
				DesktopAlert(text: error, identifier: "settings.message")
			}
			if let notice = state.notice {
				DesktopAlert(text: notice, isError: state.noticeKind == .error, identifier: "settings.message")
			}
		}
		.padding(DesignToken.Spacing.space4)
		.background(DesignToken.View.muted.opacity(0.2))
		.desktopCard(padding: 0)
	}

	private func entry(
		_ title: String,
		icon: String,
		enabled: Bool,
		isDestructive: Bool = false,
		id: String,
		perform: @escaping () -> Void
	) -> some View {
		Button(action: perform) {
			Label(title, systemImage: icon)
				.font(DesignToken.Typography.caption)
				.frame(maxWidth: .infinity, minHeight: 28, alignment: .leading)
				.padding(.horizontal, DesignToken.Spacing.space2)
				.foregroundColor(isDestructive ? DesignToken.View.destructive : DesignToken.View.foreground)
				.overlay(
					RoundedRectangle(cornerRadius: DesignToken.Radius.md)
						.stroke(isDestructive ? DesignToken.View.destructive.opacity(0.4) : DesignToken.View.input, lineWidth: 1)
				)
				.contentShape(Rectangle())
		}
		.buttonStyle(.plain)
		.disabled(!enabled)
		.opacity(enabled ? 1 : 0.5)
		.accessibilityIdentifier("settings.\(id)")
	}

	private var sessionCard: some View {
		HStack(spacing: DesignToken.Spacing.space3) {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
				Text("세션 제어").font(DesignToken.Typography.label.weight(.medium))
				Text("볼트를 잠그거나 계정에서 로그아웃합니다.")
					.font(DesignToken.Typography.caption)
					.foregroundColor(DesignToken.View.mutedForeground)
			}
			Spacer()
			KMButton("잠그기", variant: .outline, size: .sm, icon: "lock") { send(SettingsViewActionRequestLock.shared) }
				.fixedSize()
				.accessibilityIdentifier("settings.lock")
			KMButton("로그아웃", variant: .destructive, size: .sm, icon: "rectangle.portrait.and.arrow.right") {
				send(SettingsViewActionRequestLogout.shared)
			}
			.fixedSize()
			.accessibilityIdentifier("settings.logout")
		}
		.padding(DesignToken.Spacing.space4)
		.background(DesignToken.View.muted.opacity(0.2))
		.desktopCard(padding: 0)
	}
}

/// 구역 제목 아이콘을 강조색으로 칠한다(웹 `text-primary` 아이콘)
private struct TintedIconLabelStyle: LabelStyle {
	func makeBody(configuration: Configuration) -> some View {
		HStack(spacing: DesignToken.Spacing.space2) {
			configuration.icon.foregroundColor(DesignToken.View.primary)
			configuration.title
		}
	}
}
/// 사이드바 아래 상자(Figma 데스크톱 시안·옛 웹 VaultHomeScreen의 sidebarFooter).
/// 설정 탭과 같은 설정 RIB 상태를 그리고, 확인 창과 비밀번호 시트도 여기서 띄운다
struct SidebarSettingsBox: View {
	let view: SwiftUISettingsView

	var body: some View {
		if let state = view.state {
			settingsModals(box(state), state: state, view: view)
		}
	}

	private func box(_ state: SettingsViewState) -> some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
			HStack {
				Text("자동 잠금").foregroundColor(DesignToken.View.mutedForeground)
				Spacer()
				Menu {
					ForEach(Array(state.autoLockChoices.enumerated()), id: \.offset) { _, choice in
						Button {
							view.send(SettingsViewActionSelectAutoLock(timeout: choice.timeout))
						} label: {
							if choice.isSelected { Label(choice.label, systemImage: "checkmark") } else { Text(choice.label) }
						}
					}
				} label: {
					Text(state.autoLockTimeoutLabel)
						.font(DesignToken.Typography.caption.weight(.medium))
						.padding(.horizontal, DesignToken.Spacing.space2)
						.padding(.vertical, DesignToken.Spacing.space1)
						.background(DesignToken.View.background)
						.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.sm))
				}
				.menuStyle(.button)
				.buttonStyle(.plain)
				.fixedSize()
				.accessibilityLabel("자동 잠금 \(state.autoLockTimeoutLabel)")
				.accessibilityIdentifier("sidebar.autoLock")
			}
			.font(DesignToken.Typography.caption)
			entry("백업 내보내기", enabled: state.canExportBackup, id: "export") { view.send(SettingsViewActionStartExport.shared) }
			entry("백업에서 복원", enabled: state.canRestoreBackup, id: "restore") { view.send(SettingsViewActionStartRestore.shared) }
			entry("마스터 비밀번호 변경", enabled: state.canStartChangePassword, id: "changePassword") {
				view.send(SettingsViewActionStartChangePassword.shared)
			}
			entry("계정 삭제", enabled: state.canStartDeleteAccount, isDestructive: true, id: "deleteAccount") {
				view.send(SettingsViewActionStartDeleteAccount.shared)
			}
			if state.modal == .none, let error = state.error {
				Text(error).font(DesignToken.Typography.caption).foregroundColor(DesignToken.View.destructive)
			}
			if let notice = state.notice {
				Text(notice)
					.font(DesignToken.Typography.caption)
					.foregroundColor(state.noticeKind == .error ? DesignToken.View.destructive : DesignToken.View.mutedForeground)
			}
		}
		.padding(DesignToken.Spacing.space3)
		.background(DesignToken.View.muted.opacity(0.3))
		.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.lg))
		.overlay(RoundedRectangle(cornerRadius: DesignToken.Radius.lg).stroke(DesignToken.View.border.opacity(0.5), lineWidth: 1))
	}

	private func entry(
		_ title: String,
		enabled: Bool,
		isDestructive: Bool = false,
		id: String,
		perform: @escaping () -> Void
	) -> some View {
		Button(action: perform) {
			Text(title)
				.font(DesignToken.Typography.caption)
				.frame(maxWidth: .infinity, minHeight: 28, alignment: .leading)
				.padding(.horizontal, DesignToken.Spacing.space2)
				.foregroundColor(isDestructive ? DesignToken.View.destructive : DesignToken.View.foreground)
				.background(isDestructive ? Color.clear : DesignToken.View.muted.opacity(0.5))
				.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.md))
				.overlay {
					if isDestructive {
						RoundedRectangle(cornerRadius: DesignToken.Radius.md).stroke(DesignToken.View.destructive, lineWidth: 1)
					}
				}
				.contentShape(Rectangle())
		}
		.buttonStyle(.plain)
		.disabled(!enabled)
		.opacity(enabled ? 1 : 0.5)
		.accessibilityIdentifier("sidebar.\(id)")
	}
}
#endif
