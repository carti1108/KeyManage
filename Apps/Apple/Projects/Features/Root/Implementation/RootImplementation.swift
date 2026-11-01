import Foundation
import SwiftUI
import MicroRIBs
import KeyManageDomain
import KeyManageDesignSystem
import UnlockFeatureInterface
import RootFeatureInterface

// MARK: - Component

@MainActor
final class RootComponent: Component<RootDependency> {
	var lockManager: VaultLockManagerProtocol {
		dependency.lockManager
	}
	var unlockBuilder: UnlockBuildable {
		dependency.unlockBuilder
	}
}

// MARK: - Interactor

@MainActor
protocol RootInteractable: Interactable, UnlockListener {
	var router: RootRouting? { get set }
	var listener: RootListener? { get set }
	func didLockVault()
}

@MainActor
final class RootInteractor: Interactor, RootInteractable {
	weak var router: RootRouting?
	weak var listener: RootListener?
	let lockManager: VaultLockManagerProtocol

	init(lockManager: VaultLockManagerProtocol) {
		self.lockManager = lockManager
		super.init()
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		// RED 단계 스텁: 라우팅 미수행
	}

	func didUnlockVault() {
		// RED 단계 스텁: Main 라우팅 미수행
	}

	func didLockVault() {
		// RED 단계 스텁: Unlock 라우팅 미수행
	}
}

// MARK: - View & ViewControllable

public struct RootView: View {
	public init() {}
	public var body: some View {
		Text("Root Container")
	}
}

@MainActor
protocol RootViewControllable: ViewControllable {
	func replaceActiveView(with viewControllable: ViewControllable)
}

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
		currentChild?.view.removeFromSuperview()
		currentChild?.removeFromParent()

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
		currentChild = child
	}
}
#endif

// MARK: - Router

@MainActor
final class RootRouter: ViewableRouter<RootInteractable, RootViewControllable>, RootRouting {
	let unlockBuilder: UnlockBuildable
	private(set) var unlockRouting: UnlockRouting?

	init(
		interactor: RootInteractable,
		viewController: RootViewControllable,
		unlockBuilder: UnlockBuildable
	) {
		self.unlockBuilder = unlockBuilder
		super.init(interactor: interactor, viewController: viewController)
	}

	func routeToUnlock() {
		// RED 단계 스텁: Unlock RIB 부착 미구현
	}

	func routeToMain() {
		// RED 단계 스텁: Main RIB 부착 미구현
	}
}

// MARK: - Builder

@MainActor
public final class RootBuilder: Builder<RootDependency>, RootBuildable {
	public override init(dependency: RootDependency) {
		super.init(dependency: dependency)
	}

	public func build(withListener listener: RootListener?) -> RootRouting {
		let component = RootComponent(dependency: dependency)
		let interactor = RootInteractor(lockManager: component.lockManager)
		interactor.listener = listener

		let viewController = RootViewController()
		let router = RootRouter(
			interactor: interactor,
			viewController: viewController,
			unlockBuilder: component.unlockBuilder
		)
		interactor.router = router
		return router
	}
}
