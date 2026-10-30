import Foundation

public enum KeyDerivationError: Error, Equatable, Sendable {
	case invalidSaltLength(Int)
	case invalidIterationCount(Int)
	case derivationFailed(Int32)
}

public struct PBKDF2KeyDeriver: Sendable {
	public static let minIterations: Int = 600_000
	public static let maxIterations: Int = 10_000_000
	public static let defaultIterations: Int = 600_000
	public static let expectedSaltLength: Int = 16
	public static let derivedKeyLength: Int = 32

	public static func deriveMasterKey(
		password: Data,
		salt: Data,
		iterations: Int = defaultIterations
	) throws -> Data {
		throw KeyDerivationError.derivationFailed(-1)
	}
}
