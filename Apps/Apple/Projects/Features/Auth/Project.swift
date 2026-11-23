import ProjectDescription
import ProjectDescriptionHelpers

// 공유 Kotlin Auth RIB의 SwiftUI 뷰, Example 앱, 뷰 테스트
let project = Project(
	name: "AuthFeature",
	settings: .swift6Settings,
	targets: SharedFeature.targets(name: "Auth")
)
