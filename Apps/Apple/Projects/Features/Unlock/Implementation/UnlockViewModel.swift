import Foundation

@MainActor
final class UnlockViewModel: ObservableObject {
	typealias State = UnlockViewState
	typealias Action = UnlockViewAction

	weak var listener: UnlockPresentableListener?

	@Published var state: State

	var onUnlockPassword: ((String) -> Void)?
	var onUnlockBiometrics: (() -> Void)?

	var password: String {
		get { state.password }
		set { state.password = newValue }
	}
	var errorMessage: String? {
		get { state.errorMessage }
		set { state.errorMessage = newValue }
	}
	var isLoading: Bool {
		get { state.isLoading }
		set { state.isLoading = newValue }
	}
	var isBiometricsEnabled: Bool {
		state.isBiometricsEnabled
	}

	init(
		listener: UnlockPresentableListener? = nil,
		state: State = .init()
	) {
		self.listener = listener
		self.state = state
	}

	convenience init(
		isBiometricsEnabled: Bool = false,
		errorMessage: String? = nil,
		isLoading: Bool = false,
		onUnlockPassword: ((String) -> Void)? = nil,
		onUnlockBiometrics: (() -> Void)? = nil
	) {
		self.init(
			state: UnlockViewState(
				errorMessage: errorMessage,
				isLoading: isLoading,
				isBiometricsEnabled: isBiometricsEnabled
			)
		)
		self.onUnlockPassword = onUnlockPassword
		self.onUnlockBiometrics = onUnlockBiometrics
	}

	func update(state: State) {
		self.state = state
	}

	func request(action: Action) {
		if let listener {
			listener.request(action: action)
			return
		}
		switch action {
		case .viewDidLoad:
			break
		case .updatePassword(let password):
			state.password = password
		case .unlockWithPassword(let password):
			onUnlockPassword?(password)
		case .unlockWithBiometrics:
			onUnlockBiometrics?()
		case .clearPassword:
			state.password = ""
		}
	}

	func unlockWithPassword() {
		request(action: .unlockWithPassword(state.password))
	}

	func unlockWithBiometrics() {
		request(action: .unlockWithBiometrics)
	}

	func clearPassword() {
		request(action: .clearPassword)
	}
}
