import Foundation

enum VaultDateHelper {
	private static let isoRegex = try! NSRegularExpression(
		pattern: #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}(:?\d{2})?)$"#
	)

	static func validateAndParse(_ string: String) throws -> Date {
		let range = NSRange(location: 0, length: string.utf16.count)
		guard isoRegex.firstMatch(in: string, options: [], range: range) != nil else {
			throw VaultError.invalidVaultFormat("Date must include ISO8601 UTC format with timezone: \(string)")
		}

		let formatterWithSeconds = ISO8601DateFormatter()
		formatterWithSeconds.formatOptions = [.withInternetDateTime]
		if let d = formatterWithSeconds.date(from: string) {
			return d
		}

		let formatterWithFractional = ISO8601DateFormatter()
		formatterWithFractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
		if let d = formatterWithFractional.date(from: string) {
			return d
		}

		throw VaultError.invalidVaultFormat("Failed to parse date: \(string)")
	}

	static func format(_ date: Date) -> String {
		let formatter = ISO8601DateFormatter()
		formatter.formatOptions = [.withInternetDateTime]
		formatter.timeZone = TimeZone(secondsFromGMT: 0)
		return formatter.string(from: date)
	}
}

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

	public static func empty(now: Date = Date()) -> VaultData {
		VaultData(
			version: currentVersion,
			updatedAt: VaultDateHelper.format(now),
			items: [],
			isReadOnly: false,
			unknownFields: [:]
		)
	}

	public init(from decoder: Decoder) throws {
		let container = try decoder.container(keyedBy: DynamicCodingKeys.self)

		// 1. version
		guard let vKey = DynamicCodingKeys(stringValue: "version"),
		      let v = try? container.decode(Int.self, forKey: vKey) else {
			throw VaultError.invalidVaultFormat("Missing or invalid version in VaultData")
		}
		if v < 1 {
			throw VaultError.invalidVaultFormat("Version < 1 is invalid: \(v)")
		}
		self.version = v
		self.isReadOnly = (v > VaultData.currentVersion)

		// 2. updatedAt
		guard let uKey = DynamicCodingKeys(stringValue: "updatedAt"),
		      let u = try? container.decode(String.self, forKey: uKey) else {
			throw VaultError.invalidVaultFormat("Missing updatedAt in VaultData")
		}
		_ = try VaultDateHelper.validateAndParse(u)
		self.updatedAt = u

		// 3. items
		guard let iKey = DynamicCodingKeys(stringValue: "items"),
		      let its = try? container.decode([VaultItem].self, forKey: iKey) else {
			throw VaultError.invalidVaultFormat("Missing or invalid items in VaultData")
		}
		self.items = its

		// 4. unknownFields
		let knownKeys: Set<String> = ["version", "updatedAt", "items"]
		var unknowns: [String: AnyCodableValue] = [:]
		for key in container.allKeys where !knownKeys.contains(key.stringValue) {
			let val = try container.decode(AnyCodableValue.self, forKey: key)
			unknowns[key.stringValue] = val
		}
		self.unknownFields = unknowns
	}

	public func encode(to encoder: Encoder) throws {
		var container = encoder.container(keyedBy: DynamicCodingKeys.self)

		try container.encode(version, forKey: DynamicCodingKeys(stringValue: "version")!)
		try container.encode(updatedAt, forKey: DynamicCodingKeys(stringValue: "updatedAt")!)
		try container.encode(items, forKey: DynamicCodingKeys(stringValue: "items")!)

		for (key, val) in unknownFields {
			if let codingKey = DynamicCodingKeys(stringValue: key) {
				try container.encode(val, forKey: codingKey)
			}
		}
	}

	public static func decode(from data: Data) throws -> VaultData {
		let decoder = JSONDecoder()
		do {
			return try decoder.decode(VaultData.self, from: data)
		} catch let err as VaultError {
			throw err
		} catch {
			throw VaultError.invalidVaultFormat(error.localizedDescription)
		}
	}

	public func encode() throws -> Data {
		let encoder = JSONEncoder()
		return try encoder.encode(self)
	}

	public mutating func deleteItem(id: String, now: Date = Date()) throws {
		guard !isReadOnly else {
			throw VaultError.vaultIsReadOnly
		}
		guard let index = items.firstIndex(where: { $0.id == id }) else {
			throw VaultError.invalidVaultFormat("Item not found: \(id)")
		}

		let prevTime = items[index].effectiveDate
		let minNextTime = prevTime.addingTimeInterval(1)
		let effectiveTime = max(now, minNextTime)
		let timeString = VaultDateHelper.format(effectiveTime)

		items[index].updatedAt = timeString
		items[index].deletedAt = timeString
		items[index].serviceName = ""
		items[index].username = ""
		items[index].password = ""
		items[index].url = nil
		items[index].notes = nil
		items[index].unknownFields = [:]

		let vaultTime = (try? VaultDateHelper.validateAndParse(updatedAt)) ?? Date.distantPast
		let newVaultTime = max(vaultTime, effectiveTime)
		self.updatedAt = VaultDateHelper.format(newVaultTime)
	}

	public mutating func updateItem(_ item: VaultItem, now: Date = Date()) throws {
		guard !isReadOnly else {
			throw VaultError.vaultIsReadOnly
		}
		guard let index = items.firstIndex(where: { $0.id == item.id }) else {
			throw VaultError.invalidVaultFormat("Item not found: \(item.id)")
		}

		let prevTime = items[index].effectiveDate
		let minNextTime = prevTime.addingTimeInterval(1)
		let effectiveTime = max(now, minNextTime)
		let timeString = VaultDateHelper.format(effectiveTime)

		var updated = item
		updated.updatedAt = timeString
		items[index] = updated

		let vaultTime = (try? VaultDateHelper.validateAndParse(updatedAt)) ?? Date.distantPast
		let newVaultTime = max(vaultTime, effectiveTime)
		self.updatedAt = VaultDateHelper.format(newVaultTime)
	}

	public mutating func addItem(_ item: VaultItem, now: Date = Date()) throws {
		guard !isReadOnly else {
			throw VaultError.vaultIsReadOnly
		}
		let timeString = VaultDateHelper.format(now)
		var newItem = item
		if newItem.createdAt.isEmpty {
			newItem.createdAt = timeString
		}
		newItem.updatedAt = timeString
		items.append(newItem)

		let vaultTime = (try? VaultDateHelper.validateAndParse(updatedAt)) ?? Date.distantPast
		let newVaultTime = max(vaultTime, now)
		self.updatedAt = VaultDateHelper.format(newVaultTime)
	}
}
