import ProjectDescription
import ProjectDescriptionHelpers

let project = Project.makeFeature(
	name: "MainFeature",
	interfaceDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "VaultListFeatureInterface", path: "../VaultList")
	],
	implementationDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "KeyManageDesignSystem", path: "../../Core/DesignSystem"),
		.project(target: "VaultListFeatureInterface", path: "../VaultList")
	]
)
