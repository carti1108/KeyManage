import Foundation

public enum PasswordPreprocessingError: Error, Equatable, Sendable {
	case unpairedSurrogate
}

public struct PasswordPreprocessor: Sendable {
	public static let trimmedCharacters: Set<Character> = [
		" ",
		"\u{0009}",
		"\u{000A}",
		"\u{000D}",
		"\u{00A0}",
		"\u{3000}"
	]

	public static func preprocess(_ password: String) throws -> Data {
		throw PasswordPreprocessingError.unpairedSurrogate
	}

	public static func preprocess(utf16CodeUnits: [UInt16]) throws -> Data {
		throw PasswordPreprocessingError.unpairedSurrogate
	}
}
