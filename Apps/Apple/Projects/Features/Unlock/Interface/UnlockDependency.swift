import Foundation
import MicroRIBs
import KeyManageDomain

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
