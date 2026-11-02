import Foundation

public struct VaultBackupMerger: Sendable {
	public init() {}

	public func restoreItems(
		current: VaultData,
		backup: VaultData,
		now: Date = Date()
	) throws -> (data: VaultData, restoredCount: Int) {
		// 0. 읽기 전용 볼트 복원 거절 (VLT-004 규칙 10)
		guard !current.isReadOnly else {
			throw VaultError.vaultIsReadOnly
		}

		// 1. 중복 ID 검증
		var seenIds = Set<String>()
		for item in backup.items {
			guard !seenIds.contains(item.id) else {
				throw VaultError.invalidVaultFormat("Duplicate item ID in backup: \(item.id)")
			}
			seenIds.insert(item.id)
		}

		var currentById: [String: VaultItem] = [:]
		for item in current.items {
			currentById[item.id] = item
		}

		var replaced: [String: VaultItem] = [:]
		var added: [VaultItem] = []

		for saved in backup.items {
			// 백업 시점에 이미 삭제된 툼스톤은 복원하지 않음 (VLT-004 규칙 7)
			guard saved.deletedAt == nil else { continue }

			if let existing = currentById[saved.id] {
				// 내용이 완전히 동일하고 활성 상태인 경우 건드리지 않음
				if existing.deletedAt == nil && sameContent(existing, saved) {
					continue
				}

				var restored = saved
				restored.deletedAt = nil
				restored.updatedAt = restoreTime(existing: existing, now: now)
				replaced[saved.id] = restored
			} else {
				// 현재 볼트에 없는 백업 항목 추가
				var restored = saved
				restored.deletedAt = nil
				restored.updatedAt = restoreTime(existing: nil, now: now)
				added.append(restored)
			}
		}

		let count = replaced.count + added.count
		if count == 0 {
			return (data: current, restoredCount: 0)
		}

		var mergedItems = current.items.map { replaced[$0.id] ?? $0 }
		mergedItems.append(contentsOf: added)

		let currentDate = (try? VaultDateHelper.validateAndParse(current.updatedAt)) ?? Date.distantPast
		var maxDate = currentDate
		for item in mergedItems {
			if let itemDate = try? VaultDateHelper.validateAndParse(item.updatedAt) {
				if itemDate > maxDate {
					maxDate = itemDate
				}
			}
		}

		var newVault = current
		newVault.items = mergedItems
		newVault.updatedAt = VaultDateHelper.format(maxDate)

		return (data: newVault, restoredCount: count)
	}

	private func restoreTime(existing: VaultItem?, now: Date) -> String {
		guard let existing = existing else {
			return VaultDateHelper.format(now)
		}
		let existingEffective = existing.effectiveDate
		let nextMinTime = existingEffective.addingTimeInterval(1.0)
		let effectiveTime = max(now, nextMinTime)
		return VaultDateHelper.format(effectiveTime)
	}

	private func sameContent(_ a: VaultItem, _ b: VaultItem) -> Bool {
		a.serviceName == b.serviceName &&
			a.username == b.username &&
			a.password == b.password &&
			a.url == b.url &&
			a.notes == b.notes &&
			a.unknownFields == b.unknownFields
	}
}
