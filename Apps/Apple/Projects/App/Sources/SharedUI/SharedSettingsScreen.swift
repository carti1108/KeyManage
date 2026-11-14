import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 설정 RIB의 뷰. 옛 Swift SettingsView 배치(보안 설정, 백업 및 복원, 마스터 비밀번호, 계정 카드와 시트)를 따른다.
/// 안내·대화상자 문구는 공유 코드(messages 패키지)가 정한다
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
		if let state = screen.state {
			SharedSettingsContent(screen: screen, state: state)
		}
	}
}

private struct SharedSettingsContent: View {
	let screen: SharedSettingsScreen
	let state: SettingsViewState

	var body: some View {
		ScrollView {
			VStack(spacing: 20) {
				if state.modal == SettingsModal.none {
					messages
				}
				infoSection
				autoLockSection
				backupSection
				changePasswordSection
				accountSection
			}
			.padding(20)
		}
		.background(DesignToken.View.background)
		.alert(ExportNotice.shared.TITLE, isPresented: modalBinding(.exportConfirm)) {
			Button(ExportNotice.shared.CANCEL, role: .cancel) { screen.send(SettingsViewActionCancelModal.shared) }
			Button(ExportNotice.shared.CONFIRM) { screen.send(SettingsViewActionConfirmExport.shared) }
				.accessibilityIdentifier("exportConfirmButton")
		} message: {
			Text(ExportNotice.shared.BODY)
		}
		.sheet(isPresented: modalBinding(.restorePassword)) {
			SharedSettingsDialog(
				screen: screen, state: state, title: RestoreDialog.shared.TITLE, message: RestoreDialog.shared.BODY,
				submitTitle: RestoreDialog.shared.SUBMIT, cancelTitle: RestoreDialog.shared.CANCEL,
				progress: RestoreDialog.shared.PROGRESS, canSubmit: state.canSubmitRestore,
				submit: SettingsViewActionSubmitRestore.shared, isDestructive: false
			) {
				secureField(RestoreDialog.shared.PASSWORD_LABEL, state.restorePassword, id: "restorePasswordField") {
					SettingsViewActionEditRestorePassword(value: $0)
				}
			}
		}
		.sheet(isPresented: modalBinding(.changePassword)) {
			SharedSettingsDialog(
				screen: screen, state: state, title: ChangePasswordDialog.shared.TITLE, message: ChangePasswordDialog.shared.BODY,
				submitTitle: ChangePasswordDialog.shared.SUBMIT, cancelTitle: ChangePasswordDialog.shared.CANCEL,
				progress: ChangePasswordDialog.shared.PROGRESS, canSubmit: state.canSubmitChangePassword,
				submit: SettingsViewActionSubmitChangePassword.shared, isDestructive: false
			) {
				secureField(ChangePasswordDialog.shared.CURRENT_LABEL, state.currentPassword, id: "currentPasswordField") {
					SettingsViewActionEditCurrentPassword(value: $0)
				}
				secureField(ChangePasswordDialog.shared.NEW_LABEL, state.newPassword, id: "newPasswordField") {
					SettingsViewActionEditNewPassword(value: $0)
				}
				secureField(ChangePasswordDialog.shared.CONFIRM_LABEL, state.passwordConfirmation, id: "newPasswordConfirmationField") {
					SettingsViewActionEditPasswordConfirmation(value: $0)
				}
			}
		}
		.sheet(isPresented: modalBinding(.deleteAccount)) {
			SharedSettingsDialog(
				screen: screen, state: state, title: DeleteAccountDialog.shared.TITLE, message: DeleteAccountDialog.shared.BODY,
				submitTitle: DeleteAccountDialog.shared.SUBMIT, cancelTitle: DeleteAccountDialog.shared.CANCEL,
				progress: DeleteAccountDialog.shared.PROGRESS, canSubmit: state.canSubmitDeletion,
				submit: SettingsViewActionSubmitDeleteAccount.shared, isDestructive: true
			) {
				secureField(DeleteAccountDialog.shared.PASSWORD_LABEL, state.deletionPassword, id: "deletionPasswordField") {
					SettingsViewActionEditDeletionPassword(value: $0)
				}
				VStack(alignment: .leading, spacing: 6) {
					SharedFieldLabel(DeleteAccountDialog.shared.PHRASE_LABEL)
					// 한글 자모가 나뉘어 들어오면(NFD) 확인 문구와 다르게 보이므로 NFC로 맞춰 보낸다 (SettingsViewState.deletionPhrase)
					SharedTextInput(title: AccountNoticesKt.ACCOUNT_DELETION_PHRASE, value: state.deletionPhrase) {
						screen.send(SettingsViewActionEditDeletionPhrase(value: $0.precomposedStringWithCanonicalMapping))
					}
					.sharedNoAutocapitalization()
					.autocorrectionDisabled()
					.accessibilityIdentifier("deletionPhraseField")
				}
			}
		}
	}

	private func modalBinding(_ modal: SettingsModal) -> Binding<Bool> {
		Binding(
			get: { state.modal == modal },
			set: { if !$0, state.modal == modal { screen.send(SettingsViewActionCancelModal.shared) } }
		)
	}

	private func secureField(
		_ title: String, _ value: String, id: String, action: @escaping (String) -> any SettingsViewAction
	) -> some View {
		VStack(alignment: .leading, spacing: 6) {
			SharedFieldLabel(title)
			SharedTextInput(title: title, value: value, isSecure: true) { screen.send(action($0)) }
				.accessibilityIdentifier(id)
		}
	}

	@ViewBuilder private var messages: some View {
		if let error = state.error {
			dismissible(SharedBanner(kind: .error, text: error)).accessibilityIdentifier("settingsError")
		}
		if let notice = state.notice {
			dismissible(SharedBanner(kind: state.noticeKind == .error ? .error : .info, text: notice))
				.accessibilityIdentifier("settingsNotice")
		}
	}

	private func dismissible(_ banner: some View) -> some View {
		HStack(alignment: .top) {
			banner
			Button { screen.send(SettingsViewActionDismissMessage.shared) } label: {
				Image(systemName: "xmark").foregroundStyle(DesignToken.View.mutedForeground)
			}
			.accessibilityLabel("안내 닫기")
		}
	}

	private var infoSection: some View {
		section("계정 정보") {
			LabeledContent("계정", value: state.email).accessibilityIdentifier("settingsEmail")
			if !state.serverAddress.isEmpty {
				LabeledContent("서버 주소", value: state.serverAddress)
			}
			LabeledContent("동기화", value: state.syncOffline ? "오프라인" : "최신 상태 (동기화 완료)")
				.accessibilityIdentifier("settingsSyncStatus")
		}
	}

	private var autoLockSection: some View {
		section("보안 설정") {
			HStack {
				Text("자동 잠금 시간").font(.subheadline).foregroundStyle(DesignToken.View.mutedForeground)
				Spacer()
				Menu {
					ForEach(Array(AutoLockChoicesKt.autoLockChoices(state: state).enumerated()), id: \.offset) { _, choice in
						Button {
							screen.send(choice.action)
						} label: {
							if choice.isSelected { Label(choice.label, systemImage: "checkmark") } else { Text(choice.label) }
						}
						.accessibilityIdentifier(choice.id)
					}
				} label: {
					Text(selectedAutoLockLabel).font(.subheadline.bold())
				}
				.accessibilityIdentifier("autoLockPicker")
			}
			KMButton("지금 잠그기", variant: .outline, icon: DesignToken.Icon.vaultLock) {
				screen.send(SettingsViewActionRequestLock.shared)
			}
			.accessibilityIdentifier("settingsLockButton")
		}
	}

	private var selectedAutoLockLabel: String {
		AutoLockChoicesKt.autoLockChoices(state: state).first(where: { $0.isSelected })?.label ?? ""
	}

	private var backupSection: some View {
		section("백업 및 복원") {
			Text(ExportNotice.shared.BODY)
				.font(.footnote)
				.foregroundStyle(DesignToken.View.mutedForeground)
				.fixedSize(horizontal: false, vertical: true)
			KMButton(ExportNotice.shared.TITLE, variant: .outline, isDisabled: !state.canExportBackup) {
				screen.send(SettingsViewActionStartExport.shared)
			}
			.accessibilityIdentifier("exportBackupButton")
			Divider()
			Text(RestoreDialog.shared.BODY)
				.font(.footnote)
				.foregroundStyle(DesignToken.View.mutedForeground)
				.fixedSize(horizontal: false, vertical: true)
			KMButton(RestoreDialog.shared.TITLE, variant: .outline, isDisabled: !state.canRestoreBackup) {
				screen.send(SettingsViewActionStartRestore.shared)
			}
			.accessibilityIdentifier("restoreBackupButton")
			if state.inProgress && state.modal == SettingsModal.none {
				ProgressView().frame(maxWidth: .infinity).accessibilityIdentifier("settingsProgress")
			}
		}
	}

	private var changePasswordSection: some View {
		section("마스터 비밀번호") {
			Text(ChangePasswordDialog.shared.BODY)
				.font(.footnote)
				.foregroundStyle(DesignToken.View.mutedForeground)
				.fixedSize(horizontal: false, vertical: true)
			KMButton(ChangePasswordDialog.shared.TITLE, variant: .outline, isDisabled: !state.canStartChangePassword) {
				screen.send(SettingsViewActionStartChangePassword.shared)
			}
			.accessibilityIdentifier("changePasswordButton")
		}
	}

	private var accountSection: some View {
		section("계정") {
			HStack(spacing: 12) {
				KMButton("로그아웃", variant: .outline) { screen.send(SettingsViewActionRequestLogout.shared) }
					.accessibilityIdentifier("settingsLogoutButton")
				Spacer()
				KMButton(DeleteAccountDialog.shared.TITLE, variant: .destructive, isDisabled: !state.canStartDeleteAccount) {
					screen.send(SettingsViewActionStartDeleteAccount.shared)
				}
				.accessibilityIdentifier("deleteAccountButton")
			}
		}
	}

	private func section(_ title: String, @ViewBuilder content: @escaping () -> some View) -> some View {
		KMCard {
			VStack(alignment: .leading, spacing: 12) {
				Text(title).font(.headline).foregroundStyle(DesignToken.View.foreground)
				content()
			}
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding(16)
		}
	}
}

/// 복원·비밀번호 변경·계정 삭제 시트. 오류는 시트 안에, 진행 중에는 진행 문구를 보이고 닫기를 막는다
private struct SharedSettingsDialog<Fields: View>: View {
	let screen: SharedSettingsScreen
	let state: SettingsViewState
	let title: String
	let message: String
	let submitTitle: String
	let cancelTitle: String
	let progress: String
	let canSubmit: Bool
	let submit: any SettingsViewAction
	let isDestructive: Bool
	@ViewBuilder let fields: () -> Fields

	var body: some View {
		NavigationStack {
			ScrollView {
				VStack(alignment: .leading, spacing: 16) {
					Text(message)
						.font(.footnote)
						.foregroundStyle(isDestructive ? DesignToken.View.destructive : DesignToken.View.mutedForeground)
						.fixedSize(horizontal: false, vertical: true)
					fields()
					if let error = state.error {
						SharedBanner(kind: .error, text: error).accessibilityIdentifier("settingsDialogError")
					}
					if state.inProgress {
						HStack(spacing: 8) {
							ProgressView()
							Text(progress).font(.footnote)
						}
					}
					KMButton(submitTitle, variant: isDestructive ? .destructive : .primary, isLoading: state.inProgress, isDisabled: !canSubmit) {
						screen.send(submit)
					}
					.accessibilityIdentifier("settingsDialogSubmitButton")
				}
				.padding(20)
			}
			.navigationTitle(title)
			.sharedInlineNavigationTitle()
			.toolbar {
				ToolbarItem(placement: .cancellationAction) {
					Button(cancelTitle) { screen.send(SettingsViewActionCancelModal.shared) }
						.disabled(state.inProgress)
				}
			}
		}
		.interactiveDismissDisabled(state.inProgress)
	}
}
