#if os(macOS)
import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// macOS 볼트 화면. 웹 VaultListRibScreen과 같은 2분할(왼쪽 목록 384, 오른쪽 상세·폼)이다
struct DesktopVaultListScreen: View {
	let view: SwiftUIVaultListView

	private static let listWidth: CGFloat = 384

	var body: some View {
		if let state = view.state {
			ScrollView {
				VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
					if let copyError = state.copyError {
						DesktopAlert(text: copyError)
					}
					HStack(alignment: .top, spacing: DesignToken.Spacing.space6) {
						ListPane(state: state, send: view.send)
							.frame(width: Self.listWidth)
						DetailPane(state: state, generatorSlot: view.generatorSlot, send: view.send)
							.frame(maxWidth: .infinity, alignment: .topLeading)
					}
				}
				.padding(DesignToken.Spacing.space6)
			}
			.background(DesignToken.View.background)
			.alert(
				VaultListMessages.DeleteConfirm.shared.TITLE,
				isPresented: sharedStateBinding(state.isConfirmingDelete) { view.send(VaultListViewActionCancelDelete.shared) }
			) {
				Button(VaultListMessages.DeleteConfirm.shared.CONFIRM, role: .destructive) { view.send(VaultListViewActionConfirmDelete.shared) }
				Button(VaultListMessages.DeleteConfirm.shared.CANCEL, role: .cancel) { view.send(VaultListViewActionCancelDelete.shared) }
			} message: {
				Text(VaultListMessages.DeleteConfirm.shared.BODY)
			}
		}
	}
}

/// 검색·항목 추가와 항목 카드 목록. 복사 버튼은 카드 옆에 둔다
private struct ListPane: View {
	let state: VaultListViewState
	let send: (VaultListViewAction) -> Void

	var body: some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space3) {
			HStack(alignment: .bottom, spacing: DesignToken.Spacing.space2) {
				DesktopTextField(label: "항목 검색", text: state.query, identifier: "vault.search") {
					send(VaultListViewActionSearch(query: $0))
				}
				if state.canEdit {
					KMButton("항목 추가", icon: "plus") { send(VaultListViewActionStartAdd.shared) }
						.fixedSize()
						.accessibilityIdentifier("vault.add")
				}
			}
			if state.items.isEmpty {
				if let empty = state.emptyMessage {
					Text(empty)
						.font(DesignToken.Typography.label)
						.foregroundColor(DesignToken.View.mutedForeground)
						.frame(maxWidth: .infinity)
						.padding(.vertical, DesignToken.Spacing.space6)
						.accessibilityIdentifier("vault.empty")
				}
			} else {
				ForEach(state.items, id: \.id) { item in
					row(item)
				}
			}
		}
	}

	private func row(_ item: VaultItem) -> some View {
		let name = item.serviceName ?? ""
		let username = item.username ?? ""
		let isOpen = item.id == state.selected?.id && state.screen != .list
		return HStack(spacing: DesignToken.Spacing.space2) {
			Button { send(VaultListViewActionOpenItem(id: item.id)) } label: {
				VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
					Text(name).font(DesignToken.Typography.body.weight(.medium))
					if !username.isEmpty {
						Text(username)
							.font(DesignToken.Typography.label)
							.foregroundColor(DesignToken.View.mutedForeground)
							.lineLimit(1)
					}
				}
				.frame(maxWidth: .infinity, alignment: .leading)
				.padding(DesignToken.Spacing.space3)
				.background(DesignToken.View.card)
				.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.lg))
				.overlay(
					RoundedRectangle(cornerRadius: DesignToken.Radius.lg)
						.stroke(isOpen ? DesignToken.View.ring : DesignToken.View.border, lineWidth: isOpen ? 2 : 1)
				)
				.contentShape(Rectangle())
			}
			.buttonStyle(.plain)
			.accessibilityIdentifier("vault.item.\(name)")
			if !(item.password ?? "").isEmpty {
				KMButton("", variant: .ghost, size: .sm, icon: DesignToken.Icon.clipboardCopy) {
					send(VaultListViewActionCopyPassword(itemId: item.id))
				}
				.fixedSize()
				.accessibilityLabel("\(name) 비밀번호 복사")
			}
		}
	}
}

/// 오른쪽 칸: 추가·수정 폼, 상세, 또는 고른 항목이 없다는 안내
private struct DetailPane: View {
	let state: VaultListViewState
	let generatorSlot: RIBScreenSlot
	let send: (VaultListViewAction) -> Void

	var body: some View {
		switch state.screen {
		case .add:
			FormPane(state: state, generatorSlot: generatorSlot, send: send)
		case .edit where state.selected != nil:
			FormPane(state: state, generatorSlot: generatorSlot, send: send)
		case .detail where state.selected != nil:
			if let item = state.selected {
				ItemPane(item: item, state: state, send: send)
			}
		default:
			emptyState
		}
	}

	private var emptyState: some View {
		VStack(spacing: DesignToken.Spacing.space1) {
			Image(systemName: "shield")
				.font(.system(size: 28))
				.foregroundColor(DesignToken.View.mutedForeground.opacity(0.4))
				.padding(.bottom, DesignToken.Spacing.space1)
			Text("선택된 항목이 없습니다").font(DesignToken.Typography.label.weight(.medium))
			Text("목록에서 항목을 선택하거나 새 항목을 추가해 주세요.")
				.font(DesignToken.Typography.caption)
		}
		.foregroundColor(DesignToken.View.mutedForeground)
		.frame(maxWidth: .infinity, minHeight: 256)
		.overlay(
			RoundedRectangle(cornerRadius: DesignToken.Radius.lg)
				.stroke(DesignToken.View.border, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
		)
	}
}

/// 항목 상세: 위에 목록으로·수정·삭제, 아래 카드에 값과 복사
private struct ItemPane: View {
	let item: VaultItem
	let state: VaultListViewState
	let send: (VaultListViewAction) -> Void

	var body: some View {
		let username = item.username ?? ""
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space3) {
			HStack {
				KMButton("목록으로", variant: .ghost, size: .sm, icon: "arrow.left", isDisabled: state.isSaving) {
					send(VaultListViewActionBack.shared)
				}
				.fixedSize()
				.accessibilityIdentifier("vault.back")
				Spacer()
				if state.canEdit {
					KMButton("수정", variant: .outline, size: .sm, icon: "pencil", isDisabled: state.isSaving) {
						send(VaultListViewActionStartEdit.shared)
					}
					.fixedSize()
					.accessibilityIdentifier("vault.edit")
					KMButton("삭제", variant: .destructive, size: .sm, icon: "trash", isDisabled: state.isSaving) {
						send(VaultListViewActionRequestDelete.shared)
					}
					.fixedSize()
					.accessibilityIdentifier("vault.delete")
				}
			}
			if let error = state.saveError {
				DesktopAlert(title: error.title, text: error.body, identifier: "vault.saveError")
			}
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				Text(item.serviceName ?? "")
					.font(DesignToken.Typography.heading)
					.accessibilityAddTraits(.isHeader)
				if !username.isEmpty {
					HStack(alignment: .bottom) {
						field("아이디", username)
						Spacer()
						KMButton("", variant: .ghost, size: .sm, icon: DesignToken.Icon.clipboardCopy) {
							send(VaultListViewActionCopyUsername(itemId: item.id))
						}
						.fixedSize()
						.accessibilityLabel("아이디 복사")
					}
				}
				DesktopPasswordField(
					label: "비밀번호",
					text: item.password ?? "",
					onCopy: (item.password ?? "").isEmpty ? nil : { send(VaultListViewActionCopyPassword(itemId: item.id)) },
					onChange: nil
				)
				if let url = item.url, !url.isEmpty {
					field("URL", url)
				}
				if let notes = item.notes, !notes.isEmpty {
					field("메모", notes)
				}
			}
			.frame(maxWidth: .infinity, alignment: .leading)
			.desktopCard()
		}
	}

	private func field(_ label: String, _ value: String) -> some View {
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
			Text(label).font(DesignToken.Typography.caption).foregroundColor(DesignToken.View.mutedForeground)
			Text(value).font(DesignToken.Typography.label).textSelection(.enabled)
		}
		.accessibilityElement(children: .combine)
	}
}

/// 항목 추가·수정 폼. 생성기는 열리면 버튼 자리에 자식 RIB 화면이 들어온다
private struct FormPane: View {
	let state: VaultListViewState
	let generatorSlot: RIBScreenSlot
	let send: (VaultListViewAction) -> Void

	var body: some View {
		let form = state.form
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
			Text(state.screen == .edit ? "항목 수정" : "항목 추가")
				.font(DesignToken.Typography.heading)
				.accessibilityAddTraits(.isHeader)
			input("서비스 이름", form.serviceName, field: .serviceName)
			input("아이디", form.username, field: .username)
			DesktopPasswordField(label: "비밀번호", text: form.password, identifier: "vault.form.password") {
				send(VaultListViewActionEditForm(field: .password, value: $0))
			}
			.disabled(state.isSaving)
			if state.showsFormStrength {
				KMGaugeBar(score: Int(state.formPasswordScore))
			}
			if state.showsFormWeakWarning {
				DesktopAlert(text: VaultListMessages.shared.WEAK_PASSWORD_BODY, isError: false)
			}
			if state.showsFormSequentialDigitsNotice {
				Text(VaultListMessages.shared.SEQUENTIAL_DIGITS_NOTICE)
					.font(DesignToken.Typography.label)
					.foregroundColor(DesignToken.View.mutedForeground)
			}
			if generatorSlot.current != nil {
				RIBSlotView(slot: generatorSlot)
			} else {
				KMButton("비밀번호 생성", variant: .outline, size: .sm, icon: "wand.and.stars", isDisabled: state.isSaving) {
					send(VaultListViewActionOpenGenerator.shared)
				}
				.fixedSize()
				.accessibilityIdentifier("vault.form.generator")
			}
			input("URL", form.url, field: .url)
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
				Text("메모").font(DesignToken.Typography.label.weight(.medium))
				TextEditor(text: Binding(get: { form.notes }, set: { send(VaultListViewActionEditForm(field: .notes, value: $0)) }))
					.font(DesignToken.Typography.label)
					.scrollContentBackground(.hidden)
					.frame(minHeight: 64)
					.padding(DesignToken.Spacing.space2)
					.overlay(RoundedRectangle(cornerRadius: DesignToken.Radius.md).stroke(DesignToken.View.input, lineWidth: 1))
					.disabled(state.isSaving)
					.accessibilityLabel("메모")
					.accessibilityIdentifier("vault.form.notes")
			}
			if let error = state.saveError {
				DesktopAlert(title: error.title, text: error.body, identifier: "vault.saveError")
			}
			HStack(spacing: DesignToken.Spacing.space2) {
				Spacer()
				KMButton("취소", variant: .outline, isDisabled: state.isSaving) { send(VaultListViewActionCancelForm.shared) }
					.fixedSize()
					.accessibilityIdentifier("vault.form.cancel")
				KMButton("저장", isLoading: state.isSaving) { send(VaultListViewActionSubmitForm.shared) }
					.fixedSize()
					.accessibilityIdentifier("vault.form.save")
			}
		}
		.frame(maxWidth: .infinity, alignment: .leading)
		.desktopCard()
	}

	private func input(_ label: String, _ value: String, field: VaultItemFormField) -> some View {
		DesktopTextField(label: label, text: value, identifier: "vault.form.\(field.name.lowercased())") {
			send(VaultListViewActionEditForm(field: field, value: $0))
		}
		.disabled(state.isSaving)
	}
}
#endif
