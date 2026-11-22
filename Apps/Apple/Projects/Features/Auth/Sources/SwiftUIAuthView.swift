import KeyManageDesignSystem
import KeyManageShared
import RIBsKMPSwiftUI
import SwiftUI

/// 로그인·가입 화면의 SwiftUI 뷰(ComposeAuthView의 짝). 공유 Auth RIB이 상태를 넘기고 입력을 돌려받는다
@MainActor
@Observable
public final class SwiftUIAuthView: NSObject, @preconcurrency AuthViewContract, SwiftUIRenderable {
	@ObservationIgnored public var listener: (any AuthPresentableListener)?
	public private(set) var state: AuthViewState?

	override public init() {}

	public func render(authState state: AuthViewState) {
		self.state = state
	}

	func send(_ action: AuthViewAction) {
		listener?.handle(authAction: action)
	}

	public func makeView() -> AnyView {
		#if os(macOS)
		AnyView(DesktopAuthScreen(view: self))
		#else
		AnyView(AuthScreen(view: self))
		#endif
	}
}

/// 로그인은 가운데에 브랜드와 제목을 두고, 가입은 위쪽에 뒤로 가기와 제목을 둔다(목업 1·2단계)
private struct AuthScreen: View {
	let view: SwiftUIAuthView

	var body: some View {
		if let state = view.state {
			ScrollView {
				VStack(alignment: .leading, spacing: DesignToken.Spacing.space4) {
					header(state)
					form(state)
				}
				.frame(maxWidth: 400)
				.padding(DesignToken.Spacing.space4)
				.frame(maxWidth: .infinity)
			}
			.background(DesignToken.View.background)
			.alert(RollbackWarning.shared.TITLE, isPresented: sharedStateBinding(state.isConfirmingRollback) { view.send(AuthViewActionCancelRollback.shared) }) {
				Button(RollbackWarning.shared.ACCEPT, role: .destructive) { view.send(AuthViewActionAcceptRollback.shared) }
				Button(RollbackWarning.shared.CANCEL, role: .cancel) { view.send(AuthViewActionCancelRollback.shared) }
			} message: {
				Text(RollbackWarning.shared.BODY)
			}
		}
	}

	@ViewBuilder
	private func header(_ state: AuthViewState) -> some View {
		if state.isRegistering {
			HStack {
				Button {
					if !state.isLoading { view.send(AuthViewActionSwitchMode.shared) }
				} label: {
					Image(systemName: "chevron.left").frame(width: 44, height: 44)
				}
				.accessibilityLabel("뒤로")
				Text("계정 만들기")
					.font(DesignToken.Typography.title.weight(.bold))
					.accessibilityAddTraits(.isHeader)
			}
		} else {
			VStack(alignment: .leading, spacing: DesignToken.Spacing.space2) {
				Text("KeyManage").font(DesignToken.Typography.label.weight(.semibold))
				Text("볼트에 로그인")
					.font(DesignToken.Typography.title.weight(.bold))
					.accessibilityAddTraits(.isHeader)
			}
			.padding(.top, DesignToken.Spacing.space6)
		}
	}

	@ViewBuilder
	private func form(_ state: AuthViewState) -> some View {
		// 로그인 화면으로 돌아온 이유(세션 만료, 다른 기기의 비밀번호 변경 등)
		if let notice = state.notice {
			Text(notice)
				.font(DesignToken.Typography.caption)
				.foregroundColor(DesignToken.View.mutedForeground)
				.accessibilityIdentifier("auth.notice")
		}
		TextField("이메일", text: Binding(get: { state.email }, set: { view.send(AuthViewActionUpdateEmail(email: $0)) }))
			.emailInput()
			.kmFieldContainer()
			.disabled(state.isLoading)
			.accessibilityIdentifier("auth.email")
		KMPasswordField(
			text: Binding(get: { state.password }, set: { view.send(AuthViewActionUpdatePassword(password: $0)) }),
			placeholder: "마스터 비밀번호",
			showCopyAction: false
		)
		.disabled(state.isLoading)
		.accessibilityIdentifier("auth.password")
		if state.isRegistering {
			registerExtras(state)
		}
		if let error = state.error {
			ErrorText(error: error, identifier: "auth.error")
		}
		KMButton(
			state.isRegistering ? "가입" : "로그인",
			size: .lg,
			isLoading: state.isLoading,
			isDisabled: state.email.trimmingCharacters(in: .whitespaces).isEmpty || state.password.isEmpty,
			isFullWidth: true
		) { view.send(AuthViewActionSubmit.shared) }
			.accessibilityIdentifier("auth.submit")
		Button(state.isRegistering ? "이미 계정이 있어요" : "새 계정 만들기") { view.send(AuthViewActionSwitchMode.shared) }
			.frame(maxWidth: .infinity, minHeight: 44)
			.disabled(state.isLoading)
			.accessibilityIdentifier("auth.switchMode")
	}

	@ViewBuilder
	private func registerExtras(_ state: AuthViewState) -> some View {
		KMPasswordField(
			text: Binding(get: { state.confirmation }, set: { view.send(AuthViewActionUpdateConfirmation(confirmation: $0)) }),
			placeholder: "마스터 비밀번호 확인",
			showCopyAction: false
		)
		.disabled(state.isLoading)
		.accessibilityIdentifier("auth.confirmation")
		if state.showsStrengthGauge {
			KMGaugeBar(score: Int(state.passwordStrengthScore))
			if state.showsWeakPasswordWarning {
				Text(AuthNoticesKt.WEAK_MASTER_PASSWORD_WARNING)
					.font(DesignToken.Typography.caption)
					.foregroundColor(DesignToken.View.destructive)
			}
		}
		Text(MasterPasswordSetup.shared.BODY)
			.font(DesignToken.Typography.caption)
			.foregroundColor(DesignToken.View.mutedForeground)
	}
}
