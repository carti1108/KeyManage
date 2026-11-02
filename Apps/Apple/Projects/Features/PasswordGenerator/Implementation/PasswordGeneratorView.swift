import SwiftUI
import KeyManageDomain
import KeyManageDesignSystem

struct PasswordGeneratorView: View {
	@ObservedObject var viewModel: PasswordGeneratorViewModel

	var body: some View {
		ScrollView {
			VStack(spacing: 24) {
				// 1. 비밀번호 표시 및 액션 카드
				passwordDisplayCard

				// 2. 강도 및 취약점 진단 카드
				strengthScoreCard

				// 3. 생성 옵션 설정 카드
				optionsCard
			}
			.padding(24)
		}
		.overlay(alignment: .bottom) {
			if let toast = viewModel.state.toastMessage {
				toastOverlay(toast)
			}
		}
	}

	// MARK: - Subviews

	private var passwordDisplayCard: some View {
		KMCard {
			VStack(alignment: .leading, spacing: 16) {
				Text("생성된 비밀번호")
					.font(.headline)
					.foregroundColor(DesignToken.View.foreground)

				HStack {
					Text(viewModel.state.generatedPassword.isEmpty ? "비밀번호를 생성 중입니다..." : viewModel.state.generatedPassword)
						.font(.system(.title3, design: .monospaced))
						.bold()
						.foregroundColor(DesignToken.View.primary)
						.lineLimit(2)
						.minimumScaleFactor(0.7)

					Spacer()

					HStack(spacing: 8) {
						KMButton(
							"재생성",
							variant: .outline,
							size: .sm,
							action: { viewModel.request(action: .regenerate) }
						)

						KMButton(
							"복사",
							variant: .primary,
							size: .sm,
							action: { viewModel.request(action: .copyPassword(viewModel.state.generatedPassword)) }
						)
						.disabled(viewModel.state.generatedPassword.isEmpty)
					}
				}
				.padding(12)
				.background(DesignToken.View.muted)
				.cornerRadius(8)
			}
			.padding(16)
		}
	}

	private var strengthScoreCard: some View {
		KMCard {
			VStack(alignment: .leading, spacing: 16) {
				HStack {
					Text("보안 강도")
						.font(.headline)
						.foregroundColor(DesignToken.View.foreground)

					Spacer()

					Text(viewModel.state.securityLevel.title)
						.font(.caption.bold())
						.padding(.horizontal, 8)
						.padding(.vertical, 4)
						.background(scoreColor(for: viewModel.state.securityLevel).opacity(0.15))
						.foregroundColor(scoreColor(for: viewModel.state.securityLevel))
						.cornerRadius(4)

					Text("\(viewModel.state.score)점 / 100점")
						.font(.subheadline.bold())
						.foregroundColor(DesignToken.View.mutedForeground)
				}

				// 강도 게이지 바
				GeometryReader { geometry in
					ZStack(alignment: .leading) {
						RoundedRectangle(cornerRadius: 4)
							.fill(DesignToken.View.muted)
							.frame(height: 8)

						RoundedRectangle(cornerRadius: 4)
							.fill(scoreColor(for: viewModel.state.securityLevel))
							.frame(width: max(0, min(geometry.size.width, geometry.size.width * CGFloat(viewModel.state.score) / 100.0)), height: 8)
					}
				}
				.frame(height: 8)

				// 안내 및 경고 문구
				if viewModel.state.securityLevel == .danger {
					Text(EntropyScorer.warningMessage)
						.font(.footnote)
						.foregroundColor(DesignToken.View.destructive)
				}

				if let notice = viewModel.state.sequentialDigitsNotice {
					Text(notice)
						.font(.footnote)
						.foregroundColor(DesignToken.View.destructive)
				}
			}
			.padding(16)
		}
	}

	private var optionsCard: some View {
		KMCard {
			VStack(alignment: .leading, spacing: 20) {
				Text("생성 옵션")
					.font(.headline)
					.foregroundColor(DesignToken.View.foreground)

				// 길이 슬라이더
				VStack(alignment: .leading, spacing: 8) {
					HStack {
						Text("비밀번호 길이")
							.font(.subheadline)
							.foregroundColor(DesignToken.View.foreground)
						Spacer()
						Text("\(Int(viewModel.state.length))자")
							.font(.subheadline.bold())
							.foregroundColor(DesignToken.View.primary)
					}

					Slider(
						value: Binding(
							get: { viewModel.state.length },
							set: { viewModel.request(action: .updateLength($0)) }
						),
						in: Double(PasswordGenerationOptions.minimumLength)...Double(PasswordGenerationOptions.maximumLength),
						step: 1
					)
				}

				Divider()

				// 문자 종류 토글들
				VStack(alignment: .leading, spacing: 12) {
					Toggle(isOn: Binding(
						get: { viewModel.state.includeUppercase },
						set: { _ in viewModel.request(action: .toggleUppercase) }
					)) {
						VStack(alignment: .leading) {
							Text("대문자 (A-Z)")
								.font(.subheadline)
							Text("ABC...XYZ")
								.font(.caption)
								.foregroundColor(DesignToken.View.mutedForeground)
						}
					}

					Toggle(isOn: Binding(
						get: { viewModel.state.includeLowercase },
						set: { _ in viewModel.request(action: .toggleLowercase) }
					)) {
						VStack(alignment: .leading) {
							Text("소문자 (a-z)")
								.font(.subheadline)
							Text("abc...xyz")
								.font(.caption)
								.foregroundColor(DesignToken.View.mutedForeground)
						}
					}

					Toggle(isOn: Binding(
						get: { viewModel.state.includeDigits },
						set: { _ in viewModel.request(action: .toggleDigits) }
					)) {
						VStack(alignment: .leading) {
							Text("숫자 (0-9)")
								.font(.subheadline)
							Text("0123456789")
								.font(.caption)
								.foregroundColor(DesignToken.View.mutedForeground)
						}
					}

					Toggle(isOn: Binding(
						get: { viewModel.state.includeSymbols },
						set: { _ in viewModel.request(action: .toggleSymbols) }
					)) {
						VStack(alignment: .leading) {
							Text("특수문자")
								.font(.subheadline)
							Text("!@#$%^&*()_+-=[]{}|;:,.<>?")
								.font(.caption)
								.foregroundColor(DesignToken.View.mutedForeground)
						}
					}
				}
			}
			.padding(16)
		}
	}

	// MARK: - Helpers

	private func scoreColor(for level: PasswordSecurityLevel) -> Color {
		switch level {
		case .danger:
			return DesignToken.View.destructive
		case .warning:
			return Color.orange
		case .secure:
			return Color.green
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
