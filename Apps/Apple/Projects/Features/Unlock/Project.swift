import ProjectDescription
import ProjectDescriptionHelpers

// 옛 Swift RIB 타깃은 앱을 공유 Root로 바꿀 때까지 둔다. 공유 Kotlin RIB의 뷰·Example은 SharedFeature가 만든다 (modularization-plan F단계)
let project = Project.makeFeature(
	name: "UnlockFeature",
	interfaceDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain")
	],
	implementationDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "KeyManageDesignSystem", path: "../../Core/DesignSystem")
	],
	extraTargets: SharedFeature.targets(name: "Unlock")
)
