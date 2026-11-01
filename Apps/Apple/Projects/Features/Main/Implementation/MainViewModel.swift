import Foundation
import MainFeatureInterface

@MainActor
final class MainViewModel: ObservableObject {
	@Published var selectedTab: MainTab = .vault
	var onLockRequested: (() -> Void)?
	var onTabSelected: ((MainTab) -> Void)?

	init(selectedTab: MainTab = .vault) {
		self.selectedTab = selectedTab
	}

	func lockVault() {
		onLockRequested?()
	}

	func selectTab(_ tab: MainTab) {
		selectedTab = tab
		onTabSelected?(tab)
	}
}
