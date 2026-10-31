import Foundation

public struct VaultMerger: Sendable {
	public init() {}

	public func merge(local: VaultData, server: VaultData) throws -> VaultData {
		throw VaultError.notImplemented
	}
}
