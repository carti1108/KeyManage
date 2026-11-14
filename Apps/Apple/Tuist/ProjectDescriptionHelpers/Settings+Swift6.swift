import ProjectDescription

public extension Settings {
	static let swift6Settings: Settings = .settings(
		base: [
			"SWIFT_VERSION": "6.0",
			"SWIFT_STRICT_CONCURRENCY": "complete"
		]
	)
}
