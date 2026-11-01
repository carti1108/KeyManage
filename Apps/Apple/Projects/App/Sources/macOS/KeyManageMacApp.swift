import SwiftUI
import AppKit
import MicroRIBs
import KeyManageDomain
import KeyManageDesignSystem
import UnlockFeatureInterface
import RootFeatureInterface
import RootFeatureImplementation

struct RootViewControllerRepresentable: NSViewControllerRepresentable {
	let viewController: NSViewController

	func makeNSViewController(context: Context) -> NSViewController {
		viewController
	}

	func updateNSViewController(_ nsViewController: NSViewController, context: Context) {}
}

@main
struct KeyManageMacApp: App {
	@State private var routing: RootRouting

	init() {
		let dependency = AppDependency()
		let builder = RootBuilder(dependency: dependency)
		let router = builder.build(withListener: nil)
		router.interactable.activate()
		router.load()
		_routing = State(initialValue: router)
	}

	var body: some Scene {
		WindowGroup {
			RootViewControllerRepresentable(viewController: routing.viewControllable.nsViewController)
				.frame(minWidth: 800, minHeight: 600)
		}
	}
}
