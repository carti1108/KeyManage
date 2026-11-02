import Foundation
import MicroRIBs
import KeyManageDomain
import PasswordGeneratorFeatureInterface

// MARK: - Component

@MainActor
final class PasswordGeneratorComponent: Component<PasswordGeneratorDependency> {
	var clipboardManager: ClipboardManagerProtocol {
		dependency.clipboardManager
	}
}

// MARK: - Builder

@MainActor
public final class PasswordGeneratorBuilder: Builder<PasswordGeneratorDependency>, PasswordGeneratorBuildable {
	public override init(dependency: PasswordGeneratorDependency) {
		super.init(dependency: dependency)
	}

	public func build(withListener listener: PasswordGeneratorListener) -> PasswordGeneratorRouting {
		let component = PasswordGeneratorComponent(dependency: dependency)
		let viewController = PasswordGeneratorViewController()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: component.clipboardManager,
			presenter: viewController
		)
		viewController.listener = interactor
		interactor.listener = listener
		let router = PasswordGeneratorRouter(
			interactor: interactor,
			viewController: viewController
		)
		return router
	}
}
