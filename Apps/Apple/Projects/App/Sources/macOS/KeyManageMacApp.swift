import SwiftUI
import KeyManageDomain
import KeyManageDesignSystem
import UnlockFeatureInterface
import UnlockFeatureImplementation

@main
struct KeyManageMacApp: App {
	var body: some Scene {
		WindowGroup {
			Text("KeyManage macOS Desktop App")
				.frame(minWidth: 800, minHeight: 600)
		}
	}
}
