import Foundation
import MicroRIBs
import RootFeatureInterface

#if os(macOS)
import AppKit

@MainActor
final class RootViewController: NSViewController, RootViewControllable {
	private var currentChild: NSViewController?

	override func loadView() {
		self.view = NSView()
	}

	func replaceActiveView(with viewControllable: ViewControllable) {
		currentChild?.view.removeFromSuperview()
		currentChild?.removeFromParent()

		let child = viewControllable.nsViewController
		addChild(child)
		child.view.translatesAutoresizingMaskIntoConstraints = false
		view.addSubview(child.view)
		NSLayoutConstraint.activate([
			child.view.topAnchor.constraint(equalTo: view.topAnchor),
			child.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
			child.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
			child.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
		])
		currentChild = child
	}

	func clearActiveView() {
		currentChild?.view.removeFromSuperview()
		currentChild?.removeFromParent()
		currentChild = nil
	}
}
#elseif os(iOS)
import UIKit

@MainActor
final class RootViewController: UIViewController, RootViewControllable {
	private var currentChild: UIViewController?

	override func loadView() {
		self.view = UIView()
	}

	func replaceActiveView(with viewControllable: ViewControllable) {
		if let current = currentChild {
			current.willMove(toParent: nil)
			current.view.removeFromSuperview()
			current.removeFromParent()
		}

		let child = viewControllable.uiViewController
		addChild(child)
		child.view.translatesAutoresizingMaskIntoConstraints = false
		view.addSubview(child.view)
		NSLayoutConstraint.activate([
			child.view.topAnchor.constraint(equalTo: view.topAnchor),
			child.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
			child.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
			child.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
		])
		child.didMove(toParent: self)
		currentChild = child
	}

	func clearActiveView() {
		guard let current = currentChild else { return }
		current.willMove(toParent: nil)
		current.view.removeFromSuperview()
		current.removeFromParent()
		currentChild = nil
	}
}
#endif
