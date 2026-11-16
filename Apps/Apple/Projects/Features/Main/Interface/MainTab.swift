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

	/// 화면 위 큰 제목. 탭 이름보다 길 수 있다.
	public var navigationTitle: String {
		switch self {
		case .generator: return "비밀번호 생성기"
		case .vault, .settings: return title
		}
	}
}
