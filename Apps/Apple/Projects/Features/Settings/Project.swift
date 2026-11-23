import ProjectDescription
import ProjectDescriptionHelpers

// 공유 Kotlin Settings RIB의 SwiftUI 뷰, Example 앱, 뷰 테스트
let project = Project(
	name: "SettingsFeature",
	settings: .swift6Settings,
	targets: SharedFeature.targets(name: "Settings")
)
