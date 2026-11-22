import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 설정 화면의 SwiftUI 뷰(ComposeSettingsView의 짝). 내보내기·복원·비밀번호 변경·계정 삭제는 상태의 `modal`에 따라 띄운다
@MainActor
@Observable
public final class SwiftUISettingsView: NSObject, @preconcurrency SettingsViewContract, SwiftUIRenderable {
	@ObservationIgnored public var listener: (any SettingsPresentableListener)?
	public private(set) var state: SettingsViewState?

	override public init() {}

	public func render(settingsState state: SettingsViewState) {
		self.state = state
	}

	func send(_ action: SettingsViewAction) {
		listener?.handle(settingsAction: action)
	}

	public func makeView() -> AnyView {
		AnyView(SettingsScreen(view: self))
	}

	#if os(macOS)
	/// 사이드바 아래 상자(자동 잠금·백업·비밀번호 변경·계정 삭제). 확인 창과 시트는 늘 보이는 이 상자가 띄운다
	func makeSidebarView() -> AnyView {
		AnyView(SidebarSettingsBox(view: self))
	}
	#endif
}

/// 설정 화면(목업 7단계)
private struct SettingsScreen: View {
	let view: SwiftUISettingsView

	var body: some View {
		if let state = view.state {
			content(state)
		}
	}

	@ViewBuilder
	private func content(_ state: SettingsViewState) -> some View {
		#if os(macOS)
		// 확인 창과 시트는 사이드바 상자가 띄운다. 두 화면이 함께 띄우면 같은 창이 두 번 뜬다
		layout(state)
		#else
		settingsModals(layout(state).navigationTitle("설정"), state: state, view: view)
		#endif
	}
}

/// 내보내기 확인 창과 비밀번호를 받는 시트. iPhone은 설정 화면이, macOS는 사이드바 상자가 붙인다
@MainActor
func settingsModals(_ content: some View, state: SettingsViewState, view: SwiftUISettingsView) -> some View {
	let cancel = { view.send(SettingsViewActionCancelModal.shared) }
	let isSheet = state.modal == .restorePassword || state.modal == .changePassword || state.modal == .deleteAccount
	return content
		.alert(ExportNotice.shared.TITLE, isPresented: sharedStateBinding(state.modal == .exportConfirm) { cancel() }) {
			Button(ExportNotice.shared.CONFIRM) { view.send(SettingsViewActionConfirmExport.shared) }
			Button(ExportNotice.shared.CANCEL, role: .cancel) { cancel() }
		} message: {
			Text(ExportNotice.shared.BODY)
		}
		.sheet(isPresented: sharedStateBinding(isSheet) { cancel() }) {
			SettingsModalSheet(state: state, send: view.send)
				.interactiveDismissDisabled(state.inProgress)
		}
}

extension SettingsScreen {
	/// iPhone은 묶음 목록, macOS는 웹 SettingsRibScreen과 같은 카드 구역
	@ViewBuilder
	private func layout(_ state: SettingsViewState) -> some View {
		#if os(macOS)
		DesktopSettingsBody(state: state, send: view.send)
		#else
		listBody(state)
		#endif
	}

	private func listBody(_ state: SettingsViewState) -> some View {
		List {
			// 결과 안내·오류는 맨 위에 둔다. 목록 아래에 두면 화면 밖이라 보이지 않는다. 모달이 열려 있으면 오류는 모달 안에 보인다
			if state.modal == .none, state.error != nil || state.notice != nil {
				Section {
					if let error = state.error {
						message(error, isError: true)
					}
					if let notice = state.notice {
						message(notice, isError: state.noticeKind == .error)
					}
				}
			}
			Section("계정") {
				row("이메일", state.email)
				if !state.serverAddress.isEmpty {
					row("서버", state.serverAddress)
				}
				row("동기화", state.syncOffline ? "오프라인" : "최신 상태 (동기화 완료)")
			}
			Section("보안") {
				Menu {
					ForEach(Array(state.autoLockChoices.enumerated()), id: \.offset) { _, choice in
						Button {
							view.send(SettingsViewActionSelectAutoLock(timeout: choice.timeout))
						} label: {
							if choice.isSelected { Label(choice.label, systemImage: "checkmark") } else { Text(choice.label) }
						}
					}
				} label: {
					row("자동 잠금", state.autoLockTimeoutLabel).foregroundStyle(DesignToken.View.foreground)
				}
				.accessibilityIdentifier("settings.autoLock")
				if state.showsBiometricToggle {
					Toggle(BiometricUnlockMessages.shared.ACTION, isOn: Binding(
						get: { state.biometricEnabled },
						set: { view.send(SettingsViewActionSetBiometricUnlock(enabled: $0)) }
					))
					.disabled(state.inProgress)
					.accessibilityIdentifier("settings.biometric")
					if let error = state.biometricError {
						message(error, isError: true)
					}
				}
				row("클립보드 자동 삭제", "30초")
				row("암호화 구조", "AES-256-GCM")
			}
			Section("백업") {
				action("백업 내보내기", enabled: state.canExportBackup, id: "export") { view.send(SettingsViewActionStartExport.shared) }
				action("백업에서 복원", enabled: state.canRestoreBackup, id: "restore") { view.send(SettingsViewActionStartRestore.shared) }
			}
			Section("세션 제어") {
				action("잠그기", id: "lock") { view.send(SettingsViewActionRequestLock.shared) }
				action("마스터 비밀번호 변경", enabled: state.canStartChangePassword, id: "changePassword") {
					view.send(SettingsViewActionStartChangePassword.shared)
				}
				action("로그아웃", id: "logout") { view.send(SettingsViewActionRequestLogout.shared) }
				action("계정 삭제", enabled: state.canStartDeleteAccount, isDestructive: true, id: "deleteAccount") {
					view.send(SettingsViewActionStartDeleteAccount.shared)
				}
			}
		}
	}

	private func row(_ label: String, _ value: String) -> some View {
		HStack {
			Text(label)
			Spacer()
			Text(value).foregroundColor(DesignToken.View.mutedForeground)
		}
		.accessibilityElement(children: .combine)
	}

	private func action(
		_ label: String,
		enabled: Bool = true,
		isDestructive: Bool = false,
		id: String,
		perform: @escaping () -> Void
	) -> some View {
		Button(label, role: isDestructive ? .destructive : nil, action: perform)
			.disabled(!enabled)
			.accessibilityIdentifier("settings.\(id)")
	}

	private func message(_ text: String, isError: Bool) -> some View {
		Text(text)
			.font(DesignToken.Typography.caption)
			.foregroundColor(isError ? DesignToken.View.destructive : DesignToken.View.mutedForeground)
			.accessibilityIdentifier("settings.message")
	}
}

/// 비밀번호를 받는 대화상자 셋(복원, 비밀번호 변경, 계정 삭제)
private struct SettingsModalSheet: View {
	let state: SettingsViewState
	let send: (SettingsViewAction) -> Void

	var body: some View {
		NavigationStack {
			Form {
				Section {
					Text(body(for: state.modal))
						.font(DesignToken.Typography.caption)
						.foregroundColor(DesignToken.View.mutedForeground)
					fields
				}
				if let error = state.error {
					Text(error).font(DesignToken.Typography.caption).foregroundColor(DesignToken.View.destructive)
				}
				if state.inProgress {
					HStack {
						ProgressView()
						Text(progress(for: state.modal))
					}
				}
			}
			.navigationTitle(title(for: state.modal))
			.inlineNavigationTitle()
			.toolbar {
				ToolbarItem(placement: .cancellationAction) {
					Button(cancelTitle(for: state.modal)) { send(SettingsViewActionCancelModal.shared) }
						.disabled(state.inProgress)
				}
				ToolbarItem(placement: .confirmationAction) {
					Button(submitTitle(for: state.modal), role: state.modal == .deleteAccount ? .destructive : nil) { submit() }
						.disabled(!canSubmit)
						.accessibilityIdentifier("settings.modal.submit")
				}
			}
		}
	}

	@ViewBuilder
	private var fields: some View {
		switch state.modal {
		case .restorePassword:
			secret(RestoreDialog.shared.PASSWORD_LABEL, state.restorePassword) { SettingsViewActionEditRestorePassword(value: $0) }
		case .changePassword:
			secret(ChangePasswordDialog.shared.CURRENT_LABEL, state.currentPassword) { SettingsViewActionEditCurrentPassword(value: $0) }
			secret(ChangePasswordDialog.shared.NEW_LABEL, state.newPassword) { SettingsViewActionEditNewPassword(value: $0) }
			secret(ChangePasswordDialog.shared.CONFIRM_LABEL, state.passwordConfirmation) { SettingsViewActionEditPasswordConfirmation(value: $0) }
		case .deleteAccount:
			secret(DeleteAccountDialog.shared.PASSWORD_LABEL, state.deletionPassword) { SettingsViewActionEditDeletionPassword(value: $0) }
			Text(DeleteAccountDialog.shared.PHRASE_LABEL).font(DesignToken.Typography.caption)
			// 한글 자모가 나뉘어 들어오면(NFD) 공유 상태와 비교되지 않으므로 NFC로 맞춰 보낸다
			TextField("확인 문구", text: Binding(
				get: { state.deletionPhrase },
				set: { send(SettingsViewActionEditDeletionPhrase(value: $0.precomposedStringWithCanonicalMapping)) }
			))
			.disabled(state.inProgress)
			.accessibilityIdentifier("settings.modal.phrase")
		default:
			EmptyView()
		}
	}

	private func secret(_ label: String, _ value: String, action: @escaping (String) -> SettingsViewAction) -> some View {
		SecureField(label, text: Binding(get: { value }, set: { send(action($0)) }))
			.disabled(state.inProgress)
			.accessibilityIdentifier("settings.modal.\(label)")
	}

	private var canSubmit: Bool {
		switch state.modal {
		case .restorePassword: state.canSubmitRestore
		case .changePassword: state.canSubmitChangePassword
		case .deleteAccount: state.canSubmitDeletion
		default: false
		}
	}

	private func submit() {
		switch state.modal {
		case .restorePassword: send(SettingsViewActionSubmitRestore.shared)
		case .changePassword: send(SettingsViewActionSubmitChangePassword.shared)
		case .deleteAccount: send(SettingsViewActionSubmitDeleteAccount.shared)
		default: break
		}
	}

	private func title(for modal: SettingsModal) -> String {
		switch modal {
		case .restorePassword: RestoreDialog.shared.TITLE
		case .changePassword: ChangePasswordDialog.shared.TITLE
		default: DeleteAccountDialog.shared.TITLE
		}
	}

	private func body(for modal: SettingsModal) -> String {
		switch modal {
		case .restorePassword: RestoreDialog.shared.BODY
		case .changePassword: ChangePasswordDialog.shared.BODY
		default: DeleteAccountDialog.shared.BODY
		}
	}

	private func progress(for modal: SettingsModal) -> String {
		switch modal {
		case .restorePassword: RestoreDialog.shared.PROGRESS
		case .changePassword: ChangePasswordDialog.shared.PROGRESS
		default: DeleteAccountDialog.shared.PROGRESS
		}
	}

	private func submitTitle(for modal: SettingsModal) -> String {
		switch modal {
		case .restorePassword: RestoreDialog.shared.SUBMIT
		case .changePassword: ChangePasswordDialog.shared.SUBMIT
		default: DeleteAccountDialog.shared.SUBMIT
		}
	}

	private func cancelTitle(for modal: SettingsModal) -> String {
		switch modal {
		case .restorePassword: RestoreDialog.shared.CANCEL
		case .changePassword: ChangePasswordDialog.shared.CANCEL
		default: DeleteAccountDialog.shared.CANCEL
		}
	}
}
