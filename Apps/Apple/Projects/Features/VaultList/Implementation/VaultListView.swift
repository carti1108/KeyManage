import SwiftUI
import KeyManageDomain
import KeyManageDesignSystem

struct VaultListView: View {
	@ObservedObject var viewModel: VaultListViewModel

	init(viewModel: VaultListViewModel) {
		self.viewModel = viewModel
	}

	var body: some View {
		VStack(spacing: 0) {
			if viewModel.hasPendingSync || viewModel.isOffline {
				HStack(spacing: 8) {
					Image(systemName: "wifi.slash")
						.foregroundColor(.orange)
					Text(VaultSyncStrings.offlineWarningMessage)
						.font(.caption)
						.foregroundColor(DesignToken.View.foreground)
					Spacer()
				}
				.padding(.horizontal, 16)
				.padding(.vertical, 8)
				.background(Color.orange.opacity(0.12))
			}

			itemsList
		}
		.searchable(
			text: Binding(
				get: { viewModel.searchQuery },
				set: { viewModel.updateSearchQuery($0) }
			),
			prompt: "서비스명, 아이디 검색..."
		)
		.toolbar {
			ToolbarItem(placement: .automatic) {
				Button {
					Task {
						await viewModel.triggerSync()
					}
				} label: {
					if viewModel.isSyncing {
						ProgressView()
							.controlSize(.small)
					} else {
						Image(systemName: viewModel.hasPendingSync ? "arrow.triangle.2.circlepath.circle" : "arrow.triangle.2.circlepath")
					}
				}
				.disabled(viewModel.isSyncing)
				.accessibilityLabel("볼트 동기화")
			}

			ToolbarItem(placement: .primaryAction) {
				if !viewModel.isReadOnly {
					Button {
						viewModel.startCreatingItem()
					} label: {
						Image(systemName: "plus")
					}
					.accessibilityLabel("항목 추가")
				}
			}
		}
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
					.padding(.horizontal)
					.padding(.vertical, 8)
				}
				.refreshable {
					await viewModel.triggerSync()
				}
			}
		}
	}

	@ViewBuilder
	private var emptyStateView: some View {
		if #available(iOS 17.0, macOS 14.0, *) {
			ContentUnavailableView {
				Label(
					viewModel.searchQuery.isEmpty ? "저장된 볼트 아이템이 없습니다" : "검색 결과가 없습니다",
					systemImage: "tray"
				)
			} description: {
				Text(viewModel.searchQuery.isEmpty ? "새로운 계정 정보를 안전하게 보관해 보세요." : "검색어를 확인하거나 새로운 항목을 추가해 보세요.")
			} actions: {
				if !viewModel.isReadOnly && viewModel.searchQuery.isEmpty {
					Button("첫 항목 추가하기") {
						viewModel.startCreatingItem()
					}
					.buttonStyle(.borderedProminent)
				}
			}
		} else {
			legacyEmptyStateView
		}
	}

	private var legacyEmptyStateView: some View {
		VStack(spacing: 12) {
			Spacer()
			Image(systemName: "tray")
				.font(.system(size: 44))
				.foregroundColor(DesignToken.View.mutedForeground)
			Text(viewModel.searchQuery.isEmpty ? "저장된 볼트 아이템이 없습니다" : "검색 결과가 없습니다")
				.font(.headline)
				.foregroundColor(DesignToken.View.mutedForeground)
			if !viewModel.isReadOnly && viewModel.searchQuery.isEmpty {
				Button("첫 항목 추가하기") {
					viewModel.startCreatingItem()
				}
				.buttonStyle(.borderedProminent)
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
		modalNavigationContainer {
			ScrollView {
				VStack(spacing: 20) {
					if let error = viewModel.formErrorMessage {
						Text(error)
							.font(.footnote)
							.foregroundColor(.red)
							.frame(maxWidth: .infinity, alignment: .leading)
					}

					itemFormFieldsView

					if let editing = viewModel.editingItem {
						Button(role: .destructive) {
							viewModel.confirmDeleteItem(editing)
						} label: {
							HStack {
								Image(systemName: "trash")
								Text("이 항목 삭제")
							}
							.frame(maxWidth: .infinity)
							.padding(.vertical, 10)
						}
						.buttonStyle(.bordered)
						.tint(.red)
						.padding(.top, 8)
					}
				}
				.padding(20)
			}
			.navigationTitle(viewModel.editingItem == nil ? "새 볼트 항목" : "항목 수정")
			#if os(iOS)
			.navigationBarTitleDisplayMode(.inline)
			#endif
			.toolbar {
				ToolbarItem(placement: .cancellationAction) {
					Button("취소") {
						viewModel.cancelEditing()
					}
				}
				ToolbarItem(placement: .confirmationAction) {
					Button("저장") {
						viewModel.requestSaveItem()
					}
					.font(.body.weight(.bold))
				}
			}
		}
		#if os(macOS)
		.frame(minWidth: 440, minHeight: 480)
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
