import Foundation
import MainFeatureInterface

// MARK: - ViewState

struct MainViewState: Equatable, Sendable {
	var selectedTab: MainTab = .vault
}

// MARK: - ViewAction

enum MainViewAction: Equatable, Sendable {
	case viewDidLoad
	case lockVault
	case selectTab(MainTab)
}
