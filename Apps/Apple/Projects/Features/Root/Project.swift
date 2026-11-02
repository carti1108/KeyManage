import ProjectDescription
import ProjectDescriptionHelpers

let project = Project.makeFeature(
	name: "RootFeature",
	interfaceDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "UnlockFeatureInterface", path: "../Unlock"),
		.project(target: "MainFeatureInterface", path: "../Main"),
		.project(target: "AuthFeatureInterface", path: "../Auth")
	],
	implementationDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "KeyManageDesignSystem", path: "../../Core/DesignSystem"),
		.project(target: "UnlockFeatureInterface", path: "../Unlock"),
		.project(target: "MainFeatureInterface", path: "../Main"),
		.project(target: "AuthFeatureInterface", path: "../Auth")
	]
)
