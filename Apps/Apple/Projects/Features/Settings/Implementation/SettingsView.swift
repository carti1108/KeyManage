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
			VStack(spacing: 24) {
				// 1. 자동 잠금 섹션
				autoLockSection

				// 2. 백업 및 복원 섹션
				backupSection

				// 3. 계정 섹션 (로그아웃)
				accountSection
			}
			.padding(24)
		}
		.overlay(alignment: .bottom) {
			if let toast = viewModel.toastMessage {
				toastOverlay(toast)
			}
		}
		.sheet(isPresented: $viewModel.isRestoreModalPresented) {
			restoreModalView
		}
		.alert("로그아웃", isPresented: $viewModel.isLogoutConfirmPresented) {
			Button("취소", role: .cancel) {}
			Button("로그아웃", role: .destructive) {
				viewModel.confirmLogout()
			}
		} message: {
			Text("로그아웃하시겠습니까? 세션이 종료되고 기기에서 민감 키가 소거됩니다.")
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
			VStack(alignment: .leading, spacing: 16) {
				Text("보안 설정")
					.font(.headline)
					.foregroundColor(DesignToken.View.foreground)

				VStack(alignment: .leading, spacing: 8) {
					Text("자동 잠금 시간")
						.font(.subheadline)
						.foregroundColor(DesignToken.View.mutedForeground)

					Picker("자동 잠금 시간", selection: Binding(
						get: { viewModel.selectedTimeout },
						set: { viewModel.selectTimeout($0) }
					)) {
						ForEach(AutoLockTimeout.allCases, id: \.self) { timeout in
							Text(timeoutTitle(for: timeout)).tag(timeout)
						}
					}
					.pickerStyle(.menu)
				}
			}
			.padding(16)
		}
	}

	private var backupSection: some View {
		KMCard {
			VStack(alignment: .leading, spacing: 16) {
				Text("백업 및 복원")
					.font(.headline)
					.foregroundColor(DesignToken.View.foreground)

				VStack(alignment: .leading, spacing: 8) {
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
						action: { viewModel.exportBackup() }
					)
				}

				Divider()

				VStack(alignment: .leading, spacing: 8) {
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
						action: { viewModel.isRestoreModalPresented = true }
					)
				}
			}
			.padding(16)
		}
	}

	private var accountSection: some View {
		KMCard {
			VStack(alignment: .leading, spacing: 16) {
				Text("계정")
					.font(.headline)
					.foregroundColor(DesignToken.View.foreground)

				KMButton(
					"로그아웃",
					variant: .destructive,
					action: { viewModel.isLogoutConfirmPresented = true }
				)
			}
			.padding(16)
		}
	}

	// MARK: - Restore Modal

	private var restoreModalView: some View {
		VStack(spacing: 20) {
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

			HStack(spacing: 12) {
				KMButton(
					"취소",
					variant: .outline,
					action: {
						viewModel.isRestoreModalPresented = false
						resetRestoreState()
					}
				)

				KMButton(
					KeyManageBackupStrings.restoreButton,
					variant: .primary,
					isDisabled: selectedFileData == nil || restorePassword.isEmpty,
					action: {
						guard let data = selectedFileData, !restorePassword.isEmpty else { return }
						viewModel.restoreBackup(fileData: data, password: restorePassword)
						viewModel.isRestoreModalPresented = false
						resetRestoreState()
					}
				)
			}
			.padding(.horizontal)
		}
		.padding(24)
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
				viewModel.showToast("파일 읽기 실패: \(error.localizedDescription)")
			}
		case .failure(let error):
			viewModel.showToast("파일 선택 실패: \(error.localizedDescription)")
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
			.foregroundColor(.white)
			.padding(.horizontal, 16)
			.padding(.vertical, 10)
			.background(Color.black.opacity(0.85))
			.cornerRadius(8)
			.padding(.bottom, 24)
			.transition(.opacity)
	}
}
