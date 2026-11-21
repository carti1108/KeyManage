import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 볼트 목록·상세·폼의 SwiftUI 뷰(ComposeVaultListView의 짝). 폼 안의 비밀번호 생성기는 자식 RIB이 `generatorSlot`에 보인다
@MainActor
@Observable
public final class SwiftUIVaultListView: NSObject, @preconcurrency VaultListViewContract, SwiftUIRenderable {
	@ObservationIgnored public var listener: (any VaultListPresentableListener)?
	public private(set) var state: VaultListViewState?
	let generatorSlot = RIBScreenSlot()

	override public init() {}

	public func render(vaultListState state: VaultListViewState) {
		self.state = state
	}

	public func showGenerator(view: any ViewControllable) {
		generatorSlot.show(anyView: view)
	}

	public func hideGenerator(view: any ViewControllable) {
		generatorSlot.hide(anyView: view)
	}

	func send(_ action: VaultListViewAction) {
		listener?.handle(vaultListAction: action)
	}

	public func makeView() -> AnyView {
		AnyView(VaultListContent(view: self))
	}
}

/// 상태의 `screen`에 따라 목록·상세·폼을 그린다. 생성기가 열려 있으면 그 화면만 보인다
private struct VaultListContent: View {
	let view: SwiftUIVaultListView

	var body: some View {
		if view.generatorSlot.current != nil {
			RIBSlotView(slot: view.generatorSlot)
		} else if let state = view.state {
			screen(state)
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

	@ViewBuilder
	private func screen(_ state: VaultListViewState) -> some View {
		switch state.screen {
		case .detail:
			if let item = state.selected {
				ItemDetailScreen(item: item, state: state, send: view.send)
			}
		case .add, .edit:
			ItemFormScreen(state: state, send: view.send)
		default:
			ItemListScreen(state: state, send: view.send)
		}
	}
}

private struct ItemListScreen: View {
	let state: VaultListViewState
	let send: (VaultListViewAction) -> Void

	var body: some View {
		ZStack(alignment: .bottomTrailing) {
			ScrollView {
				LazyVStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
					Text("볼트")
						.font(DesignToken.Typography.title.weight(.bold))
						.accessibilityAddTraits(.isHeader)
					TextField("서비스 이름이나 아이디", text: Binding(get: { state.query }, set: { send(VaultListViewActionSearch(query: $0)) }))
						.textInputAutocapitalization(.never)
						.autocorrectionDisabled()
						.kmFieldContainer()
						.accessibilityLabel("검색")
						.accessibilityIdentifier("vault.search")
					if let copyError = state.copyError {
						WarningText(text: copyError)
					}
					if let empty = state.emptyMessage {
						Text(empty)
							.font(DesignToken.Typography.body)
							.foregroundColor(DesignToken.View.mutedForeground)
							.padding(.vertical, DesignToken.Spacing.space6)
							.accessibilityIdentifier("vault.empty")
					}
					ForEach(state.items, id: \.id) { item in
						ItemRow(item: item, send: send)
					}
				}
				.padding(DesignToken.Spacing.space4)
			}
			if state.canEdit {
				Button { send(VaultListViewActionStartAdd.shared) } label: {
					Image(systemName: "plus")
						.font(.title2.weight(.semibold))
						.foregroundColor(DesignToken.View.primaryForeground)
						.frame(width: 56, height: 56)
						.background(DesignToken.View.primary)
						.clipShape(Circle())
				}
				.padding(DesignToken.Spacing.space5)
				.accessibilityLabel("항목 추가")
				.accessibilityIdentifier("vault.add")
			}
		}
	}
}

private struct ItemRow: View {
	let item: VaultItem
	let send: (VaultListViewAction) -> Void

	var body: some View {
		let name = item.serviceName ?? ""
		KMCard(padding: .sm, isInteractive: true) {
			HStack(spacing: DesignToken.Spacing.space3) {
				Button { send(VaultListViewActionOpenItem(id: item.id)) } label: {
					HStack(spacing: DesignToken.Spacing.space3) {
						Text(String(name.prefix(1)))
							.font(DesignToken.Typography.label.weight(.semibold))
							.frame(width: 36, height: 36)
							.overlay(Circle().stroke(DesignToken.View.border, lineWidth: 1))
						VStack(alignment: .leading) {
							Text(name).font(DesignToken.Typography.label.weight(.semibold))
							Text((item.username ?? "").isEmpty ? "(아이디 없음)" : item.username ?? "")
								.font(DesignToken.Typography.caption)
								.foregroundColor(DesignToken.View.mutedForeground)
						}
						Spacer()
					}
					.contentShape(Rectangle())
				}
				.buttonStyle(.plain)
				.accessibilityIdentifier("vault.item.\(name)")
				Button { send(VaultListViewActionCopyPassword(itemId: item.id)) } label: {
					Image(systemName: DesignToken.Icon.clipboardCopy).frame(width: 44, height: 44)
				}
				.foregroundColor(DesignToken.View.mutedForeground)
				.accessibilityLabel("\(name) 비밀번호 복사")
			}
		}
	}
}

/// 항목 상세(목업 5단계). 비밀번호는 가린 채 보이고, 복사한 값은 공유 RIB이 30초 뒤 비운다
private struct ItemDetailScreen: View {
	let item: VaultItem
	let state: VaultListViewState
	let send: (VaultListViewAction) -> Void

	var body: some View {
		let username = item.username ?? ""
		let password = item.password ?? ""
		ScrollView {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				ScreenBar(title: item.serviceName ?? "", isDisabled: state.isSaving, onBack: { send(VaultListViewActionBack.shared) }) {
					if state.canEdit {
						Button("수정") { send(VaultListViewActionStartEdit.shared) }
							.disabled(state.isSaving)
							.accessibilityIdentifier("vault.edit")
					}
				}
				DetailRow(label: "서비스 이름", value: item.serviceName ?? "")
				DetailRow(label: "아이디", value: username.isEmpty ? "(아이디 없음)" : username)
				if let url = item.url, !url.isEmpty {
					DetailRow(label: "URL", value: url)
				}
				KMPasswordField(text: .constant(password), placeholder: "비밀번호", onCopy: { send(VaultListViewActionCopyPassword(itemId: item.id)) })
				if !password.isEmpty {
					StrengthNotes(password: password)
				}
				if let notes = item.notes, !notes.isEmpty {
					KMCard {
						VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
							Text("메모").font(DesignToken.Typography.caption).foregroundColor(DesignToken.View.mutedForeground)
							Text(notes).font(DesignToken.Typography.body)
						}
						.frame(maxWidth: .infinity, alignment: .leading)
					}
				}
				if let copyError = state.copyError {
					WarningText(text: copyError)
				}
				if let error = state.saveError {
					ErrorText(error: error, identifier: "vault.saveError")
				}
				HStack(spacing: DesignToken.Spacing.space2) {
					KMButton("아이디 복사", variant: .outline, isDisabled: username.isEmpty, isFullWidth: true) {
						send(VaultListViewActionCopyUsername(itemId: item.id))
					}
					if state.canEdit {
						KMButton("항목 삭제", variant: .destructive, isLoading: state.isSaving, isFullWidth: true) {
							send(VaultListViewActionRequestDelete.shared)
						}
						.accessibilityIdentifier("vault.delete")
					}
				}
			}
			.padding(DesignToken.Spacing.space4)
		}
	}
}

/// 항목 추가·수정 폼
private struct ItemFormScreen: View {
	let state: VaultListViewState
	let send: (VaultListViewAction) -> Void

	var body: some View {
		let form = state.form
		ScrollView {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
				ScreenBar(
					title: state.screen == .add ? "항목 추가" : "항목 수정",
					isDisabled: state.isSaving,
					onBack: { send(VaultListViewActionCancelForm.shared) }
				) { EmptyView() }
				input("서비스 이름", form.serviceName, field: .serviceName)
				input("아이디", form.username, field: .username)
				KMPasswordField(
					text: Binding(get: { form.password }, set: { send(VaultListViewActionEditForm(field: .password, value: $0)) }),
					placeholder: "비밀번호",
					showCopyAction: false
				)
				.disabled(state.isSaving)
				.accessibilityIdentifier("vault.form.password")
				if state.showsFormStrength {
					StrengthNotes(password: form.password)
				}
				KMButton("비밀번호 생성", variant: .outline, isDisabled: state.isSaving, isFullWidth: true) {
					send(VaultListViewActionOpenGenerator.shared)
				}
				.accessibilityIdentifier("vault.form.generator")
				input("URL", form.url, field: .url)
				TextField("메모", text: binding(form.notes, field: .notes), axis: .vertical)
					.lineLimit(3...)
					.padding(DesignToken.Spacing.space3)
					.background(DesignToken.View.muted)
					.clipShape(RoundedRectangle(cornerRadius: DesignToken.Radius.lg))
					.disabled(state.isSaving)
				if let error = state.saveError {
					ErrorText(error: error, identifier: "vault.saveError")
				}
				KMButton("저장", size: .lg, isLoading: state.isSaving, isFullWidth: true) { send(VaultListViewActionSubmitForm.shared) }
					.accessibilityIdentifier("vault.form.save")
			}
			.padding(DesignToken.Spacing.space4)
		}
	}

	private func input(_ label: String, _ value: String, field: VaultItemFormField) -> some View {
		TextField(label, text: binding(value, field: field))
			.textInputAutocapitalization(.never)
			.autocorrectionDisabled()
			.kmFieldContainer()
			.disabled(state.isSaving)
			.accessibilityIdentifier("vault.form.\(field.name.lowercased())")
	}

	private func binding(_ value: String, field: VaultItemFormField) -> Binding<String> {
		Binding(get: { value }, set: { send(VaultListViewActionEditForm(field: field, value: $0)) })
	}
}

/// 상세·폼 위쪽의 뒤로 가기, 제목, 오른쪽 동작
private struct ScreenBar<Actions: View>: View {
	let title: String
	let isDisabled: Bool
	let onBack: () -> Void
	@ViewBuilder let actions: () -> Actions

	var body: some View {
		HStack {
			Button(action: onBack) { Image(systemName: "chevron.left").frame(width: 44, height: 44) }
				.disabled(isDisabled)
				.accessibilityLabel("뒤로")
				.accessibilityIdentifier("vault.back")
			Text(title)
				.font(DesignToken.Typography.title.weight(.bold))
				.lineLimit(1)
				.accessibilityAddTraits(.isHeader)
			Spacer()
			actions()
		}
	}
}

private struct DetailRow: View {
	let label: String
	let value: String

	var body: some View {
		HStack {
			Text(label).font(DesignToken.Typography.body)
			Spacer()
			Text(value).font(DesignToken.Typography.body).foregroundColor(DesignToken.View.mutedForeground)
		}
		.frame(minHeight: 44)
		.accessibilityElement(children: .combine)
	}
}

/// 강도 게이지와 안내(GEN-001). 안내만 하고 저장은 막지 않는다
private struct StrengthNotes: View {
	let password: String

	var body: some View {
		let score = PasswordStrengthKt.scorePassword(password: password)
		VStack(alignment: .leading, spacing: DesignToken.Spacing.space1) {
			KMGaugeBar(score: Int(score))
			if PasswordStrengthKt.isWeak(score: score) {
				WarningText(text: VaultListMessages.shared.WEAK_PASSWORD_BODY)
			}
			if PasswordStrengthKt.hasSequentialDigits(password: password) {
				Text(VaultListMessages.shared.SEQUENTIAL_DIGITS_NOTICE)
					.font(DesignToken.Typography.caption)
					.foregroundColor(DesignToken.View.mutedForeground)
			}
		}
	}
}

private struct WarningText: View {
	let text: String

	var body: some View {
		Text(text)
			.font(DesignToken.Typography.caption)
			.foregroundColor(DesignToken.View.destructive)
	}
}
