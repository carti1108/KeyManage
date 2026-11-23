import ProjectDescription
import ProjectDescriptionHelpers

// 공유 Kotlin PasswordGenerator RIB의 SwiftUI 뷰, Example 앱, 뷰 테스트
let project = Project(
	name: "PasswordGeneratorFeature",
	settings: .swift6Settings,
	targets: SharedFeature.targets(name: "PasswordGenerator")
)
