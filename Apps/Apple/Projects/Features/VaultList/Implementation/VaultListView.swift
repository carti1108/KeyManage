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
			if viewModel.state.hasPendingSync || viewModel.state.isOffline {
				HStack(spacing: DesignToken.Spacing.space2) {
					Image(systemName: "wifi.slash")
						.foregroundColor(DesignToken.View.mutedForeground)
					Text(VaultSyncStrings.offlineWarningMessage)
						.font(DesignToken.Typography.caption)
						.foregroundColor(DesignToken.View.foreground)
					Spacer()
				}
				.padding(.horizontal, DesignToken.Spacing.space4)
				.padding(.vertical, DesignToken.Spacing.space2)
				.background(DesignToken.View.muted)
			}

			itemsList
		}
		.searchable(
			text: Binding(
				get: { viewModel.state.searchQuery },
				set: { viewModel.request(action: .search($0)) }
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
					if viewModel.state.isSyncing {
						ProgressView()
							.controlSize(.small)
					} else {
						Image(systemName: viewModel.state.hasPendingSync ? "arrow.triangle.2.circlepath.circle" : "arrow.triangle.2.circlepath")
					}
				}
				.disabled(viewModel.state.isSyncing)
				.accessibilityLabel("볼트 동기화")
			}

			ToolbarItem(placement: .primaryAction) {
				if !viewModel.state.isReadOnly {
					Button {
						viewModel.request(action: .startCreatingItem)
					} label: {
						Image(systemName: "plus")
					}
					.accessibilityLabel("항목 추가")
				}
			}
		}
		.background(DesignToken.View.background)
		.overlay(alignment: .bottom) {
			if let toast = viewModel.state.toastMessage {
				toastView(toast)
					.transition(.move(edge: .bottom).combined(with: .opacity))
			}
		}
		.sheet(
			isPresented: Binding(
				get: { viewModel.state.isItemSheetPresented },
				set: { viewModel.request(action: .setItemSheetPresented($0)) }
			)
		) {
			itemFormModalView
		}
		.alert(
			"항목 삭제",
			isPresented: Binding(
				get: { viewModel.state.isDeleteAlertPresented },
				set: { viewModel.request(action: .setDeleteAlertPresented($0)) }
			)
		) {
			Button("취소", role: .cancel) {
				viewModel.request(action: .cancelDeleteItem)
			}
			Button("삭제", role: .destructive) {
				viewModel.request(action: .executeDeleteItem)
			}
		} message: {
			Text("이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다.")
		}
	}

	// MARK: - Items List

	private var itemsList: some View {
		Group {
			if viewModel.state.items.isEmpty {
				emptyStateView
			} else {
				ScrollView {
					LazyVStack(spacing: DesignToken.Spacing.space3) {
						ForEach(viewModel.state.items, id: \.id) { item in
							itemCard(item)
						}
					}
					.padding(.horizontal)
					.padding(.vertical, DesignToken.Spacing.space2)
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
					viewModel.state.searchQuery.isEmpty ? "저장된 볼트 아이템이 없습니다" : "검색 결과가 없습니다",
					systemImage: "tray"
				)
			} description: {
				Text(viewModel.state.searchQuery.isEmpty ? "새로운 계정 정보를 안전하게 보관해 보세요." : "검색어를 확인하거나 새로운 항목을 추가해 보세요.")
			} actions: {
				if !viewModel.state.isReadOnly && viewModel.state.searchQuery.isEmpty {
					Button("첫 항목 추가하기") {
						viewModel.request(action: .startCreatingItem)
					}
					.buttonStyle(.borderedProminent)
				}
			}
		} else {
			legacyEmptyStateView
		}
	}

	private var legacyEmptyStateView: some View {
		VStack(spacing: DesignToken.Spacing.space3) {
			Spacer()
			Image(systemName: "tray")
				.font(.system(size: 44))
				.foregroundColor(DesignToken.View.mutedForeground)
			Text(viewModel.state.searchQuery.isEmpty ? "저장된 볼트 아이템이 없습니다" : "검색 결과가 없습니다")
				.font(DesignToken.Typography.heading)
				.foregroundColor(DesignToken.View.mutedForeground)
			if !viewModel.state.isReadOnly && viewModel.state.searchQuery.isEmpty {
				Button("첫 항목 추가하기") {
					viewModel.request(action: .startCreatingItem)
				}
				.buttonStyle(.borderedProminent)
				.padding(.top, DesignToken.Spacing.space1)
			}
			Spacer()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
	}

	// MARK: - Item Card

	private func itemCard(_ item: VaultItem) -> some View {
		KMCard(padding: .md) {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space3) {
				HStack {
					Text(item.serviceName)
						.font(DesignToken.Typography.heading.weight(.bold))
						.foregroundColor(DesignToken.View.foreground)

					Spacer()

					if let url = item.url, !url.isEmpty {
						Text(url)
							.font(DesignToken.Typography.caption)
							.foregroundColor(DesignToken.View.mutedForeground)
							.lineLimit(1)
					}

					if !viewModel.state.isReadOnly {
						Button(action: { viewModel.request(action: .startEditingItem(item)) }) {
							Image(systemName: "pencil")
								.foregroundColor(DesignToken.View.mutedForeground)
						}
						.buttonStyle(.plain)
						.help("수정")

						Button(action: { viewModel.request(action: .confirmDeleteItem(item)) }) {
							Image(systemName: "trash")
								.foregroundColor(DesignToken.View.destructive)
						}
						.buttonStyle(.plain)
						.help("삭제")
					}
				}

				Divider()

				// Username row
				HStack {
					Label(item.username.isEmpty ? "(아이디 없음)" : item.username, systemImage: "person.fill")
						.font(DesignToken.Typography.label)
						.foregroundColor(item.username.isEmpty ? DesignToken.View.mutedForeground : DesignToken.View.foreground)

					Spacer()

					if !item.username.isEmpty {
						KMButton(
							"아이디 복사",
							variant: .outline,
							size: .sm,
							action: { viewModel.request(action: .copyUsername(item)) }
						)
					}
				}

				// Password row
				HStack {
					HStack(spacing: DesignToken.Spacing.space2) {
						Image(systemName: "key.fill")
							.foregroundColor(DesignToken.View.mutedForeground)

						if viewModel.state.isPasswordRevealed(id: item.id) {
							Text(item.password.isEmpty ? "(비밀번호 없음)" : item.password)
								.font(DesignToken.Typography.labelMono)
								.foregroundColor(item.password.isEmpty ? DesignToken.View.mutedForeground : DesignToken.View.foreground)
								.lineLimit(1)
								.truncationMode(.middle)
						} else {
							Text(item.password.isEmpty ? "(비밀번호 없음)" : String(repeating: "•", count: min(item.password.count, 12)))
								.font(DesignToken.Typography.label)
								.foregroundColor(DesignToken.View.mutedForeground)
						}

						if !item.password.isEmpty {
							Button(action: { viewModel.request(action: .togglePasswordVisibility(id: item.id)) }) {
								Image(systemName: viewModel.state.isPasswordRevealed(id: item.id) ? "eye.slash" : "eye")
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
							action: { viewModel.request(action: .copyPassword(item)) }
						)
					}
				}

				if let notes = item.notes, !notes.isEmpty {
					Text(notes)
						.font(DesignToken.Typography.caption)
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
				VStack(spacing: DesignToken.Spacing.space5) {
					if let error = viewModel.state.formErrorMessage {
						Text(error)
							.font(DesignToken.Typography.caption)
							.foregroundColor(DesignToken.View.destructive)
							.frame(maxWidth: .infinity, alignment: .leading)
					}

					itemFormFieldsView

					if let editing = viewModel.state.editingItem {
						Button(role: .destructive) {
							viewModel.request(action: .confirmDeleteItem(editing))
						} label: {
							HStack {
								Image(systemName: "trash")
								Text("이 항목 삭제")
							}
							.frame(maxWidth: .infinity)
							.padding(.vertical, DesignToken.Spacing.space3)
						}
						.buttonStyle(.bordered)
						.tint(DesignToken.View.destructive)
						.padding(.top, DesignToken.Spacing.space2)
					}
				}
				.padding(DesignToken.Spacing.space5)
			}
			.navigationTitle(viewModel.state.editingItem == nil ? "새 볼트 항목" : "항목 수정")
			#if os(iOS)
			.navigationBarTitleDisplayMode(.inline)
			#endif
			.toolbar {
				ToolbarItem(placement: .cancellationAction) {
					Button("취소") {
						viewModel.request(action: .cancelEditing)
					}
				}
				ToolbarItem(placement: .confirmationAction) {
					Button("저장") {
						viewModel.request(action: .requestSaveItem)
					}
					.font(DesignToken.Typography.body.weight(.bold))
				}
			}
		}
		#if os(macOS)
		.frame(minWidth: 440, minHeight: 480)
		#endif
		.alert(
			"항목 삭제",
			isPresented: Binding(
				get: { viewModel.state.isDeleteAlertPresented },
				set: { viewModel.request(action: .setDeleteAlertPresented($0)) }
			)
		) {
			Button("취소", role: .cancel) {
				viewModel.request(action: .cancelDeleteItem)
			}
			Button("삭제", role: .destructive) {
				viewModel.request(action: .executeDeleteItem)
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
		VStack(spacing: DesignToken.Spacing.space3) {
			formInputField(
				title: "서비스 이름 *",
				text: Binding(
					get: { viewModel.state.formServiceName },
					set: { viewModel.request(action: .updateFormServiceName($0)) }
				),
				placeholder: "예: Google, GitHub"
			)

			formInputField(
				title: "아이디 / 이메일",
				text: Binding(
					get: { viewModel.state.formUsername },
					set: { viewModel.request(action: .updateFormUsername($0)) }
				),
				placeholder: "사용자 아이디 또는 이메일"
			)

			VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
				HStack {
					Text("비밀번호")
						.font(DesignToken.Typography.caption.weight(.bold))
						.foregroundColor(DesignToken.View.foreground)

					Spacer()

					KMButton(
						"자동 생성",
						variant: .outline,
						size: .sm,
						action: { viewModel.request(action: .generatePasswordForForm) }
					)
				}

				KMPasswordField(
					text: Binding(
						get: { viewModel.state.formPassword },
						set: { viewModel.request(action: .updateFormPassword($0)) }
					),
					placeholder: "비밀번호 입력 또는 자동 생성",
					showCopyAction: false
				)

				// 약한 비밀번호도 저장은 허용하고 안내만 한다 (GEN-001)
				if viewModel.state.showsFormStrengthGauge {
					KMGaugeBar(score: viewModel.state.formPasswordStrengthScore)
				}
				if viewModel.state.showsFormWeakPasswordWarning {
					Text(EntropyScorer.warningMessage)
						.font(DesignToken.Typography.caption)
						.foregroundColor(DesignToken.View.mutedForeground)
						.fixedSize(horizontal: false, vertical: true)
				}
				if viewModel.state.showsFormSequentialDigitsNotice {
					Text(SequentialDigitsChecker.noticeMessage)
						.font(DesignToken.Typography.caption)
						.foregroundColor(DesignToken.View.mutedForeground)
						.fixedSize(horizontal: false, vertical: true)
				}
			}

			formInputField(
				title: "웹사이트 URL",
				text: Binding(
					get: { viewModel.state.formUrl },
					set: { viewModel.request(action: .updateFormUrl($0)) }
				),
				placeholder: "https://example.com"
			)

			formInputField(
				title: "메모",
				text: Binding(
					get: { viewModel.state.formNotes },
					set: { viewModel.request(action: .updateFormNotes($0)) }
				),
				placeholder: "추가 메모 (선택 사항)"
			)
		}
	}

	private func formInputField(
		title: String,
		text: Binding<String>,
		placeholder: String
	) -> some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
			Text(title)
				.font(DesignToken.Typography.caption.weight(.bold))
				.foregroundColor(DesignToken.View.foreground)

			TextField(placeholder, text: text)
				.textFieldStyle(.plain)
				.padding(DesignToken.Spacing.space2)
				.background(DesignToken.View.card)
				.cornerRadius(DesignToken.Radius.sm)
				.overlay(
					RoundedRectangle(cornerRadius: DesignToken.Radius.sm)
						.stroke(DesignToken.View.border, lineWidth: 1)
				)
		}
	}

	// MARK: - Toast View

	private func toastView(_ message: String) -> some View {
		HStack(spacing: DesignToken.Spacing.space2) {
			Image(systemName: "checkmark.circle.fill")
				.foregroundColor(DesignToken.View.accent)
			Text(message)
				.font(DesignToken.Typography.caption.weight(.bold))
				.foregroundColor(DesignToken.View.primaryForeground)
		}
		.padding(.horizontal, DesignToken.Spacing.space4)
		.padding(.vertical, DesignToken.Spacing.space3)
		.background(DesignToken.View.primary)
		.clipShape(Capsule())
		.padding(.bottom, DesignToken.Spacing.space4)
		.shadow(radius: 4)
	}
}
