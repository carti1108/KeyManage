import Foundation

public struct VaultItem: Codable, Sendable, Equatable {
	public var id: String
	public var serviceName: String
	public var username: String
	public var password: String
	public var url: String?
	public var notes: String?
	public var createdAt: String
	public var updatedAt: String
	public var deletedAt: String?
	public var unknownFields: [String: AnyCodableValue]

	public var isDeleted: Bool {
		deletedAt != nil
	}

	public init(
		id: String,
		serviceName: String,
		username: String,
		password: String,
		url: String? = nil,
		notes: String? = nil,
		createdAt: String,
		updatedAt: String,
		deletedAt: String? = nil,
		unknownFields: [String: AnyCodableValue] = [:]
	) {
		self.id = id
		self.serviceName = serviceName
		self.username = username
		self.password = password
		self.url = url
		self.notes = notes
		self.createdAt = createdAt
		self.updatedAt = updatedAt
		self.deletedAt = deletedAt
		self.unknownFields = unknownFields
	}

	public init(from decoder: Decoder) throws {
		throw VaultError.notImplemented
	}

	public func encode(to encoder: Encoder) throws {
		throw VaultError.notImplemented
	}
}
