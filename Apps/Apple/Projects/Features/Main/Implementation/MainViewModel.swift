import Foundation
import MicroRIBs
import MainFeatureInterface

@MainActor
final class MainViewModel: ObservableObject {
	typealias State = MainViewState
	typealias Action = MainViewAction

	weak var listener: MainPresentableListener?

	@Published var state: State
	@Published var vaultListViewControllable: ViewControllable?
	@Published var settingsViewControllable: ViewControllable?
	@Published var passwordGeneratorViewControllable: ViewControllable?

	var onLockRequested: (() -> Void)?
	var onTabSelected: ((MainTab) -> Void)?

	var selectedTab: MainTab {
		get { state.selectedTab }
		set { state.selectedTab = newValue }
	}

	init(
		listener: MainPresentableListener? = nil,
		state: State = .init()
	) {
		self.listener = listener
		self.state = state
	}

	convenience init(selectedTab: MainTab) {
		self.init(state: MainViewState(selectedTab: selectedTab))
	}

	func update(state: State) {
		self.state = state
	}

	func request(action: Action) {
		listener?.request(action: action)
		switch action {
		case .viewDidLoad:
			break
		case .lockVault:
			onLockRequested?()
		case .selectTab(let tab):
			state.selectedTab = tab
			onTabSelected?(tab)
		}
	}

	func lockVault() {
		request(action: .lockVault)
	}

	func selectTab(_ tab: MainTab) {
		request(action: .selectTab(tab))
	}
}
