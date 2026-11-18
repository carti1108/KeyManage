# 2026-11-18 · Android API 어댑터와 볼트 저장 동기화

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 6단계 두 번째 PR로 Android API 포트·오류 타입·HTTP 어댑터(엔드포인트 8종)와 볼트 저장 유스케이스(409 자동 병합 3회, 5MB 상한, revision 되돌림 감지)를 넣고, API 계약 검사가 Android 어댑터도 대조하게 했다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle, JDK 내장 HTTP 서버(테스트)
- 플러그인·스킬: 없음

## 요청

6a PR을 머지하고 6b(API·동기화)를 진행하는 요청이었다.

## AI 산출물

- `:core:domain`: `VaultApi`·`AuthApi` 포트, `ApiError`·`ServerErrorCode`, `EncryptedVaultPayload.canonicalJson()`, `SaveVaultUseCase`·`SyncError`
- `:core:data`: `ApiEndpoints`, `HttpApiClient`(HttpURLConnection·org.json, HTTPS 강제, 30초, 429 Retry-After)
- 앱: 인터넷 권한, `network_security_config.xml`(localhost·127.0.0.1만 HTTP)
- `Scripts/check-api-contract.sh`: Android 오류 코드·엔드포인트 대조 추가

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #135 머지 후 6b 진행 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 유스케이스 8개, 오류 코드·정규 JSON 2개, HTTP 어댑터 11개 추가 |
| `bash Scripts/check-api-contract.sh` | 통과. 엔드포인트 하나를 일부러 지웠을 때 위반을 잡는 것도 확인 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. 실제 백엔드와 붙여 보지 않았다. 화면 단계에서 로컬 백엔드(adb reverse)로 확인한다.
2. 오프라인 대기(VLT-002)와 기기 저장(VLT-003)은 다음 PR(6c)에서 다룬다.

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| `ServerErrorCode`에 명세의 `ORIGIN_NOT_ALLOWED`를 빠뜨림 | 새로 넓힌 계약 검사가 잡음 | 코드 추가 |
| `PreloginInfo`를 바이트 배열 생성자 인자가 있는 data class로 씀 | 컴파일 | 일반 클래스로 바꿈 |
