import SwiftUI
import KeyManageDomain
import KeyManageDesignSystem

struct SettingsView: View {
	@ObservedObject var viewModel: SettingsViewModel
	@State private var restorePassword = ""
	@State private var selectedFileData: Data?
	@State private var selectedFileName: String?
	@State private var isFileImporterPresented = false

	var body: some View {
		Form {
			accountSection
			securitySection
			backupSection
			sessionSection
		}
		#if os(macOS)
		.formStyle(.grouped)
		#endif
		.overlay(alignment: .bottom) {
			if let toast = viewModel.state.toastMessage {
				toastOverlay(toast)
			}
		}
		.sheet(isPresented: $viewModel.isRestoreModalPresented) {
			restoreModalView
		}
		.sheet(isPresented: $viewModel.isChangePasswordPresented) {
			changePasswordModalView
		}
		.sheet(isPresented: $viewModel.isDeleteAccountPresented) {
			deleteAccountModalView
				.interactiveDismissDisabled(viewModel.state.isDeletingAccount)
		}
		.alert(viewModel.state.hasPendingSync ? VaultSyncStrings.pendingLogoutConfirmTitle : "로그아웃", isPresented: $viewModel.isLogoutConfirmPresented) {
			Button(VaultSyncStrings.cancelButton, role: .cancel) {}
			Button(VaultSyncStrings.logoutButton, role: .destructive) {
				viewModel.send(.requestLogout)
			}
		} message: {
			Text(viewModel.state.hasPendingSync ? VaultSyncStrings.pendingLogoutConfirmMessage : "로그아웃하시겠습니까? 세션이 종료되고 기기에서 민감 키가 소거됩니다.")
		}
		.fileImporter(
			isPresented: $isFileImporterPresented,
			allowedContentTypes: [.json],
			allowsMultipleSelection: false
		) { result in
			handleFileImport(result)
		}
	}

	// MARK: - Sections

	private var accountSection: some View {
		Section("계정") {
			valueRow("이메일", value: viewModel.state.email ?? "-")
		}
	}

	private var securitySection: some View {
		Section("보안") {
			Picker("자동 잠금", selection: Binding(
				get: { viewModel.state.selectedTimeout },
				set: { viewModel.send(.selectAutoLockTimeout($0)) }
			)) {
				ForEach(AutoLockTimeout.allCases, id: \.self) { timeout in
					Text(timeoutTitle(for: timeout)).tag(timeout)
				}
			}
			valueRow("클립보드 자동 삭제", value: "\(Int(ClipboardManager.defaultAutoBurnInterval))초")
			valueRow("암호화 구조", value: VaultCipher.supportedCipher)
		}
	}

	private var backupSection: some View {
		Section {
			settingsRow(KeyManageBackupStrings.exportTitle) {
				viewModel.send(.requestExportBackup)
			}
			settingsRow(KeyManageBackupStrings.restoreTitle) {
				viewModel.send(.setRestoreModalPresented(true))
			}
		} header: {
			Text("백업")
		} footer: {
			Text(KeyManageBackupStrings.exportDescription)
		}
	}

	private var sessionSection: some View {
		Section {
			settingsRow("마스터 비밀번호 변경") {
				viewModel.send(.setChangePasswordPresented(true))
			}
			Button("로그아웃") {
				viewModel.send(.showPendingLogoutConfirmation)
			}
			.foregroundColor(DesignToken.View.foreground)
			Button(AccountDeletionStrings.preNoticeTitle, role: .destructive) {
				viewModel.send(.startAccountDeletion)
			}
			.foregroundColor(DesignToken.View.destructive)
		} header: {
			Text("세션 제어")
		} footer: {
			Text(MasterPasswordChangeStrings.noticeBody)
		}
	}

	private func valueRow(_ title: String, value: String) -> some View {
		HStack {
			Text(title)
				.foregroundColor(DesignToken.View.foreground)
			Spacer()
			Text(value)
				.foregroundColor(DesignToken.View.mutedForeground)
		}
		.accessibilityElement(children: .combine)
	}

	/// 누르면 다음 화면이나 시트로 넘어가는 행. 오른쪽에 쉐브론을 붙인다.
	private func settingsRow(_ title: String, action: @escaping () -> Void) -> some View {
		Button(action: action) {
			HStack {
				Text(title)
					.foregroundColor(DesignToken.View.foreground)
				Spacer()
				Image(systemName: "chevron.right")
					.font(DesignToken.Typography.caption.weight(.semibold))
					.foregroundColor(DesignToken.View.mutedForeground)
					.accessibilityHidden(true)
			}
			.contentShape(Rectangle())
		}
		.buttonStyle(.plain)
	}

	// MARK: - Change Password Modal

	private var changePasswordModalView: some View {
		VStack(spacing: DesignToken.Spacing.space5) {
			Text(MasterPasswordChangeStrings.noticeTitle)
				.font(DesignToken.Typography.title)
				.bold()
				.foregroundColor(DesignToken.View.foreground)

			changePasswordWarningCard

			changePasswordFieldsView

			changePasswordButtonsView
		}
		.padding(DesignToken.Spacing.space6)
		.frame(minWidth: 420)
	}

	private var changePasswordWarningCard: some View {
		KMCard {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
				Text("주의 사항")
					.font(DesignToken.Typography.caption)
					.bold()
					.foregroundColor(DesignToken.View.foreground)

				Text(MasterPasswordChangeStrings.noticeBody)
					.font(DesignToken.Typography.caption)
					.foregroundColor(DesignToken.View.mutedForeground)
					.fixedSize(horizontal: false, vertical: true)
			}
			.padding(DesignToken.Spacing.space3)
		}
	}

	private var changePasswordFieldsView: some View {
		VStack(spacing: DesignToken.Spacing.space3) {
			KMPasswordField(
				text: $viewModel.currentPasswordInput,
				placeholder: MasterPasswordChangeStrings.currentPasswordPlaceholder,
				showCopyAction: false
			)

			KMPasswordField(
				text: $viewModel.newPasswordInput,
				placeholder: MasterPasswordChangeStrings.newPasswordPlaceholder,
				showCopyAction: false
			)

			KMPasswordField(
				text: $viewModel.confirmPasswordInput,
				placeholder: MasterPasswordChangeStrings.confirmPasswordPlaceholder,
				showCopyAction: false
			)
		}
	}

	private var isChangePasswordButtonDisabled: Bool {
		viewModel.state.currentPasswordInput.isEmpty ||
		viewModel.state.newPasswordInput.isEmpty ||
		viewModel.state.confirmPasswordInput.isEmpty
	}

	private var changePasswordButtonsView: some View {
		HStack(spacing: DesignToken.Spacing.space3) {
			KMButton(
				"취소",
				variant: .outline,
				action: {
					viewModel.send(.setChangePasswordPresented(false))
				}
			)

			KMButton(
				MasterPasswordChangeStrings.changeButton,
				variant: .primary,
				isDisabled: isChangePasswordButtonDisabled,
				action: { viewModel.requestChangePassword() }
			)
		}
	}

	// MARK: - Restore Modal

	private var restoreModalView: some View {
		VStack(spacing: DesignToken.Spacing.space5) {
			Text(KeyManageBackupStrings.restoreTitle)
				.font(DesignToken.Typography.title)
				.bold()
				.foregroundColor(DesignToken.View.foreground)

			Text(KeyManageBackupStrings.restoreDescription)
				.font(DesignToken.Typography.caption)
				.foregroundColor(DesignToken.View.mutedForeground)
				.multilineTextAlignment(.center)
				.padding(.horizontal)

			if let fileName = selectedFileName {
				Text("선택된 파일: \(fileName)")
					.font(DesignToken.Typography.caption)
					.foregroundColor(DesignToken.View.accent)
			} else {
				KMButton(
					"백업 파일 선택 (.json)",
					variant: .outline,
					action: { isFileImporterPresented = true }
				)
			}

			SecureField("백업 마스터 비밀번호 입력", text: $restorePassword)
				.textFieldStyle(.roundedBorder)
				.padding(.horizontal)

			HStack(spacing: DesignToken.Spacing.space3) {
				KMButton(
					"취소",
					variant: .outline,
					action: {
						viewModel.send(.setRestoreModalPresented(false))
						resetRestoreState()
					}
				)

				KMButton(
					KeyManageBackupStrings.restoreButton,
					variant: .primary,
					isDisabled: selectedFileData == nil || restorePassword.isEmpty,
					action: {
						guard let data = selectedFileData, !restorePassword.isEmpty else { return }
						viewModel.send(.requestRestoreBackup(fileData: data, password: restorePassword))
						viewModel.send(.setRestoreModalPresented(false))
						resetRestoreState()
					}
				)
			}
			.padding(.horizontal)
		}
		.padding(DesignToken.Spacing.space6)
		.frame(minWidth: 360, minHeight: 320)
	}

	private func resetRestoreState() {
		restorePassword = ""
		selectedFileData = nil
		selectedFileName = nil
	}

	private func handleFileImport(_ result: Result<[URL], Error>) {
		switch result {
		case .success(let urls):
			guard let url = urls.first else { return }
			let gotAccess = url.startAccessingSecurityScopedResource()
			defer {
				if gotAccess { url.stopAccessingSecurityScopedResource() }
			}
			do {
				let data = try Data(contentsOf: url)
				selectedFileData = data
				selectedFileName = url.lastPathComponent
			} catch {
				viewModel.send(.showToast("파일 읽기 실패: \(error.localizedDescription)"))
			}
		case .failure(let error):
			viewModel.send(.showToast("파일 선택 실패: \(error.localizedDescription)"))
		}
	}

	private func timeoutTitle(for timeout: AutoLockTimeout) -> String {
		switch timeout {
		case .immediate:
			return "즉시 (0초)"
		case .oneMinute:
			return "1분"
		case .fiveMinutes:
			return "5분"
		case .fifteenMinutes:
			return "15분"
		case .oneHour:
			return "1시간"
		}
	}

	private func toastOverlay(_ message: String) -> some View {
		Text(message)
			.font(DesignToken.Typography.caption)
			.foregroundColor(DesignToken.View.primaryForeground)
			.padding(.horizontal, DesignToken.Spacing.space4)
			.padding(.vertical, DesignToken.Spacing.space3)
			.background(DesignToken.View.primary)
			.cornerRadius(DesignToken.Radius.md)
			.padding(.bottom, DesignToken.Spacing.space6)
			.transition(.opacity)
	}

	// MARK: - Delete Account Modal (SEC-005)

	private var deleteAccountModalView: some View {
		ScrollView {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				Text(AccountDeletionStrings.preNoticeTitle)
					.font(DesignToken.Typography.title.weight(.bold))
					.foregroundColor(DesignToken.View.destructive)
					.accessibilityAddTraits(.isHeader)

				Text(AccountDeletionStrings.preNoticeBody)
					.font(DesignToken.Typography.label)
					.foregroundColor(DesignToken.View.mutedForeground)
					.fixedSize(horizontal: false, vertical: true)

				if let error = viewModel.state.deleteAccountErrorMessage {
					Text(error)
						.font(DesignToken.Typography.caption)
						.foregroundColor(DesignToken.View.destructive)
				}

				fieldLabel("마스터 비밀번호")
				KMPasswordField(
					text: $viewModel.deleteAccountPasswordInput,
					placeholder: "마스터 비밀번호",
					showCopyAction: false
				)

				fieldLabel(AccountDeletionStrings.confirmationPrompt)
				TextField(AccountDeletionStrings.confirmationPhrase, text: $viewModel.deleteAccountConfirmationInput)
					.textFieldStyle(.plain)
					#if os(iOS)
					.textInputAutocapitalization(.never)
					#endif
					.disableAutocorrection(true)
					.kmFieldContainer()

				VStack(spacing: DesignToken.Spacing.space2) {
					KMButton(
						AccountDeletionStrings.deleteButtonTitle,
						variant: .destructive,
						size: .lg,
						isLoading: viewModel.state.isDeletingAccount,
						isDisabled: !viewModel.canExecuteDeleteAccount,
						isFullWidth: true
					) {
						viewModel.executeDeleteAccount()
					}
					KMButton(
						AccountDeletionStrings.cancelButtonTitle,
						variant: .secondary,
						size: .lg,
						isDisabled: viewModel.state.isDeletingAccount,
						isFullWidth: true
					) {
						viewModel.send(.cancelAccountDeletion)
					}
				}
				.padding(.top, DesignToken.Spacing.space2)
			}
			.padding(DesignToken.Spacing.space5)
		}
		#if os(iOS)
		.modifier(SheetGrabber())
		#else
		.frame(minWidth: 460, minHeight: 440)
		#endif
	}

	private func fieldLabel(_ text: String) -> some View {
		Text(text)
			.font(DesignToken.Typography.caption.weight(.medium))
			.foregroundColor(DesignToken.View.mutedForeground)
	}

	@ViewBuilder
	private func modalNavigationContainer<Content: View>(@ViewBuilder content: () -> Content) -> some View {
		if #available(iOS 16.0, macOS 13.0, *) {
			NavigationStack {
				content()
			}
		} else {
			NavigationView {
				content()
			}
			#if os(iOS)
			.navigationViewStyle(.stack)
			#endif
		}
	}
}

/// iOS 16 이상에서 시트 위에 그래버를 보인다 (iPhone 목업 계정 삭제 시트)
private struct SheetGrabber: ViewModifier {
	func body(content: Content) -> some View {
		if #available(iOS 16.0, *) {
			content
				.presentationDetents([.large])
				.presentationDragIndicator(.visible)
		} else {
			content
		}
	}
}
