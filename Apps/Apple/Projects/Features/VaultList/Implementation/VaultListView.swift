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
			topBar
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
		.sheet(isPresented: $viewModel.isItemSheetPresented) {
			itemFormModalView
		}
		.alert("항목 삭제", isPresented: $viewModel.isDeleteAlertPresented) {
			Button("취소", role: .cancel) {
				viewModel.itemToDelete = nil
			}
			Button("삭제", role: .destructive) {
				viewModel.executeDeleteItem()
			}
		} message: {
			Text("이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다.")
		}
	}

	// MARK: - Top Bar (Search + Add Button)

	private var topBar: some View {
		HStack(spacing: 12) {
			searchBar

			if !viewModel.isReadOnly {
				KMButton(
					"항목 추가",
					variant: .primary,
					size: .md,
					action: { viewModel.startCreatingItem() }
				)
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
			if !viewModel.isReadOnly && viewModel.searchQuery.isEmpty {
				KMButton(
					"첫 항목 추가하기",
					variant: .outline,
					size: .sm,
					action: { viewModel.startCreatingItem() }
				)
				.padding(.top, 4)
			}
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

					if !viewModel.isReadOnly {
						Button(action: { viewModel.startEditingItem(item) }) {
							Image(systemName: "pencil")
								.foregroundColor(DesignToken.View.mutedForeground)
						}
						.buttonStyle(.plain)
						.help("수정")

						Button(action: { viewModel.confirmDeleteItem(item) }) {
							Image(systemName: "trash")
								.foregroundColor(.red)
						}
						.buttonStyle(.plain)
						.help("삭제")
					}
				}

				Divider()

				// Username row
				HStack {
					Label(item.username.isEmpty ? "(아이디 없음)" : item.username, systemImage: "person.fill")
						.font(.subheadline)
						.foregroundColor(item.username.isEmpty ? DesignToken.View.mutedForeground : DesignToken.View.foreground)

					Spacer()

					if !item.username.isEmpty {
						KMButton(
							"아이디 복사",
							variant: .outline,
							size: .sm,
							action: { viewModel.copyUsername(for: item) }
						)
					}
				}

				// Password row
				HStack {
					HStack(spacing: 6) {
						Image(systemName: "key.fill")
							.foregroundColor(DesignToken.View.mutedForeground)

						if viewModel.isPasswordRevealed(id: item.id) {
							Text(item.password.isEmpty ? "(비밀번호 없음)" : item.password)
								.font(.system(.subheadline, design: .monospaced))
								.foregroundColor(item.password.isEmpty ? DesignToken.View.mutedForeground : DesignToken.View.foreground)
								.lineLimit(1)
								.truncationMode(.middle)
						} else {
							Text(item.password.isEmpty ? "(비밀번호 없음)" : String(repeating: "•", count: min(item.password.count, 12)))
								.font(.subheadline)
								.foregroundColor(DesignToken.View.mutedForeground)
						}

						if !item.password.isEmpty {
							Button(action: { viewModel.togglePasswordVisibility(id: item.id) }) {
								Image(systemName: viewModel.isPasswordRevealed(id: item.id) ? "eye.slash" : "eye")
									.foregroundColor(DesignToken.View.mutedForeground)
							}
							.buttonStyle(.plain)
						}
					}

					Spacer()

					if !item.password.isEmpty {
						KMButton(
							"비밀번호 복사",
							variant: .primary,
							size: .sm,
							action: { viewModel.copyPassword(for: item) }
						)
					}
				}

				if let notes = item.notes, !notes.isEmpty {
					Text(notes)
						.font(.caption)
						.foregroundColor(DesignToken.View.mutedForeground)
						.lineLimit(2)
				}
			}
		}
	}

	// MARK: - Item Form Modal Sheet

	private var itemFormModalView: some View {
		VStack(spacing: 20) {
			Text(viewModel.editingItem == nil ? "새 볼트 항목" : "항목 수정")
				.font(.title2.bold())
				.foregroundColor(DesignToken.View.foreground)

			if let error = viewModel.formErrorMessage {
				Text(error)
					.font(.footnote)
					.foregroundColor(.red)
			}

			ScrollView {
				itemFormFieldsView
					.padding(.horizontal, 2)
			}

			itemFormButtonsView
		}
		.padding(24)
		#if os(macOS)
		.frame(minWidth: 440)
		#else
		.frame(maxWidth: .infinity)
		#endif
		.alert("항목 삭제", isPresented: $viewModel.isDeleteAlertPresented) {
			Button("취소", role: .cancel) {
				viewModel.itemToDelete = nil
			}
			Button("삭제", role: .destructive) {
				viewModel.executeDeleteItem()
			}
		} message: {
			Text("이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다.")
		}
	}

	private var itemFormFieldsView: some View {
		VStack(spacing: 12) {
			formInputField(
				title: "서비스 이름 *",
				text: $viewModel.formServiceName,
				placeholder: "예: Google, GitHub"
			)

			formInputField(
				title: "아이디 / 이메일",
				text: $viewModel.formUsername,
				placeholder: "사용자 아이디 또는 이메일"
			)

			VStack(alignment: .leading, spacing: 4) {
				HStack {
					Text("비밀번호")
						.font(.caption.bold())
						.foregroundColor(DesignToken.View.foreground)

					Spacer()

					KMButton(
						"자동 생성",
						variant: .outline,
						size: .sm,
						action: { viewModel.generatePasswordForForm() }
					)
				}

				KMPasswordField(
					text: $viewModel.formPassword,
					placeholder: "비밀번호 입력 또는 자동 생성",
					showCopyAction: false
				)
			}

			formInputField(
				title: "웹사이트 URL",
				text: $viewModel.formUrl,
				placeholder: "https://example.com"
			)

			formInputField(
				title: "메모",
				text: $viewModel.formNotes,
				placeholder: "추가 메모 (선택 사항)"
			)
		}
	}

	private func formInputField(
		title: String,
		text: Binding<String>,
		placeholder: String
	) -> some View {
		VStack(alignment: .leading, spacing: 4) {
			Text(title)
				.font(.caption.bold())
				.foregroundColor(DesignToken.View.foreground)

			TextField(placeholder, text: text)
				.textFieldStyle(.plain)
				.padding(8)
				.background(DesignToken.View.card)
				.cornerRadius(6)
				.overlay(
					RoundedRectangle(cornerRadius: 6)
						.stroke(DesignToken.View.border, lineWidth: 1)
				)
		}
	}

	private var itemFormButtonsView: some View {
		HStack(spacing: 12) {
			KMButton(
				"취소",
				variant: .outline,
				action: {
					viewModel.cancelEditing()
				}
			)

			Spacer()

			if let editing = viewModel.editingItem {
				KMButton(
					"삭제",
					variant: .destructive,
					action: {
						viewModel.confirmDeleteItem(editing)
					}
				)
			}

			KMButton(
				"저장",
				variant: .primary,
				action: { viewModel.requestSaveItem() }
			)
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
