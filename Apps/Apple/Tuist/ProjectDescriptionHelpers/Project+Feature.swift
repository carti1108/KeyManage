import ProjectDescription

public extension Settings {
	static let swift6Settings: Settings = .settings(
		base: [
			"SWIFT_VERSION": "6.0",
			"SWIFT_STRICT_CONCURRENCY": "complete"
		]
	)
}

public extension Project {

	static func makeFeature(
		name: String,
		interfaceDependencies: [TargetDependency] = [],
		implementationDependencies: [TargetDependency] = [],
		destinations: Destinations = [.iPhone, .iPad, .mac],
		hasExample: Bool = false
	) -> Project {
		// DIP 방어: 타 피처의 Implementation 직접 참조 금지 (tuist-modularization 표준)
		for dependency in implementationDependencies {
			switch dependency {
			case let .target(targetName, _, _):
				if targetName.hasSuffix("Implementation") {
					fatalError("❌ [Architecture Violation] Feature Implementation cannot depend on another Implementation: \(targetName)")
				}
			case let .project(targetName, _, _, _):
				if targetName.hasSuffix("Implementation") {
					fatalError("❌ [Architecture Violation] Feature Implementation cannot depend on another Implementation: \(targetName)")
				}
			default:
				break
			}
		}

		let interfaceTarget = Target.target(
			name: "\(name)Interface",
			destinations: destinations,
			product: .staticFramework,
			bundleId: "com.keymanage.\(name.lowercased()).interface",
			deploymentTargets: .multiplatform(iOS: "15.0", macOS: "14.0"),
			sources: ["Interface/**"],
			dependencies: interfaceDependencies
		)

		let implementationTarget = Target.target(
			name: "\(name)Implementation",
			destinations: destinations,
			product: .staticFramework,
			bundleId: "com.keymanage.\(name.lowercased()).implementation",
			deploymentTargets: .multiplatform(iOS: "15.0", macOS: "14.0"),
			sources: ["Implementation/**"],
			dependencies: [.target(name: "\(name)Interface")] + implementationDependencies
		)

		let testingTarget = Target.target(
			name: "\(name)Testing",
			destinations: destinations,
			product: .staticFramework,
			bundleId: "com.keymanage.\(name.lowercased()).testing",
			deploymentTargets: .multiplatform(iOS: "15.0", macOS: "14.0"),
			sources: ["Testing/**"],
			dependencies: [.target(name: "\(name)Interface")]
		)

		let testsTarget = Target.target(
			name: "\(name)Tests",
			destinations: destinations,
			product: .unitTests,
			bundleId: "com.keymanage.\(name.lowercased()).tests",
			deploymentTargets: .multiplatform(iOS: "15.0", macOS: "14.0"),
			sources: ["Tests/**"],
			dependencies: [
				.target(name: "\(name)Implementation"),
				.target(name: "\(name)Testing")
			]
		)

		var targets = [
			interfaceTarget,
			implementationTarget,
			testingTarget,
			testsTarget
		]

		if hasExample {
			let exampleTarget = Target.target(
				name: "\(name)Example",
				destinations: destinations,
				product: .app,
				bundleId: "com.keymanage.\(name.lowercased()).example",
				deploymentTargets: .multiplatform(iOS: "15.0", macOS: "14.0"),
				infoPlist: .default,
				sources: ["Example/Sources/**"],
				resources: ["Example/Resources/**"],
				dependencies: [
					.target(name: "\(name)Implementation"),
					.target(name: "\(name)Testing")
				]
			)
			targets.append(exampleTarget)
		}

		return Project(
			name: name,
			settings: .swift6Settings,
			targets: targets
		)
	}
}
