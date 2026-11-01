import ProjectDescription
import ProjectDescriptionHelpers

let project = Project.makeFeature(
	name: "UnlockFeature",
	interfaceDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.external(name: "MicroRIBs")
	],
	implementationDependencies: [
		.project(target: "KeyManageDomain", path: "../../Core/Domain"),
		.project(target: "KeyManageDesignSystem", path: "../../Core/DesignSystem"),
		.external(name: "MicroRIBs")
	]
)
