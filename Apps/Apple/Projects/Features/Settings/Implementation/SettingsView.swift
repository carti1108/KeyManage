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
		ScrollView {
			VStack(spacing: DesignToken.Spacing.space6) {
				// 1. 자동 잠금 섹션
				autoLockSection

				// 2. 백업 및 복원 섹션
				backupSection

				// 3. 마스터 비밀번호 변경 섹션
				changePasswordSection

				// 4. 계정 섹션 (로그아웃)
				accountSection
			}
			.padding(DesignToken.Spacing.space6)
		}
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

	private var autoLockSection: some View {
		KMCard {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				Text("보안 설정")
					.font(.headline)
					.foregroundColor(DesignToken.View.foreground)

				VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
					Text("자동 잠금 시간")
						.font(.subheadline)
						.foregroundColor(DesignToken.View.mutedForeground)

					Picker("자동 잠금 시간", selection: Binding(
						get: { viewModel.state.selectedTimeout },
						set: { viewModel.send(.selectAutoLockTimeout($0)) }
					)) {
						ForEach(AutoLockTimeout.allCases, id: \.self) { timeout in
							Text(timeoutTitle(for: timeout)).tag(timeout)
						}
					}
					.pickerStyle(.menu)
				}
			}
			.padding(DesignToken.Spacing.space4)
		}
	}

	private var backupSection: some View {
		KMCard {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				Text("백업 및 복원")
					.font(.headline)
					.foregroundColor(DesignToken.View.foreground)

				VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
					Text(KeyManageBackupStrings.exportTitle)
						.font(.subheadline)
						.foregroundColor(DesignToken.View.foreground)

					Text(KeyManageBackupStrings.exportDescription)
						.font(.footnote)
						.foregroundColor(DesignToken.View.mutedForeground)
						.fixedSize(horizontal: false, vertical: true)

					KMButton(
						KeyManageBackupStrings.exportButton,
						variant: .outline,
						action: { viewModel.send(.requestExportBackup) }
					)
				}

				Divider()

				VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
					Text(KeyManageBackupStrings.restoreTitle)
						.font(.subheadline)
						.foregroundColor(DesignToken.View.foreground)

					Text(KeyManageBackupStrings.restoreDescription)
						.font(.footnote)
						.foregroundColor(DesignToken.View.mutedForeground)
						.fixedSize(horizontal: false, vertical: true)

					KMButton(
						KeyManageBackupStrings.restoreButton,
						variant: .outline,
						action: { viewModel.send(.setRestoreModalPresented(true)) }
					)
				}
			}
			.padding(DesignToken.Spacing.space4)
		}
	}

	private var changePasswordSection: some View {
		KMCard {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				Text("마스터 비밀번호")
					.font(.headline)
					.foregroundColor(DesignToken.View.foreground)

				VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
					Text(MasterPasswordChangeStrings.noticeTitle)
						.font(.subheadline)
						.foregroundColor(DesignToken.View.foreground)

					Text(MasterPasswordChangeStrings.noticeBody)
						.font(.footnote)
						.foregroundColor(DesignToken.View.mutedForeground)
						.fixedSize(horizontal: false, vertical: true)

					KMButton(
						"비밀번호 변경",
						variant: .outline,
						action: { viewModel.send(.setChangePasswordPresented(true)) }
					)
				}
			}
			.padding(DesignToken.Spacing.space4)
		}
	}

	private var accountSection: some View {
		KMCard {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				Text("계정")
					.font(.headline)
					.foregroundColor(DesignToken.View.foreground)

				HStack(spacing: DesignToken.Spacing.space3) {
					KMButton(
						"로그아웃",
						variant: .outline,
						action: { viewModel.send(.showPendingLogoutConfirmation) }
					)

					Spacer()

					KMButton(
						AccountDeletionStrings.preNoticeTitle,
						variant: .destructive,
						action: { viewModel.send(.startAccountDeletion) }
					)
				}
			}
			.padding(DesignToken.Spacing.space4)
		}
	}

	// MARK: - Change Password Modal

	private var changePasswordModalView: some View {
		VStack(spacing: DesignToken.Spacing.space5) {
			Text(MasterPasswordChangeStrings.noticeTitle)
				.font(.title2)
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
					.font(.caption)
					.bold()
					.foregroundColor(DesignToken.View.foreground)

				Text(MasterPasswordChangeStrings.noticeBody)
					.font(.footnote)
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
				.font(.title2)
				.bold()
				.foregroundColor(DesignToken.View.foreground)

			Text(KeyManageBackupStrings.restoreDescription)
				.font(.footnote)
				.foregroundColor(DesignToken.View.mutedForeground)
				.multilineTextAlignment(.center)
				.padding(.horizontal)

			if let fileName = selectedFileName {
				Text("선택된 파일: \(fileName)")
					.font(.caption)
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
			.font(.footnote)
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
		modalNavigationContainer {
			ScrollView {
				VStack(alignment: .leading, spacing: DesignToken.Spacing.space5) {
					// 경고 카드
					KMCard {
						VStack(alignment: .leading, spacing: DesignToken.Spacing.space3) {
							HStack(spacing: DesignToken.Spacing.space2) {
								Image(systemName: "exclamationmark.triangle.fill")
									.foregroundColor(DesignToken.View.destructive)
								Text("되돌릴 수 없습니다")
									.font(.headline)
									.foregroundColor(DesignToken.View.destructive)
							}

							Text(AccountDeletionStrings.preNoticeBody)
								.font(.footnote)
								.foregroundColor(DesignToken.View.foreground)
								.fixedSize(horizontal: false, vertical: true)
						}
						.padding(DesignToken.Spacing.space4)
					}

					if let error = viewModel.state.deleteAccountErrorMessage {
						Text(error)
							.font(.footnote)
							.foregroundColor(DesignToken.View.destructive)
					}

					// 마스터 비밀번호 입력
					VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
						Text("마스터 비밀번호")
							.font(.subheadline.bold())
							.foregroundColor(DesignToken.View.foreground)

						KMPasswordField(
							text: $viewModel.deleteAccountPasswordInput,
							placeholder: "현재 마스터 비밀번호 입력",
							showCopyAction: false
						)
					}

					// 확인 문구 입력 ('계정 삭제')
					VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
						Text("확인 문구 입력")
							.font(.subheadline.bold())
							.foregroundColor(DesignToken.View.foreground)

						TextField(AccountDeletionStrings.confirmationPrompt, text: $viewModel.deleteAccountConfirmationInput)
							.textFieldStyle(.plain)
							#if os(iOS)
							.textInputAutocapitalization(.never)
							#endif
							.disableAutocorrection(true)
							.padding(DesignToken.Spacing.space3)
							.background(DesignToken.View.card)
							.cornerRadius(DesignToken.Radius.sm)
							.overlay(
								RoundedRectangle(cornerRadius: DesignToken.Radius.sm)
									.stroke(DesignToken.View.border, lineWidth: 1)
							)

						Text("진행하려면 '\(AccountDeletionStrings.confirmationPhrase)'를 정확히 입력해 주세요.")
							.font(.caption)
							.foregroundColor(DesignToken.View.mutedForeground)
					}
				}
				.padding(DesignToken.Spacing.space5)
			}
			.navigationTitle(AccountDeletionStrings.preNoticeTitle)
			#if os(iOS)
			.navigationBarTitleDisplayMode(.inline)
			#endif
			.toolbar {
				ToolbarItem(placement: .cancellationAction) {
					Button(AccountDeletionStrings.cancelButtonTitle) {
						viewModel.send(.cancelAccountDeletion)
					}
					.disabled(viewModel.state.isDeletingAccount)
				}
				ToolbarItem(placement: .confirmationAction) {
					Button(role: .destructive) {
						viewModel.executeDeleteAccount()
					} label: {
						if viewModel.state.isDeletingAccount {
							ProgressView()
								.progressViewStyle(.circular)
						} else {
							Text(AccountDeletionStrings.deleteButtonTitle)
								.font(.body.weight(.bold))
						}
					}
					.tint(DesignToken.View.destructive)
					.disabled(!viewModel.canExecuteDeleteAccount || viewModel.state.isDeletingAccount)
				}
			}
		}
		#if os(macOS)
		.frame(minWidth: 460, minHeight: 440)
		#endif
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
