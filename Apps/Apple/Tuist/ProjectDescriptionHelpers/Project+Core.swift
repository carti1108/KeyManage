import ProjectDescription

public extension Project {

	static func makeCore(
		name: String,
		product: Product = .staticFramework,
		dependencies: [TargetDependency] = [],
		destinations: Destinations = [.iPhone, .iPad, .mac],
		hasTesting: Bool = false
	) -> Project {
		let mainTarget = Target.target(
			name: name,
			destinations: destinations,
			product: product,
			bundleId: "com.keymanage.core.\(name.lowercased())",
			deploymentTargets: .multiplatform(iOS: "15.0", macOS: "14.0"),
			sources: ["Sources/**"],
			dependencies: dependencies
		)

		var targets = [mainTarget]

		if hasTesting {
			let testingTarget = Target.target(
				name: "\(name)Testing",
				destinations: destinations,
				product: product,
				bundleId: "com.keymanage.core.\(name.lowercased()).testing",
				deploymentTargets: .multiplatform(iOS: "15.0", macOS: "14.0"),
				sources: ["Testing/**"],
				dependencies: [.target(name: name)]
			)
			targets.append(testingTarget)
		}

		let testsDependencies: [TargetDependency] = hasTesting
			? [.target(name: name), .target(name: "\(name)Testing")]
			: [.target(name: name)]

		let testsTarget = Target.target(
			name: "\(name)Tests",
			destinations: destinations,
			product: .unitTests,
			bundleId: "com.keymanage.core.\(name.lowercased()).tests",
			deploymentTargets: .multiplatform(iOS: "15.0", macOS: "14.0"),
			sources: ["Tests/**"],
			dependencies: testsDependencies
		)
		targets.append(testsTarget)

		return Project(
			name: name,
			settings: .swift6Settings,
			targets: targets
		)
	}
}
