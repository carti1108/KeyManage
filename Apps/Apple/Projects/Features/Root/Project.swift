import ProjectDescription
import ProjectDescriptionHelpers

let project = Project.makeFeature(
	name: "RootFeature",
	interfaceDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "UnlockFeatureInterface", path: "../Unlock")
	],
	implementationDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "KeyManageDesignSystem", path: "../../Core/DesignSystem"),
		.project(target: "UnlockFeatureInterface", path: "../Unlock")
	]
)
