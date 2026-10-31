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
		if let container = try? decoder.container(keyedBy: DynamicCodingKeys.self) {
			var dict: [String: AnyCodableValue] = [:]
			for key in container.allKeys {
				dict[key.stringValue] = try container.decode(AnyCodableValue.self, forKey: key)
			}
			self = .dictionary(dict)
			return
		}
		if var container = try? decoder.unkeyedContainer() {
			var array: [AnyCodableValue] = []
			while !container.isAtEnd {
				array.append(try container.decode(AnyCodableValue.self))
			}
			self = .array(array)
			return
		}
		let single = try decoder.singleValueContainer()
		if single.decodeNil() {
			self = .null
		} else if let boolVal = try? single.decode(Bool.self) {
			self = .bool(boolVal)
		} else if let intVal = try? single.decode(Int.self) {
			self = .int(intVal)
		} else if let doubleVal = try? single.decode(Double.self) {
			self = .double(doubleVal)
		} else if let strVal = try? single.decode(String.self) {
			self = .string(strVal)
		} else {
			throw DecodingError.dataCorrupted(
				DecodingError.Context(codingPath: decoder.codingPath, debugDescription: "Unsupported AnyCodable value")
			)
		}
	}

	public func encode(to encoder: Encoder) throws {
		switch self {
		case .null:
			var container = encoder.singleValueContainer()
			try container.encodeNil()
		case .bool(let value):
			var container = encoder.singleValueContainer()
			try container.encode(value)
		case .int(let value):
			var container = encoder.singleValueContainer()
			try container.encode(value)
		case .double(let value):
			var container = encoder.singleValueContainer()
			try container.encode(value)
		case .string(let value):
			var container = encoder.singleValueContainer()
			try container.encode(value)
		case .array(let value):
			var container = encoder.unkeyedContainer()
			for item in value {
				try container.encode(item)
			}
		case .dictionary(let value):
			var container = encoder.container(keyedBy: DynamicCodingKeys.self)
			for (key, val) in value {
				if let codingKey = DynamicCodingKeys(stringValue: key) {
					try container.encode(val, forKey: codingKey)
				}
			}
		}
	}
}

public struct DynamicCodingKeys: CodingKey, Sendable {
	public var stringValue: String
	public var intValue: Int?

	public init?(stringValue: String) {
		self.stringValue = stringValue
		self.intValue = nil
	}

	public init?(intValue: Int) {
		self.stringValue = String(intValue)
		self.intValue = intValue
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
