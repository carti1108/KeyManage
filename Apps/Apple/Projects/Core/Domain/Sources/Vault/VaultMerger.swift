import Foundation

public struct VaultMerger: Sendable {
	public init() {}

	public func merge(local: VaultData, server: VaultData) throws -> VaultData {
		var serverItemsById: [String: VaultItem] = [:]
		for item in server.items {
			serverItemsById[item.id] = item
		}

		var mergedItems: [VaultItem] = []
		var processedIds = Set<String>()

		for localItem in local.items {
			processedIds.insert(localItem.id)
			if let serverItem = serverItemsById[localItem.id] {
				let localDate = localItem.effectiveDate
				let serverDate = serverItem.effectiveDate
				if localDate > serverDate {
					mergedItems.append(localItem)
				} else if serverDate > localDate {
					mergedItems.append(serverItem)
				} else {
					// 시각이 동일한 경우 서버 우선 (VLT-001)
					mergedItems.append(serverItem)
				}
			} else {
				mergedItems.append(localItem)
			}
		}

		for serverItem in server.items where !processedIds.contains(serverItem.id) {
			mergedItems.append(serverItem)
		}

		let localVaultDate = (try? VaultDateHelper.validateAndParse(local.updatedAt)) ?? Date.distantPast
		let serverVaultDate = (try? VaultDateHelper.validateAndParse(server.updatedAt)) ?? Date.distantPast
		var latestDate = max(localVaultDate, serverVaultDate)
		for item in mergedItems {
			latestDate = max(latestDate, item.effectiveDate)
		}
		let mergedUpdatedAt = VaultDateHelper.format(latestDate)

		var mergedUnknownFields = local.unknownFields
		for (key, value) in server.unknownFields {
			mergedUnknownFields[key] = value
		}

		let mergedVersion = max(local.version, server.version)
		let mergedReadOnly = local.isReadOnly || server.isReadOnly

		return VaultData(
			version: mergedVersion,
			updatedAt: mergedUpdatedAt,
			items: mergedItems,
			isReadOnly: mergedReadOnly,
			unknownFields: mergedUnknownFields
		)
	}
}
