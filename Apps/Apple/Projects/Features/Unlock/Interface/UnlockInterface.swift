import Foundation
import MicroRIBs
import KeyManageDomain

public typealias VaultLockError = LockError

/// The biometric key store protocol for recovering encryption keys protected by Secure Enclave / Keychain.
@MainActor
public protocol BiometricKeyStoreProtocol: AnyObject, Sendable {
	func evaluateAndRecoverKey() throws -> (biometryHash: Data, encryptionKey: Data)
}

/// The dependency protocol for UnlockFeature.
@MainActor
public protocol UnlockDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
	var biometricKeyStore: BiometricKeyStoreProtocol? { get }
}

public extension UnlockDependency {
	var biometricKeyStore: BiometricKeyStoreProtocol? {
		nil
	}
}

/// The listener protocol for UnlockFeature.
@MainActor
public protocol UnlockListener: AnyObject {
	func didUnlockVault()
}

/// The routing interface for UnlockFeature.
@MainActor
public protocol UnlockRouting: ViewableRouting {}

/// The buildable interface for UnlockFeature.
@MainActor
public protocol UnlockBuildable: Buildable {
	func build(withListener listener: UnlockListener) -> UnlockRouting
}
