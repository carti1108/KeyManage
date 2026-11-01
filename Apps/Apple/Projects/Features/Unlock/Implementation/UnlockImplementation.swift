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
		guard !password.isEmpty else { return }
		isLoading = true
		presenter?.setLoading(true)
		defer {
			isLoading = false
			presenter?.setLoading(false)
		}

		do {
			try lockManager.unlockWithMasterPassword(password: password, now: Date())
			self.password = ""
			self.errorMessage = nil
			presenter?.clearInputPassword()
			presenter?.setErrorMessage(nil)
			unlockSuccess()
		} catch let error as LocalizedError {
			let msg = error.errorDescription ?? VaultLockUIStrings.invalidMasterPasswordMessage
			self.errorMessage = msg
			presenter?.setErrorMessage(msg)
		} catch {
			self.errorMessage = VaultLockUIStrings.invalidMasterPasswordMessage
			presenter?.setErrorMessage(VaultLockUIStrings.invalidMasterPasswordMessage)
		}
	}

	func unlockWithBiometrics() {
		guard lockManager.isBiometricsEnabled else { return }
		isLoading = true
		presenter?.setLoading(true)
		defer {
			isLoading = false
			presenter?.setLoading(false)
		}

		do {
			let stateHash = lockManager.currentBiometryStateHash ?? Data()
			let recoveredKey = lockManager.encryptionKey ?? Data(repeating: 0x01, count: 32)
			try lockManager.unlockWithBiometrics(
				evaluatedStateHash: stateHash,
				recoveredEncryptionKey: recoveredKey,
				now: Date()
			)
			self.errorMessage = nil
			presenter?.setErrorMessage(nil)
			unlockSuccess()
		} catch let error as LocalizedError {
			let msg = error.errorDescription ?? "생체 인증에 실패했습니다."
			self.errorMessage = msg
			presenter?.setErrorMessage(msg)
		} catch {
			self.errorMessage = "생체 인증에 실패했습니다."
			presenter?.setErrorMessage("생체 인증에 실패했습니다.")
		}
	}
}

// MARK: - View Model

@MainActor
public final class UnlockViewModel: ObservableObject {
	@Published public var password: String = ""
	@Published public var errorMessage: String?
	@Published public var isLoading: Bool = false
	public let isBiometricsEnabled: Bool
	public var onUnlockPassword: ((String) -> Void)?
	public var onUnlockBiometrics: (() -> Void)?

	public init(
		isBiometricsEnabled: Bool = false,
		errorMessage: String? = nil,
		isLoading: Bool = false,
		onUnlockPassword: ((String) -> Void)? = nil,
		onUnlockBiometrics: (() -> Void)? = nil
	) {
		self.isBiometricsEnabled = isBiometricsEnabled
		self.errorMessage = errorMessage
		self.isLoading = isLoading
		self.onUnlockPassword = onUnlockPassword
		self.onUnlockBiometrics = onUnlockBiometrics
	}

	public func unlockWithPassword() {
		onUnlockPassword?(password)
	}

	public func unlockWithBiometrics() {
		onUnlockBiometrics?()
	}

	public func clearPassword() {
		password = ""
	}
}

// MARK: - View & ViewControllable

public struct UnlockView: View {
	@ObservedObject public var viewModel: UnlockViewModel

	public init(viewModel: UnlockViewModel) {
		self.viewModel = viewModel
	}

	public init(
		errorMessage: String? = nil,
		isLoading: Bool = false,
		isBiometricsEnabled: Bool = false,
		onUnlockPassword: ((String) -> Void)? = nil,
		onUnlockBiometrics: (() -> Void)? = nil
	) {
		self.init(viewModel: UnlockViewModel(
			isBiometricsEnabled: isBiometricsEnabled,
			errorMessage: errorMessage,
			isLoading: isLoading,
			onUnlockPassword: onUnlockPassword,
			onUnlockBiometrics: onUnlockBiometrics
		))
	}

	public var body: some View {
		VStack(spacing: 24) {
			Text(VaultLockUIStrings.lockedTitle)
				.font(.title2.bold())
				.foregroundColor(DesignToken.View.foreground)

			KMCard(padding: .lg) {
				VStack(alignment: .leading, spacing: 16) {
					KMPasswordField(
						text: $viewModel.password,
						placeholder: "마스터 비밀번호 입력",
						showCopyAction: false
					)

					if let errorMessage = viewModel.errorMessage, !errorMessage.isEmpty {
						Text(errorMessage)
							.font(.footnote)
							.foregroundColor(DesignToken.View.destructive)
					}

					KMButton(
						VaultLockUIStrings.unlockButtonTitle,
						variant: .primary,
						isLoading: viewModel.isLoading,
						isDisabled: viewModel.isLoading || viewModel.password.isEmpty
					) {
						viewModel.unlockWithPassword()
					}

					if viewModel.isBiometricsEnabled {
						KMButton(
							VaultLockUIStrings.biometricUnlockButtonTitle,
							variant: .outline,
							icon: DesignToken.Icon.vaultLock,
							isDisabled: viewModel.isLoading
						) {
							viewModel.unlockWithBiometrics()
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
	let viewModel: UnlockViewModel

	init(
		isBiometricsEnabled: Bool = false,
		listener: UnlockPresentableListener? = nil
	) {
		self.listener = listener
		let model = UnlockViewModel(isBiometricsEnabled: isBiometricsEnabled)
		self.viewModel = model
		super.init(rootView: UnlockView(viewModel: model))

		model.onUnlockPassword = { [weak self] password in
			self?.listener?.unlock(withMasterPassword: password)
		}
		model.onUnlockBiometrics = { [weak self] in
			self?.listener?.unlockWithBiometrics()
		}
	}

	@MainActor required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func setErrorMessage(_ message: String?) {
		viewModel.errorMessage = message
	}

	func setLoading(_ isLoading: Bool) {
		viewModel.isLoading = isLoading
	}

	func clearInputPassword() {
		viewModel.clearPassword()
	}
}
#elseif os(iOS)
import UIKit

@MainActor
final class UnlockViewController: UIHostingController<UnlockView>, UnlockViewControllable, UnlockPresentable {
	weak var listener: UnlockPresentableListener?
	let viewModel: UnlockViewModel

	init(
		isBiometricsEnabled: Bool = false,
		listener: UnlockPresentableListener? = nil
	) {
		self.listener = listener
		let model = UnlockViewModel(isBiometricsEnabled: isBiometricsEnabled)
		self.viewModel = model
		super.init(rootView: UnlockView(viewModel: model))

		model.onUnlockPassword = { [weak self] password in
			self?.listener?.unlock(withMasterPassword: password)
		}
		model.onUnlockBiometrics = { [weak self] in
			self?.listener?.unlockWithBiometrics()
		}
	}

	@MainActor required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	func setErrorMessage(_ message: String?) {
		viewModel.errorMessage = message
	}

	func setLoading(_ isLoading: Bool) {
		viewModel.isLoading = isLoading
	}

	func clearInputPassword() {
		viewModel.clearPassword()
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
