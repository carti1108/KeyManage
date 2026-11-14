import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 볼트 목록 RIB의 뷰. 옛 Swift VaultListView 배치(검색, 항목 카드, 추가 버튼, 추가·수정 시트, 삭제 확인)를 따르고,
/// 공유 RIB의 상세 화면은 내비게이션으로 민다. 폼 안 비밀번호 생성기는 자식 RIB 화면을 슬롯에 보인다
@MainActor
@Observable
final class SharedVaultListScreen: NSObject, @preconcurrency VaultListViewContract, SwiftUIRenderable {
	@ObservationIgnored weak var listener: (any VaultListPresentableListener)?
	@ObservationIgnored let generatorSlot = RIBScreenSlot()
	@ObservationIgnored private let activity: @MainActor () -> Void
	private(set) var state: VaultListViewState?

	init(activity: @escaping @MainActor () -> Void) {
		self.activity = activity
	}

	func renderVaultList(state: VaultListViewState) {
		self.state = state
	}

	func showGenerator(view: any ViewControllable) {
		generatorSlot.show(anyView: view)
	}

	func hideGenerator(view: any ViewControllable) {
		generatorSlot.hide(anyView: view)
	}

	func send(_ action: any VaultListViewAction) {
		activity()
		listener?.handleVaultList(action: action)
	}

	func makeView() -> AnyView {
		AnyView(SharedVaultListView(screen: self))
	}
}

private struct SharedVaultListView: View {
	let screen: SharedVaultListScreen

	var body: some View {
		if let state = screen.state {
			SharedVaultListContent(screen: screen, state: state)
		}
	}
}

private struct SharedVaultListContent: View {
	let screen: SharedVaultListScreen
	let state: VaultListViewState

	private var isShowingDetail: Bool {
		state.selected != nil && (state.screen == .detail || state.screen == .edit)
	}

	private var isShowingForm: Bool {
		state.screen == .add || state.screen == .edit
	}

	var body: some View {
		VStack(spacing: 0) {
			if let error = state.copyError {
				SharedBanner(kind: .error, text: error)
					.padding(.horizontal)
					.padding(.top, 8)
					.accessibilityIdentifier("vaultCopyError")
			}
			itemsList
		}
		.searchable(
			text: Binding(get: { state.query }, set: { if $0 != state.query { screen.send(VaultListViewActionSearch(query: $0)) } }),
			placement: .navigationBarDrawer(displayMode: .always),
			prompt: "서비스명, 아이디 검색..."
		)
		.toolbar {
			ToolbarItem(placement: .primaryAction) {
				if state.canEdit {
					Button { screen.send(VaultListViewActionStartAdd.shared) } label: {
						Image(systemName: "plus")
					}
					.accessibilityLabel("항목 추가")
					.accessibilityIdentifier("vaultAddButton")
				}
			}
		}
		.navigationDestination(isPresented: Binding(
			get: { isShowingDetail },
			set: { if !$0, state.screen == .detail { screen.send(VaultListViewActionBack.shared) } }
		)) {
			if let item = state.selected {
				SharedVaultItemDetail(screen: screen, state: state, item: item)
			}
		}
		.sheet(isPresented: Binding(
			get: { isShowingForm && !isShowingDetail },
			set: { if !$0, isShowingForm { screen.send(VaultListViewActionCancelForm.shared) } }
		)) {
			SharedVaultItemFormSheet(screen: screen, state: state)
		}
		.alert(
			VaultListMessages.DeleteConfirm.shared.TITLE,
			isPresented: deleteBinding
		) {
			deleteButtons
		} message: {
			Text(VaultListMessages.DeleteConfirm.shared.BODY)
		}
		.background(DesignToken.View.background)
	}

	private var deleteBinding: Binding<Bool> {
		Binding(
			get: { state.isConfirmingDelete && !isShowingDetail },
			set: { if !$0, state.isConfirmingDelete { screen.send(VaultListViewActionCancelDelete.shared) } }
		)
	}

	@ViewBuilder private var deleteButtons: some View {
		Button(VaultListMessages.DeleteConfirm.shared.CANCEL, role: .cancel) { screen.send(VaultListViewActionCancelDelete.shared) }
		Button(VaultListMessages.DeleteConfirm.shared.CONFIRM, role: .destructive) { screen.send(VaultListViewActionConfirmDelete.shared) }
			.accessibilityIdentifier("vaultDeleteConfirmButton")
	}

	@ViewBuilder private var itemsList: some View {
		if let message = state.emptyMessage {
			ContentUnavailableView {
				Label(message, systemImage: "tray").accessibilityIdentifier("vaultEmptyMessage")
			} actions: {
				if state.canEdit && state.isVaultEmpty {
					Button("첫 항목 추가하기") { screen.send(VaultListViewActionStartAdd.shared) }
						.buttonStyle(.borderedProminent)
				}
			}
		} else {
			ScrollView {
				LazyVStack(spacing: 12) {
					ForEach(state.items, id: \.id) { item in
						SharedVaultItemCard(screen: screen, item: item)
					}
				}
				.padding(.horizontal)
				.padding(.vertical, 8)
			}
		}
	}
}

/// 목록의 항목 카드. 누르면 상세로 가고, 아이디·비밀번호는 바로 복사한다
private struct SharedVaultItemCard: View {
	let screen: SharedVaultListScreen
	let item: VaultItem
	@State private var isPasswordRevealed = false

	var body: some View {
		KMCard(padding: .md) {
			VStack(alignment: .leading, spacing: 10) {
				Button { screen.send(VaultListViewActionOpenItem(id: item.id)) } label: {
					HStack {
						Text(item.serviceName ?? "")
							.font(.headline.bold())
							.foregroundStyle(DesignToken.View.foreground)
						Spacer()
						if let url = item.url, !url.isEmpty {
							Text(url).font(.caption).foregroundStyle(DesignToken.View.mutedForeground).lineLimit(1)
						}
						Image(systemName: "chevron.right").foregroundStyle(DesignToken.View.mutedForeground)
					}
					.contentShape(Rectangle())
				}
				.buttonStyle(.plain)
				.accessibilityIdentifier("vaultItem")
				Divider()
				HStack {
					let username = item.username ?? ""
					Label(username.isEmpty ? "(아이디 없음)" : username, systemImage: "person.fill")
						.font(.subheadline)
						.foregroundStyle(username.isEmpty ? DesignToken.View.mutedForeground : DesignToken.View.foreground)
					Spacer()
					if !username.isEmpty {
						KMButton("아이디 복사", variant: .outline, size: .sm) {
							screen.send(VaultListViewActionCopyUsername(itemId: item.id))
						}
						.accessibilityIdentifier("vaultCopyUsernameButton")
					}
				}
				HStack {
					let password = item.password ?? ""
					HStack(spacing: 6) {
						Image(systemName: "key.fill").foregroundStyle(DesignToken.View.mutedForeground)
						Text(maskedPassword(password))
							.font(isPasswordRevealed ? .system(.subheadline, design: .monospaced) : .subheadline)
							.foregroundStyle(password.isEmpty ? DesignToken.View.mutedForeground : DesignToken.View.foreground)
							.lineLimit(1)
							.truncationMode(.middle)
						if !password.isEmpty {
							Button { isPasswordRevealed.toggle() } label: {
								Image(systemName: isPasswordRevealed ? DesignToken.Icon.passwordHide : DesignToken.Icon.passwordShow)
									.foregroundStyle(DesignToken.View.mutedForeground)
							}
							.buttonStyle(.plain)
							.accessibilityLabel(isPasswordRevealed ? "비밀번호 숨기기" : "비밀번호 보기")
						}
					}
					Spacer()
					if !password.isEmpty {
						KMButton("비밀번호 복사", variant: .primary, size: .sm) {
							screen.send(VaultListViewActionCopyPassword(itemId: item.id))
						}
						.accessibilityIdentifier("vaultCopyPasswordButton")
					}
				}
				if let notes = item.notes, !notes.isEmpty {
					Text(notes).font(.caption).foregroundStyle(DesignToken.View.mutedForeground).lineLimit(2)
				}
			}
		}
	}

	private func maskedPassword(_ password: String) -> String {
		if password.isEmpty { return "(비밀번호 없음)" }
		return isPasswordRevealed ? password : String(repeating: "•", count: min(password.count, 12))
	}
}

/// 항목 상세. 공유 RIB의 DETAIL 화면이다. 수정은 시트로, 삭제는 확인을 거친다
private struct SharedVaultItemDetail: View {
	let screen: SharedVaultListScreen
	let state: VaultListViewState
	let item: VaultItem

	var body: some View {
		ScrollView { details.padding() }
			.navigationTitle(item.serviceName ?? "")
			.toolbar { editButton }
			.sheet(isPresented: editBinding) { SharedVaultItemFormSheet(screen: screen, state: state) }
			.alert(VaultListMessages.DeleteConfirm.shared.TITLE, isPresented: deleteBinding) {
				Button(VaultListMessages.DeleteConfirm.shared.CANCEL, role: .cancel) { screen.send(VaultListViewActionCancelDelete.shared) }
				Button(VaultListMessages.DeleteConfirm.shared.CONFIRM, role: .destructive) { screen.send(VaultListViewActionConfirmDelete.shared) }
					.accessibilityIdentifier("vaultDeleteConfirmButton")
			} message: {
				Text(VaultListMessages.DeleteConfirm.shared.BODY)
			}
	}

	private var editBinding: Binding<Bool> {
		Binding(
			get: { state.screen == .edit },
			set: { if !$0, state.screen == .edit { screen.send(VaultListViewActionCancelForm.shared) } }
		)
	}

	private var deleteBinding: Binding<Bool> {
		Binding(
			get: { state.isConfirmingDelete },
			set: { if !$0, state.isConfirmingDelete { screen.send(VaultListViewActionCancelDelete.shared) } }
		)
	}

	@ToolbarContentBuilder private var editButton: some ToolbarContent {
		if state.canEdit {
			ToolbarItem(placement: .primaryAction) {
				Button("수정") { screen.send(VaultListViewActionStartEdit.shared) }
					.disabled(state.isSaving)
					.accessibilityIdentifier("vaultEditButton")
			}
		}
	}

	private var details: some View {
			VStack(alignment: .leading, spacing: 16) {
				if let error = state.saveError, state.screen == .detail {
					SharedErrorText(message: error)
				}
				SharedVaultItemCard(screen: screen, item: item)
				if let url = item.url, !url.isEmpty {
					LabeledContent("웹사이트 URL", value: url)
				}
				if let notes = item.notes, !notes.isEmpty {
					VStack(alignment: .leading, spacing: 4) {
						SharedFieldLabel("메모")
						Text(notes).font(.body).foregroundStyle(DesignToken.View.foreground)
					}
				}
				if state.canEdit {
					KMButton("이 항목 삭제", variant: .destructive, icon: "trash", isDisabled: state.isSaving) {
						screen.send(VaultListViewActionRequestDelete.shared)
					}
					.accessibilityIdentifier("vaultDeleteButton")
				}
			}
	}
}

/// 추가·수정 폼 시트. 옛 Swift itemFormModalView 배치다
private struct SharedVaultItemFormSheet: View {
	let screen: SharedVaultListScreen
	let state: VaultListViewState

	var body: some View {
		NavigationStack {
			ScrollView {
				VStack(spacing: 12) {
					if let error = state.saveError {
						SharedErrorText(message: error).accessibilityIdentifier("vaultSaveError")
					}
					field("서비스 이름 *", .serviceName, state.form.serviceName, placeholder: "예: Google, GitHub", id: "vaultFormServiceName")
					field("아이디 / 이메일", .username, state.form.username, placeholder: "사용자 아이디 또는 이메일", id: "vaultFormUsername")
					passwordField
					field("웹사이트 URL", .url, state.form.url, placeholder: "https://example.com", id: "vaultFormUrl")
					field("메모", .notes, state.form.notes, placeholder: "추가 메모 (선택 사항)", id: "vaultFormNotes")
				}
				.padding(20)
			}
			.navigationTitle(state.screen == .edit ? "항목 수정" : "새 볼트 항목")
			.navigationBarTitleDisplayMode(.inline)
			.toolbar {
				ToolbarItem(placement: .cancellationAction) {
					Button("취소") { screen.send(VaultListViewActionCancelForm.shared) }
						.disabled(state.isSaving)
						.accessibilityIdentifier("vaultFormCancelButton")
				}
				ToolbarItem(placement: .confirmationAction) {
					if state.isSaving {
						ProgressView()
					} else {
						Button("저장") { screen.send(VaultListViewActionSubmitForm.shared) }
							.font(.body.weight(.bold))
							.accessibilityIdentifier("vaultFormSaveButton")
					}
				}
			}
		}
		.interactiveDismissDisabled(state.isSaving)
	}

	private var passwordField: some View {
		VStack(alignment: .leading, spacing: 4) {
			HStack {
				Text("비밀번호").font(.caption.bold()).foregroundStyle(DesignToken.View.foreground)
				Spacer()
				if !state.isGeneratorOpen {
					KMButton("자동 생성", variant: .outline, size: .sm) { screen.send(VaultListViewActionOpenGenerator.shared) }
						.accessibilityIdentifier("vaultFormGenerateButton")
				}
			}
			SharedTextInput(title: "비밀번호 입력 또는 자동 생성", value: state.form.password, isSecure: true, isMonospaced: true) {
				screen.send(VaultListViewActionEditForm(field: .password, value: $0))
			}
			.accessibilityIdentifier("vaultFormPassword")
			if state.isGeneratorOpen {
				KMCard { RIBSlotView(slot: screen.generatorSlot) }
			}
			// 약한 비밀번호도 저장은 허용하고 안내만 한다 (GEN-001)
			if state.showsFormStrength {
				KMGaugeBar(score: Int(state.formPasswordScore))
			}
			if state.showsFormWeakWarning {
				notice(VaultListMessages.shared.WEAK_PASSWORD_BODY)
			}
			if state.showsFormSequentialDigitsNotice {
				notice(VaultListMessages.shared.SEQUENTIAL_DIGITS_NOTICE).accessibilityIdentifier("vaultFormSequentialNotice")
			}
		}
	}

	private func notice(_ text: String) -> some View {
		Text(text)
			.font(.footnote)
			.foregroundStyle(DesignToken.View.mutedForeground)
			.fixedSize(horizontal: false, vertical: true)
	}

	private func field(_ title: String, _ field: VaultItemFormField, _ value: String, placeholder: String, id: String) -> some View {
		VStack(alignment: .leading, spacing: 4) {
			Text(title).font(.caption.bold()).foregroundStyle(DesignToken.View.foreground)
			SharedTextInput(title: placeholder, value: value) {
				screen.send(VaultListViewActionEditForm(field: field, value: $0))
			}
			.textInputAutocapitalization(.never)
			.autocorrectionDisabled()
			.accessibilityIdentifier(id)
		}
	}
}
