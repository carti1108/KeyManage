import Foundation

public struct VaultData: Codable, Sendable, Equatable {
	public static let currentVersion = 1

	public var version: Int
	public var updatedAt: String
	public var items: [VaultItem]
	public var isReadOnly: Bool
	public var unknownFields: [String: AnyCodableValue]

	public init(
		version: Int = 1,
		updatedAt: String,
		items: [VaultItem] = [],
		isReadOnly: Bool = false,
		unknownFields: [String: AnyCodableValue] = [:]
	) {
		self.version = version
		self.updatedAt = updatedAt
		self.items = items
		self.isReadOnly = isReadOnly
		self.unknownFields = unknownFields
	}

	public init(from decoder: Decoder) throws {
		throw VaultError.notImplemented
	}

	public func encode(to encoder: Encoder) throws {
		throw VaultError.notImplemented
	}

	public static func decode(from data: Data) throws -> VaultData {
		throw VaultError.notImplemented
	}

	public func encode() throws -> Data {
		throw VaultError.notImplemented
	}

	public mutating func deleteItem(id: String, now: Date = Date()) throws {
		throw VaultError.notImplemented
	}

	public mutating func updateItem(_ item: VaultItem, now: Date = Date()) throws {
		throw VaultError.notImplemented
	}

	public mutating func addItem(_ item: VaultItem, now: Date = Date()) throws {
		throw VaultError.notImplemented
	}
}
