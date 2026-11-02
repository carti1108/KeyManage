import ProjectDescription
import ProjectDescriptionHelpers

let project = Project.makeFeature(
	name: "MainFeature",
	interfaceDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "VaultListFeatureInterface", path: "../VaultList"),
		.project(target: "SettingsFeatureInterface", path: "../Settings"),
		.project(target: "PasswordGeneratorFeatureInterface", path: "../PasswordGenerator")
	],
	implementationDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "KeyManageDesignSystem", path: "../../Core/DesignSystem"),
		.project(target: "VaultListFeatureInterface", path: "../VaultList"),
		.project(target: "SettingsFeatureInterface", path: "../Settings"),
		.project(target: "PasswordGeneratorFeatureInterface", path: "../PasswordGenerator")
	]
)
