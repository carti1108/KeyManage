import Foundation
import CommonCrypto

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
		guard salt.count == expectedSaltLength else {
			throw KeyDerivationError.invalidSaltLength(salt.count)
		}
		guard iterations >= minIterations && iterations <= maxIterations else {
			throw KeyDerivationError.invalidIterationCount(iterations)
		}

		var derivedKey = Data(count: derivedKeyLength)

		let status = derivedKey.withUnsafeMutableBytes { derivedKeyBytes in
			salt.withUnsafeBytes { saltBytes in
				password.withUnsafeBytes { passwordBytes in
					CCKeyDerivationPBKDF(
						CCPBKDFAlgorithm(kCCPBKDF2),
						passwordBytes.baseAddress?.assumingMemoryBound(to: Int8.self),
						password.count,
						saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
						salt.count,
						CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
						UInt32(iterations),
						derivedKeyBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
						derivedKeyLength
					)
				}
			}
		}

		guard status == kCCSuccess else {
			throw KeyDerivationError.derivationFailed(status)
		}

		return derivedKey
	}
}
