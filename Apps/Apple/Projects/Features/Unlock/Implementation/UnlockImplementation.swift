import Foundation
import SwiftUI
import MicroRIBs
import KeyManageDomain
import KeyManageDesignSystem
import UnlockFeatureInterface

// MARK: - Component

@MainActor
final class UnlockComponent: Component<UnlockDependency> {
	var lockManager: VaultLockManagerProtocol {
		dependency.lockManager
	}
}

// MARK: - Presentable Protocols

@MainActor
public protocol UnlockPresentable: AnyObject {
	var listener: UnlockPresentableListener? { get set }
	func setErrorMessage(_ message: String?)
	func setLoading(_ isLoading: Bool)
	func clearInputPassword()
}

@MainActor
public protocol UnlockPresentableListener: AnyObject {
	func unlock(withMasterPassword password: String)
	func unlockWithBiometrics()
}

// MARK: - Interactor

@MainActor
protocol UnlockInteractable: Interactable {
	var router: UnlockRouting? { get set }
	var listener: UnlockListener? { get set }
	func unlockSuccess()
}

@MainActor
final class UnlockInteractor: Interactor, UnlockInteractable, UnlockPresentableListener {
	weak var router: UnlockRouting?
	weak var listener: UnlockListener?
	weak var presenter: UnlockPresentable?
	let lockManager: VaultLockManagerProtocol

	var errorMessage: String?
	var isLoading: Bool = false
	var password: String = ""
	var inputPassword: String {
		get { password }
		set { password = newValue }
	}

	init(
		lockManager: VaultLockManagerProtocol,
		presenter: UnlockPresentable? = nil
	) {
		self.lockManager = lockManager
		self.presenter = presenter
		super.init()
	}

	func unlockSuccess() {
		listener?.didUnlockVault()
	}

	func unlock(withMasterPassword password: String) {
		// RED 단계 스텁: 비즈니스 로직 미구현 상태로 단위 테스트 실패 유도
	}

	func unlockWithBiometrics() {
		// RED 단계 스텁: 비즈니스 로직 미구현 상태로 단위 테스트 실패 유도
	}
}

// MARK: - View & ViewControllable

public struct UnlockView: View {
	@State private var password: String = ""
	public var errorMessage: String?
	public var isLoading: Bool
	public var isBiometricsEnabled: Bool
	public var onUnlockPassword: ((String) -> Void)?
	public var onUnlockBiometrics: (() -> Void)?

	public init(
		errorMessage: String? = nil,
		isLoading: Bool = false,
		isBiometricsEnabled: Bool = false,
		onUnlockPassword: ((String) -> Void)? = nil,
		onUnlockBiometrics: (() -> Void)? = nil
	) {
		self.errorMessage = errorMessage
		self.isLoading = isLoading
		self.isBiometricsEnabled = isBiometricsEnabled
		self.onUnlockPassword = onUnlockPassword
		self.onUnlockBiometrics = onUnlockBiometrics
	}

	public var body: some View {
		VStack(spacing: 24) {
			Text(VaultLockUIStrings.lockedTitle)
				.font(.title2.bold())
				.foregroundColor(DesignToken.View.foreground)

			KMCard(padding: .lg) {
				VStack(alignment: .leading, spacing: 16) {
					KMPasswordField(
						text: $password,
						placeholder: "마스터 비밀번호 입력",
						showCopyAction: false
					)

					if let errorMessage = errorMessage, !errorMessage.isEmpty {
						Text(errorMessage)
							.font(.footnote)
							.foregroundColor(DesignToken.View.destructive)
					}

					KMButton(
						VaultLockUIStrings.unlockButtonTitle,
						variant: .primary,
						isLoading: isLoading,
						isDisabled: isLoading || password.isEmpty
					) {
						onUnlockPassword?(password)
					}

					if isBiometricsEnabled {
						KMButton(
							VaultLockUIStrings.biometricUnlockButtonTitle,
							variant: .outline,
							isDisabled: isLoading
						) {
							onUnlockBiometrics?()
						}
					}
				}
			}
			.frame(maxWidth: 400)
		}
		.padding(32)
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.background(DesignToken.View.background)
	}
}

@MainActor
protocol UnlockViewControllable: ViewControllable {}

#if os(macOS)
import AppKit

@MainActor
final class UnlockViewController: NSHostingController<UnlockView>, UnlockViewControllable, UnlockPresentable {
	weak var listener: UnlockPresentableListener?

	init(
		isBiometricsEnabled: Bool = false,
		listener: UnlockPresentableListener? = nil
	) {
		self.listener = listener
		super.init(rootView: UnlockView(
			errorMessage: nil,
			isLoading: false,
			isBiometricsEnabled: isBiometricsEnabled,
			onUnlockPassword: { [weak listener] password in
				listener?.unlock(withMasterPassword: password)
			},
			onUnlockBiometrics: { [weak listener] in
				listener?.unlockWithBiometrics()
			}
		))
	}

	@MainActor required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func setErrorMessage(_ message: String?) {}
	func setLoading(_ isLoading: Bool) {}
	func clearInputPassword() {}
}
#elseif os(iOS)
import UIKit

@MainActor
final class UnlockViewController: UIHostingController<UnlockView>, UnlockViewControllable, UnlockPresentable {
	weak var listener: UnlockPresentableListener?

	init(
		isBiometricsEnabled: Bool = false,
		listener: UnlockPresentableListener? = nil
	) {
		self.listener = listener
		super.init(rootView: UnlockView(
			errorMessage: nil,
			isLoading: false,
			isBiometricsEnabled: isBiometricsEnabled,
			onUnlockPassword: { [weak listener] password in
				listener?.unlock(withMasterPassword: password)
			},
			onUnlockBiometrics: { [weak listener] in
				listener?.unlockWithBiometrics()
			}
		))
	}

	@MainActor required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func setErrorMessage(_ message: String?) {}
	func setLoading(_ isLoading: Bool) {}
	func clearInputPassword() {}
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
		let component = UnlockComponent(dependency: dependency)
		let interactor = UnlockInteractor(lockManager: component.lockManager)
		interactor.listener = listener

		let viewController = UnlockViewController(
			isBiometricsEnabled: component.lockManager.isBiometricsEnabled,
			listener: interactor
		)
		interactor.presenter = viewController

		let router = UnlockRouter(interactor: interactor, viewController: viewController)
		interactor.router = router
		return router
	}
}
