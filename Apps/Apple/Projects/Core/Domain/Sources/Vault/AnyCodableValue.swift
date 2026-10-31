import Foundation

public enum AnyCodableValue: Codable, Sendable, Equatable, Hashable {
	case string(String)
	case int(Int)
	case double(Double)
	case bool(Bool)
	case array([AnyCodableValue])
	case dictionary([String: AnyCodableValue])
	case null

	public init(from decoder: Decoder) throws {
		throw VaultError.notImplemented
	}

	public func encode(to encoder: Encoder) throws {
		throw VaultError.notImplemented
	}
}

extension AnyCodableValue: ExpressibleByStringLiteral {
	public init(stringLiteral value: String) {
		self = .string(value)
	}
}

extension AnyCodableValue: ExpressibleByIntegerLiteral {
	public init(integerLiteral value: Int) {
		self = .int(value)
	}
}

extension AnyCodableValue: ExpressibleByFloatLiteral {
	public init(floatLiteral value: Double) {
		self = .double(value)
	}
}

extension AnyCodableValue: ExpressibleByBooleanLiteral {
	public init(booleanLiteral value: Bool) {
		self = .bool(value)
	}
}

extension AnyCodableValue: ExpressibleByDictionaryLiteral {
	public init(dictionaryLiteral elements: (String, AnyCodableValue)...) {
		self = .dictionary(Dictionary(uniqueKeysWithValues: elements))
	}
}

extension AnyCodableValue: ExpressibleByArrayLiteral {
	public init(arrayLiteral elements: AnyCodableValue...) {
		self = .array(elements)
	}
}
