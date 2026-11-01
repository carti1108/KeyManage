import Foundation

public enum MainTab: String, CaseIterable, Identifiable, Sendable {
	case vault
	case generator
	case settings

	public var id: String { rawValue }

	public var title: String {
		switch self {
		case .vault: return "볼트"
		case .generator: return "생성기"
		case .settings: return "설정"
		}
	}
}
