import Foundation
import SwiftUI
import MicroRIBs
import UnlockFeatureInterface

@MainActor
protocol UnlockViewControllable: ViewControllable {}

@MainActor
protocol UnlockPresentableListener: AnyObject {
	func unlock(withMasterPassword password: String)
	func unlockWithBiometrics()
}

#if os(macOS)
import AppKit
typealias PlatformHostingController<Content: View> = NSHostingController<Content>
#elseif os(iOS)
import UIKit
typealias PlatformHostingController<Content: View> = UIHostingController<Content>
#endif

@MainActor
final class UnlockViewController: PlatformHostingController<UnlockView>, UnlockViewControllable, UnlockPresentable {
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
