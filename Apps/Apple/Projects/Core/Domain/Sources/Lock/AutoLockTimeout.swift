import Foundation

public enum AutoLockTimeout: String, CaseIterable, Sendable, Codable {
	case immediate
	case oneMinute
	case fiveMinutes
	case fifteenMinutes
	case oneHour

	public static let `default`: AutoLockTimeout = .fiveMinutes

	public var timeInterval: TimeInterval {
		switch self {
		case .immediate:
			return 0
		case .oneMinute:
			return 60
		case .fiveMinutes:
			return 300
		case .fifteenMinutes:
			return 900
		case .oneHour:
			return 3600
		}
	}
}
