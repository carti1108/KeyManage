import ProjectDescription
import ProjectDescriptionHelpers

// 피처 Example 앱의 공통 틀. Kotlin 프레임워크에 의존하지 않는다 (modularization-plan F단계 설계)
let project = Project(
	name: "KeyManageFeatureExample",
	settings: .swift6Settings,
	targets: [
		.target(
			name: "KeyManageFeatureExample",
			destinations: [.iPhone, .iPad],
			product: .staticFramework,
			bundleId: "com.keymanage.core.featureexample",
			deploymentTargets: .iOS(SharedFeature.iOSVersion),
			sources: ["Sources/**"],
			dependencies: [.external(name: "RIBsKMPSwiftUI")]
		)
	]
)
