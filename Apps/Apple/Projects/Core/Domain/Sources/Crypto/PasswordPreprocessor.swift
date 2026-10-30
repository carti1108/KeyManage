import Foundation

public enum PasswordPreprocessingError: Error, Equatable, Sendable {
	case unpairedSurrogate
	case invalidEncoding
}

public struct PasswordPreprocessor: Sendable {
	public static let trimmedScalars: Set<UnicodeScalar> = [
		UnicodeScalar(0x0020)!, // ASCII Space
		UnicodeScalar(0x0009)!, // Tab
		UnicodeScalar(0x000A)!, // Line Feed
		UnicodeScalar(0x000D)!, // Carriage Return
		UnicodeScalar(0x00A0)!, // Non-Breaking Space
		UnicodeScalar(0x3000)!  // Ideographic Space (전각 공백)
	]

	public static func preprocess(_ password: String) throws -> Data {
		// 1. Validate no unpaired surrogates in UTF-16 view
		try validateNoUnpairedSurrogates(password.utf16)

		// 2. NFC normalization
		let normalized = password.precomposedStringWithCanonicalMapping

		// 3. Trim leading and trailing designated whitespace
		let trimmed = trimDesignatedWhitespace(normalized)

		// 4. Convert to UTF-8 bytes
		guard let data = trimmed.data(using: .utf8) else {
			throw PasswordPreprocessingError.invalidEncoding
		}
		return data
	}

	public static func preprocess(utf16CodeUnits: [UInt16]) throws -> Data {
		// 1. Validate no unpaired surrogates
		try validateNoUnpairedSurrogates(utf16CodeUnits)

		// 2. Convert UTF-16 code units to String
		let string = String(decoding: utf16CodeUnits, as: UTF16.self)

		// 3. Delegate to preprocess(String)
		return try preprocess(string)
	}

	private static func validateNoUnpairedSurrogates<T: Sequence>(_ utf16: T) throws where T.Element == UInt16 {
		var iterator = utf16.makeIterator()
		while let unit = iterator.next() {
			if (0xD800...0xDBFF).contains(unit) {
				guard let next = iterator.next(), (0xDC00...0xDFFF).contains(next) else {
					throw PasswordPreprocessingError.unpairedSurrogate
				}
			} else if (0xDC00...0xDFFF).contains(unit) {
				throw PasswordPreprocessingError.unpairedSurrogate
			}
		}
	}

	private static func trimDesignatedWhitespace(_ string: String) -> String {
		let scalars = string.unicodeScalars
		var start = scalars.startIndex
		var end = scalars.endIndex

		while start < end && trimmedScalars.contains(scalars[start]) {
			start = scalars.index(after: start)
		}
		while end > start {
			let prev = scalars.index(before: end)
			if trimmedScalars.contains(scalars[prev]) {
				end = prev
			} else {
				break
			}
		}
		return String(scalars[start..<end])
	}
}
