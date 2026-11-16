import SwiftUI
import KeyManageDomain
import KeyManageDesignSystem

/// 볼트 항목 상세 화면. 목록에서 항목을 누르면 열린다 (iPhone 목업 6단계).
struct VaultItemDetailView: View {
	let item: VaultItem
	let isPasswordRevealed: Bool
	let isReadOnly: Bool
	let clipboardBurnStartedAt: Date?
	let send: (VaultListViewAction) -> Void

	private static let burnSeconds = Int(ClipboardManager.defaultAutoBurnInterval)

	var body: some View {
		ScrollView {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				infoGroup
				passwordSection
				KMGaugeBar(score: EntropyScorer.score(for: item.password))
				burnBadge
				if let notes = item.notes, !notes.isEmpty {
					labeled("메모") {
						Text(notes)
							.font(DesignToken.Typography.body)
							.foregroundColor(DesignToken.View.foreground)
							.textSelection(.enabled)
					}
				}
				actionButtons
			}
			.padding(DesignToken.Spacing.space4)
		}
		.background(DesignToken.View.background)
		.navigationTitle(item.serviceName)
		.toolbar {
			ToolbarItem(placement: .primaryAction) {
				if !isReadOnly {
					Button("수정") { send(.startEditingItem(item)) }
				}
			}
		}
	}

	// MARK: - Sections

	private var infoGroup: some View {
		VStack(spacing: 0) {
			infoRow("서비스 이름", value: item.serviceName)
			Divider()
			infoRow("아이디", value: item.username.isEmpty ? "(아이디 없음)" : item.username)
			if let url = item.url, !url.isEmpty {
				Divider()
				infoRow("URL", value: url)
			}
		}
		.padding(.horizontal, DesignToken.Spacing.space4)
		.background(DesignToken.View.muted)
		.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.lg))
	}

	private var passwordSection: some View {
		labeled("비밀번호") {
			HStack(spacing: DesignToken.Spacing.space2) {
				Text(isPasswordRevealed ? item.password : String(repeating: "•", count: 16))
					.font(DesignToken.Typography.passwordMonospaced)
					.foregroundColor(DesignToken.View.foreground)
					.lineLimit(1)
					.minimumScaleFactor(0.7)
					.accessibilityLabel(isPasswordRevealed ? item.password : "비밀번호 숨김")
				Spacer(minLength: 0)
				Button(isPasswordRevealed ? "숨기기" : "보기") {
					send(.togglePasswordVisibility(id: item.id))
				}
				.buttonStyle(.plain)
				.font(DesignToken.Typography.caption.weight(.medium))
				.foregroundColor(DesignToken.View.mutedForeground)
				Button("복사") {
					send(.copyPassword(item))
				}
				.buttonStyle(.plain)
				.font(DesignToken.Typography.caption.weight(.medium))
				.foregroundColor(DesignToken.View.mutedForeground)
				.accessibilityLabel("비밀번호 복사")
			}
			.kmFieldContainer()
		}
	}

	@ViewBuilder
	private var burnBadge: some View {
		if let startedAt = clipboardBurnStartedAt {
			TimelineView(.periodic(from: startedAt, by: 1)) { context in
				let elapsed = Int(context.date.timeIntervalSince(startedAt))
				let remaining = max(0, Self.burnSeconds - elapsed)
				if remaining > 0 {
					KMBurnBadge(remainingSeconds: remaining, totalSeconds: Self.burnSeconds)
				}
			}
		}
	}

	private var actionButtons: some View {
		HStack(spacing: DesignToken.Spacing.space2) {
			KMButton("아이디 복사", variant: .secondary, size: .lg, isDisabled: item.username.isEmpty, isFullWidth: true) {
				send(.copyUsername(item))
			}
			if !isReadOnly {
				KMButton("항목 삭제", variant: .destructive, size: .lg, isFullWidth: true) {
					send(.confirmDeleteItem(item))
				}
			}
		}
	}

	// MARK: - Helpers

	private func infoRow(_ title: String, value: String) -> some View {
		HStack {
			Text(title)
				.font(DesignToken.Typography.body)
				.foregroundColor(DesignToken.View.mutedForeground)
			Spacer()
			Text(value)
				.font(DesignToken.Typography.body)
				.foregroundColor(DesignToken.View.foreground)
				.lineLimit(1)
				.textSelection(.enabled)
		}
		.padding(.vertical, DesignToken.Spacing.space3)
		.accessibilityElement(children: .combine)
	}

	private func labeled<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
			Text(title)
				.font(DesignToken.Typography.caption.weight(.medium))
				.foregroundColor(DesignToken.View.mutedForeground)
			content()
		}
	}
}
