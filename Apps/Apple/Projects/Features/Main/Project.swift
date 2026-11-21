import ProjectDescription
import ProjectDescriptionHelpers

// 공유 Kotlin Main RIB의 SwiftUI 뷰, Example 앱, 뷰 테스트 (modularization-plan F단계)
let project = Project(
	name: "MainFeature",
	settings: .swift6Settings,
	targets: SharedFeature.targets(name: "Main", childFeatures: ["VaultList", "PasswordGenerator", "Settings"])
)
