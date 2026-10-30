import Foundation
import SwiftUI
import MicroRIBs
import KeyManageDomain
import KeyManageDesignSystem
import UnlockFeatureInterface

// MARK: - Component

@MainActor
final class UnlockComponent: Component<UnlockDependency> {}

// MARK: - Interactor

@MainActor
protocol UnlockInteractable: Interactable {
	var router: UnlockRouting? { get set }
	var listener: UnlockListener? { get set }
	func unlockSuccess()
}

@MainActor
final class UnlockInteractor: Interactor, UnlockInteractable {
	weak var router: UnlockRouting?
	weak var listener: UnlockListener?

	func unlockSuccess() {
		listener?.didUnlockVault()
	}
}

// MARK: - View & ViewControllable

struct UnlockView: View {
	var onUnlockTapped: (() -> Void)?

	var body: some View {
		VStack(spacing: 16) {
			Text("Unlock KeyManage Vault")
			Button("Unlock") {
				onUnlockTapped?()
			}
		}
		.padding()
	}
}

@MainActor
protocol UnlockViewControllable: ViewControllable {}

#if os(macOS)
import AppKit

@MainActor
final class UnlockViewController: NSHostingController<UnlockView>, UnlockViewControllable {
	init(onUnlock: @escaping () -> Void = {}) {
		super.init(rootView: UnlockView(onUnlockTapped: onUnlock))
	}

	@MainActor required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}
}
#elseif os(iOS)
import UIKit

@MainActor
final class UnlockViewController: UIHostingController<UnlockView>, UnlockViewControllable {
	init(onUnlock: @escaping () -> Void = {}) {
		super.init(rootView: UnlockView(onUnlockTapped: onUnlock))
	}

	@MainActor required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}
}
#endif

// MARK: - Router

@MainActor
final class UnlockRouter: ViewableRouter<UnlockInteractable, UnlockViewControllable>, UnlockRouting {
	override init(interactor: UnlockInteractable, viewController: UnlockViewControllable) {
		super.init(interactor: interactor, viewController: viewController)
	}
}

// MARK: - Builder

@MainActor
public final class UnlockBuilder: Builder<UnlockDependency>, UnlockBuildable {
	public override init(dependency: UnlockDependency) {
		super.init(dependency: dependency)
	}

	public func build(withListener listener: UnlockListener) -> UnlockRouting {
		let interactor = UnlockInteractor()
		interactor.listener = listener

		let viewController = UnlockViewController(onUnlock: { [weak interactor] in
			interactor?.unlockSuccess()
		})

		let router = UnlockRouter(interactor: interactor, viewController: viewController)
		interactor.router = router
		return router
	}
}
