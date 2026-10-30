import ProjectDescription
import ProjectDescriptionHelpers

let project = Project.makeCore(
	name: "KeyManageUIAdapter",
	dependencies: [
		.external(name: "MicroRIBs")
	]
)
