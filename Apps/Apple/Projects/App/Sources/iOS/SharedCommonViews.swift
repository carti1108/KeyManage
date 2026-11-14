import KeyManageDesignSystem
import KeyManageShared
import SwiftUI

/// 공유 코드의 오류 문구. 제목이 있으면 함께 보인다. 옛 Swift 화면의 오류 배너 모양이다
struct SharedErrorText: View {
	let message: ErrorMessage

	var body: some View {
		SharedBanner(kind: .error) {
			VStack(alignment: .leading, spacing: 4) {
				if let title = message.title {
					Text(title).bold()
				}
				Text(message.body)
			}
		}
	}
}

/// 상태 안내 띠. 문구는 공유 코드가 정한다
struct SharedBanner<Content: View>: View {
	enum Kind {
		case info, warning, error, offline

		var icon: String {
			switch self {
			case .info: "info.circle.fill"
			case .warning, .error: DesignToken.Icon.warning
			case .offline: DesignToken.Icon.offlineBanner
			}
		}

		var tint: Color {
			switch self {
			case .info: DesignToken.View.accent
			case .warning, .offline: .orange
			case .error: DesignToken.View.destructive
			}
		}
	}

	let kind: Kind
	@ViewBuilder let content: () -> Content

	var body: some View {
		HStack(alignment: .top, spacing: 10) {
			Image(systemName: kind.icon).foregroundStyle(kind.tint).accessibilityHidden(true)
			content()
				.font(.footnote)
				.foregroundStyle(kind == .error ? kind.tint : DesignToken.View.foreground)
				.fixedSize(horizontal: false, vertical: true)
			Spacer(minLength: 0)
		}
		.padding(12)
		.frame(maxWidth: .infinity, alignment: .leading)
		.background(kind.tint.opacity(0.1))
		.clipShape(RoundedRectangle(cornerRadius: 8))
		.accessibilityElement(children: .combine)
	}
}

extension SharedBanner where Content == Text {
	init(kind: Kind, text: String) {
		self.init(kind: kind) { Text(text) }
	}
}

/// 입력 칸 위 제목. 옛 Swift 폼과 같은 모양이다
struct SharedFieldLabel: View {
	let title: String

	init(_ title: String) {
		self.title = title
	}

	var body: some View {
		Text(title).font(.subheadline.bold()).foregroundStyle(DesignToken.View.foreground)
	}
}

/// 서버 볼트가 이전 상태로 돌아갔다는 경고를 받아들일지 묻는다 (VLT-001)
struct SharedRollbackPrompt: View {
	let accept: () -> Void
	let cancel: () -> Void

	var body: some View {
		VStack(alignment: .leading, spacing: 8) {
			Text(RollbackWarning.shared.TITLE).bold().foregroundStyle(DesignToken.View.destructive)
			Text(RollbackWarning.shared.BODY)
			HStack {
				Button(RollbackWarning.shared.ACCEPT, action: accept).accessibilityIdentifier("rollbackAcceptButton")
				Button(RollbackWarning.shared.CANCEL, role: .cancel, action: cancel).accessibilityIdentifier("rollbackCancelButton")
			}
		}
	}
}

/// 올리지 못한 변경 사항이 사라진다고 알리고 로그아웃할지 묻는다 (VLT-002)
struct SharedLogoutPrompt: View {
	let confirm: () -> Void
	let cancel: () -> Void

	var body: some View {
		VStack(alignment: .leading, spacing: 8) {
			Text(PendingLogoutConfirm.shared.TITLE).bold()
			Text(PendingLogoutConfirm.shared.BODY)
			HStack {
				Button(PendingLogoutConfirm.shared.CONFIRM, role: .destructive, action: confirm)
					.accessibilityIdentifier("logoutConfirmButton")
				Button(PendingLogoutConfirm.shared.CANCEL, role: .cancel, action: cancel)
					.accessibilityIdentifier("logoutCancelButton")
			}
		}
	}
}

/// 공유 코드 상태와 이어진 입력 칸. 글자는 이 뷰가 먼저 받고, 바뀌는 즉시(같은 입력 이벤트 안에서) `onEdit`으로 보낸다.
/// `onChange`로 늦게 보내면 마지막 글자를 보내기 전에 제출 버튼이 먼저 처리될 수 있다.
/// 공유 코드를 거쳐 돌아온 값으로 칸을 덮으면 빠른 입력에서 글자가 빠지므로, 보낸 적 없는 값을 공유 코드가 정했을 때(지움 등)만 칸을 고친다
struct SharedTextInput: View {
	let title: String
	let value: String
	var isSecure = false
	var isMonospaced = false
	let onEdit: (String) -> Void
	@State private var text = ""
	@State private var sent: Set<String> = []

	var body: some View {
		Group {
			if isSecure {
				SecureField(title, text: edits)
			} else {
				TextField(title, text: edits)
			}
		}
		.font(isMonospaced ? DesignToken.Typography.passwordMonospaced : DesignToken.Typography.body)
		.padding(10)
		.background(DesignToken.View.card)
		.clipShape(RoundedRectangle(cornerRadius: 6))
		.overlay(RoundedRectangle(cornerRadius: 6).stroke(DesignToken.View.border, lineWidth: 1))
		.onAppear { text = value }
		.onChange(of: value) { _, shared in
			// 이 칸이 보낸 값이 돌아온 것이면 그사이 더 친 글자를 지키고, 공유 코드가 바꾼 값이면 따른다
			if sent.remove(shared) == nil, shared != text { text = shared }
			if shared == text { sent.removeAll() }
		}
	}

	private var edits: Binding<String> {
		Binding(
			get: { text },
			set: { edited in
				text = edited
				guard edited != value else { return }
				sent.insert(edited)
				onEdit(edited)
			}
		)
	}
}
