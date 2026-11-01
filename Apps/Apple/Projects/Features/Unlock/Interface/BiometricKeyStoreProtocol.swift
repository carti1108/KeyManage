import Foundation

/// The biometric key store protocol for recovering encryption keys protected by Secure Enclave / Keychain.
@MainActor
public protocol BiometricKeyStoreProtocol: AnyObject, Sendable {
	func evaluateAndRecoverKey() throws -> (biometryHash: Data, encryptionKey: Data)
}
