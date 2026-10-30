import ProjectDescription
import ProjectDescriptionHelpers

let project = Project.makeCore(
	name: "KeyManageData",
	dependencies: [
		.project(target: "KeyManageDomain", path: "../Domain")
	]
)
