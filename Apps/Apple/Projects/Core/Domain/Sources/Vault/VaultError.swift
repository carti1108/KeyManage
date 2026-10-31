import Foundation

public enum VaultError: Error, Equatable, Sendable {
	case invalidVaultFormat(String)
	case vaultIsReadOnly
}
