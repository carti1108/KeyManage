import MicroRIBs

@MainActor
public protocol SettingsBuildable: Buildable {
	func build(withListener listener: SettingsListener) -> SettingsRouting
}
