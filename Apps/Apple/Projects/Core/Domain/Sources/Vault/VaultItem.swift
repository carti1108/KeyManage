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

	public var effectiveDate: Date {
		let up = (try? VaultDateHelper.validateAndParse(updatedAt)) ?? Date.distantPast
		if let del = deletedAt, let delDate = try? VaultDateHelper.validateAndParse(del) {
			return max(delDate, up)
		}
		return up
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
		let container = try decoder.container(keyedBy: DynamicCodingKeys.self)

		// 1. id
		guard let idKey = DynamicCodingKeys(stringValue: "id"),
		      let id = try? container.decode(String.self, forKey: idKey) else {
			throw VaultError.invalidVaultFormat("Missing id in VaultItem")
		}
		self.id = id

		// 2. createdAt
		guard let createdKey = DynamicCodingKeys(stringValue: "createdAt"),
		      let createdAt = try? container.decode(String.self, forKey: createdKey) else {
			throw VaultError.invalidVaultFormat("Missing createdAt in VaultItem")
		}
		_ = try VaultDateHelper.validateAndParse(createdAt)
		self.createdAt = createdAt

		// 3. updatedAt
		guard let updatedKey = DynamicCodingKeys(stringValue: "updatedAt"),
		      let updatedAt = try? container.decode(String.self, forKey: updatedKey) else {
			throw VaultError.invalidVaultFormat("Missing updatedAt in VaultItem")
		}
		_ = try VaultDateHelper.validateAndParse(updatedAt)
		self.updatedAt = updatedAt

		// 4. deletedAt
		if let delKey = DynamicCodingKeys(stringValue: "deletedAt"),
		   container.contains(delKey),
		   !((try? container.decodeNil(forKey: delKey)) ?? false) {
			let delStr = try container.decode(String.self, forKey: delKey)
			_ = try VaultDateHelper.validateAndParse(delStr)
			self.deletedAt = delStr
		} else {
			self.deletedAt = nil
		}

		// 5. url
		if let urlKey = DynamicCodingKeys(stringValue: "url"),
		   container.contains(urlKey),
		   !((try? container.decodeNil(forKey: urlKey)) ?? false) {
			self.url = try? container.decode(String.self, forKey: urlKey)
		} else {
			self.url = nil
		}

		// 6. notes
		if let notesKey = DynamicCodingKeys(stringValue: "notes"),
		   container.contains(notesKey),
		   !((try? container.decodeNil(forKey: notesKey)) ?? false) {
			self.notes = try? container.decode(String.self, forKey: notesKey)
		} else {
			self.notes = nil
		}

		// 7. 삭제 여부에 따른 처리
		if self.deletedAt != nil {
			self.serviceName = ""
			self.username = ""
			self.password = ""
			self.url = nil
			self.notes = nil
			self.unknownFields = [:]
		} else {
			guard let sKey = DynamicCodingKeys(stringValue: "serviceName"),
			      let sName = try? container.decode(String.self, forKey: sKey) else {
				throw VaultError.invalidVaultFormat("Missing serviceName in VaultItem")
			}
			guard let uKey = DynamicCodingKeys(stringValue: "username"),
			      let uName = try? container.decode(String.self, forKey: uKey) else {
				throw VaultError.invalidVaultFormat("Missing username in VaultItem")
			}
			guard let pKey = DynamicCodingKeys(stringValue: "password"),
			      let pwd = try? container.decode(String.self, forKey: pKey) else {
				throw VaultError.invalidVaultFormat("Missing password in VaultItem")
			}
			self.serviceName = sName
			self.username = uName
			self.password = pwd

			let knownKeys: Set<String> = [
				"id", "serviceName", "username", "password", "url", "notes",
				"createdAt", "updatedAt", "deletedAt"
			]
			var unknowns: [String: AnyCodableValue] = [:]
			for key in container.allKeys where !knownKeys.contains(key.stringValue) {
				let val = try container.decode(AnyCodableValue.self, forKey: key)
				unknowns[key.stringValue] = val
			}
			self.unknownFields = unknowns
		}
	}

	public func encode(to encoder: Encoder) throws {
		var container = encoder.container(keyedBy: DynamicCodingKeys.self)

		try container.encode(id, forKey: DynamicCodingKeys(stringValue: "id")!)
		try container.encode(serviceName, forKey: DynamicCodingKeys(stringValue: "serviceName")!)
		try container.encode(username, forKey: DynamicCodingKeys(stringValue: "username")!)
		try container.encode(password, forKey: DynamicCodingKeys(stringValue: "password")!)
		if let url = url {
			try container.encode(url, forKey: DynamicCodingKeys(stringValue: "url")!)
		}
		if let notes = notes {
			try container.encode(notes, forKey: DynamicCodingKeys(stringValue: "notes")!)
		}
		try container.encode(createdAt, forKey: DynamicCodingKeys(stringValue: "createdAt")!)
		try container.encode(updatedAt, forKey: DynamicCodingKeys(stringValue: "updatedAt")!)
		if let deletedAt = deletedAt {
			try container.encode(deletedAt, forKey: DynamicCodingKeys(stringValue: "deletedAt")!)
		}

		for (key, val) in unknownFields {
			if let codingKey = DynamicCodingKeys(stringValue: key) {
				try container.encode(val, forKey: codingKey)
			}
		}
	}
}
