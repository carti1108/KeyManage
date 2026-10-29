// 데스크톱 틀 진입점 (ADR-0012). 플랫폼 기능은 필요한 작업에서 플러그인과 권한을 함께 추가한다
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
  tauri::Builder::default()
    .run(tauri::generate_context!())
    .expect("error while building tauri application");
}
