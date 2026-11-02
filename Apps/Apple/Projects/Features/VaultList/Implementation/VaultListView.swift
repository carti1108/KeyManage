import SwiftUI
import KeyManageDomain
import KeyManageDesignSystem

struct VaultListView: View {
	@ObservedObject var viewModel: VaultListViewModel

	init(viewModel: VaultListViewModel) {
		self.viewModel = viewModel
	}

	var body: some View {
		VStack(spacing: 16) {
			searchBar
			itemsList
		}
		.padding()
		.background(DesignToken.View.background)
		.overlay(alignment: .bottom) {
			if let toast = viewModel.toastMessage {
				toastView(toast)
					.transition(.move(edge: .bottom).combined(with: .opacity))
			}
		}
	}

	// MARK: - Search Bar

	private var searchBar: some View {
		HStack {
			Image(systemName: "magnifyingglass")
				.foregroundColor(DesignToken.View.mutedForeground)

			TextField("서비스명, 아이디 검색...", text: Binding(
				get: { viewModel.searchQuery },
				set: { viewModel.updateSearchQuery($0) }
			))
			.textFieldStyle(.plain)
			.font(.body)

			if !viewModel.searchQuery.isEmpty {
				Button(action: { viewModel.updateSearchQuery("") }) {
					Image(systemName: "xmark.circle.fill")
						.foregroundColor(DesignToken.View.mutedForeground)
				}
				.buttonStyle(.plain)
			}
		}
		.padding(.horizontal, 12)
		.padding(.vertical, 8)
		.background(DesignToken.View.card)
		.cornerRadius(8)
		.overlay(
			RoundedRectangle(cornerRadius: 8)
				.stroke(DesignToken.View.border, lineWidth: 1)
		)
	}

	// MARK: - Items List

	private var itemsList: some View {
		Group {
			if viewModel.items.isEmpty {
				emptyStateView
			} else {
				ScrollView {
					LazyVStack(spacing: 12) {
						ForEach(viewModel.items, id: \.id) { item in
							itemCard(item)
						}
					}
				}
			}
		}
	}

	private var emptyStateView: some View {
		VStack(spacing: 12) {
			Spacer()
			Image(systemName: "tray")
				.font(.system(size: 44))
				.foregroundColor(DesignToken.View.mutedForeground)
			Text(viewModel.searchQuery.isEmpty ? "저장된 볼트 아이템이 없습니다" : "검색 결과가 없습니다")
				.font(.headline)
				.foregroundColor(DesignToken.View.mutedForeground)
			Spacer()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
	}

	// MARK: - Item Card

	private func itemCard(_ item: VaultItem) -> some View {
		KMCard(padding: .md) {
			VStack(alignment: .leading, spacing: 10) {
				HStack {
					Text(item.serviceName)
						.font(.headline.bold())
						.foregroundColor(DesignToken.View.foreground)

					Spacer()

					if let url = item.url, !url.isEmpty {
						Text(url)
							.font(.caption)
							.foregroundColor(DesignToken.View.mutedForeground)
							.lineLimit(1)
					}
				}

				Divider()

				// Username row
				HStack {
					Label(item.username, systemImage: "person.fill")
						.font(.subheadline)
						.foregroundColor(DesignToken.View.foreground)

					Spacer()

					KMButton(
						"아이디 복사",
						variant: .outline,
						size: .sm,
						action: { viewModel.copyUsername(for: item) }
					)
				}

				// Password row
				HStack {
					HStack(spacing: 6) {
						Image(systemName: "key.fill")
							.foregroundColor(DesignToken.View.mutedForeground)

						if viewModel.isPasswordRevealed(id: item.id) {
							Text(item.password)
								.font(.system(.subheadline, design: .monospaced))
								.foregroundColor(DesignToken.View.foreground)
						} else {
							Text(String(repeating: "•", count: min(item.password.count, 12)))
								.font(.subheadline)
								.foregroundColor(DesignToken.View.mutedForeground)
						}

						Button(action: { viewModel.togglePasswordVisibility(id: item.id) }) {
							Image(systemName: viewModel.isPasswordRevealed(id: item.id) ? "eye.slash" : "eye")
								.foregroundColor(DesignToken.View.mutedForeground)
						}
						.buttonStyle(.plain)
					}

					Spacer()

					KMButton(
						"비밀번호 복사",
						variant: .primary,
						size: .sm,
						action: { viewModel.copyPassword(for: item) }
					)
				}
			}
		}
	}

	// MARK: - Toast View

	private func toastView(_ message: String) -> some View {
		HStack(spacing: 8) {
			Image(systemName: "checkmark.circle.fill")
				.foregroundColor(.green)
			Text(message)
				.font(.footnote.bold())
				.foregroundColor(.white)
		}
		.padding(.horizontal, 16)
		.padding(.vertical, 10)
		.background(Color.black.opacity(0.85))
		.cornerRadius(20)
		.padding(.bottom, 16)
		.shadow(radius: 4)
	}
}
