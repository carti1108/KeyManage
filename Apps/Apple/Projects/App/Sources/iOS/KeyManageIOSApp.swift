import SwiftUI
import UIKit
import MicroRIBs
import KeyManageDomain
import KeyManageDesignSystem
import UnlockFeatureInterface
import RootFeatureInterface
import RootFeatureImplementation

struct RootViewControllerRepresentable: UIViewControllerRepresentable {
	let viewController: UIViewController

	func makeUIViewController(context: Context) -> UIViewController {
		viewController
	}

	func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct KeyManageIOSApp: App {
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
			RootViewControllerRepresentable(viewController: routing.viewControllable.uiViewController)
				.ignoresSafeArea()
		}
	}
}
