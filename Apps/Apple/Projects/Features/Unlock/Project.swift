import ProjectDescription
import ProjectDescriptionHelpers

let project = Project.makeFeature(
	name: "UnlockFeature",
	interfaceDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain")
	],
	implementationDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "KeyManageDesignSystem", path: "../../Core/DesignSystem")
	]
)
