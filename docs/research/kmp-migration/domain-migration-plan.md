# 도메인 층 commonMain 이전 계획

- 상태: 조사 문서(실험 브랜치 `research/kmp-migration` 전용, main에 합치지 않는다)
- 날짜: 2026-11-12
- 관련: [target-architecture.md](target-architecture.md), [react-bridge.md](react-bridge.md)

> **요약** 웹 TypeScript 도메인(`Apps/Web/src/domain`, 3,445줄, 테스트 5,021줄)을 기준으로 삼아 `Apps/Shared/domain`의 `commonMain`으로 옮긴다. 웹 도메인은 이미 플랫폼 기능을 포트로 분리해 두었고, Apple 도메인에 없는 기기 볼트 보존(VLT-003)까지 갖췄기 때문이다. 의존 순서대로 아래부터 옮기고, 각 단계는 TypeScript 테스트를 Kotlin 테스트로 먼저 옮긴 뒤 구현한다. 저장 데이터 형식(볼트 JSON, 백업 파일, 기기 기록)은 바꾸지 않는다.

---

## 1. 지금 두 도메인

| 항목 | 웹 (TypeScript) | Apple (Swift) |
| :--- | :--- | :--- |
| 규모 | 40개 파일 3,445줄, 테스트 29개 파일 5,021줄 | 58개 파일 5,161줄 |
| 세션 구조 | `session/` 10개 파일로 나뉨(로그인, 잠금 해제 단계, 대기 전송, 기기 동기화, 비밀번호 변경, 계정 삭제) | `VaultLockManager.swift` 한 파일 1,733줄에 몰림 |
| 플랫폼 분리 | 서버·저장소·탭·타이머·클립보드·백업 파일을 모두 포트로 받는다. 직접 쓰는 것은 `crypto.subtle`·`getRandomValues`·`TextEncoder`·`Date`뿐 | CryptoKit·CommonCrypto·Security·URLSession을 도메인이 직접 쓴다 |
| 오프라인 대기 동기화(VLT-002) | 있음 | 일부. `PendingSyncEngine`은 만들어 두었지만 쓰는 곳이 없다 |
| 기기 볼트 보존(VLT-003) | 있음(IndexedDB) | 포트만 있고 구현 없음 |
| 되돌림 감지·백업·비밀번호 변경·계정 삭제 | 있음 | 있음 |
| 탭 간 통신(WEB-001) | 있음 | 해당 없음 |

결론: **웹 도메인을 옮길 원본으로 삼는다.** 구조가 이미 포트로 나뉘어 있어 Kotlin `commonMain`에 그대로 대응하고, 테스트 5,021줄을 행동 명세로 쓸 수 있다. Apple은 공유 도메인으로 바꾸는 순간 VLT-002·VLT-003을 함께 얻는다.

## 2. 목표 모듈

```text
Apps/Shared/
  crypto/        (있음) 키 유도·볼트 암호
  secure-store/  (있음) 키체인·Keystore·생체 인증
  device-store/  (있음, 웹만) 기기 기록 저장 위치 → Android·Apple 구현 추가
  domain/        (새로) commonMain: auth, vault, backup, device, lock, clipboard, generator, tabs, session
  server-http/   (새로) commonMain: AuthApi·VaultApi의 HTTP 구현
```

- `domain`은 한 모듈에 패키지로 나눈다. 웹 폴더 간 의존이 서로 얽혀 있어(세션이 거의 전부를 쓴다) 모듈을 잘게 나누면 순환만 생긴다.
- `domain`의 `commonMain`에는 플랫폼 타입을 두지 않는다. 시간·난수·저장소·서버·탭·클립보드·백업 파일은 모두 인터페이스로 받는다.

## 3. 포트 대응

| 웹 포트 | Kotlin 포트(`commonMain`) | 웹 구현 | Android 구현 | iOS 구현 |
| :--- | :--- | :--- | :--- | :--- |
| `AuthApi`, `VaultApi` | 같은 이름, `suspend` 함수 | `server-http` | `server-http` | `server-http` |
| `DeviceStore` | `device-store`의 `DeviceStore`(있음) | IndexedDB(있음) | 앱 전용 파일 | 앱 전용 파일 |
| `TabMemory`, `Tabs` | 같은 이름 | sessionStorage, BroadcastChannel·Web Locks | 단일 탭 구현(공통 기본값) | 단일 탭 구현 |
| `Scheduler`, `now` | `Clock`(`kotlin.time`), `Scheduler` | 브라우저 타이머 | 코루틴 기본 구현 | 코루틴 기본 구현 |
| `ClipboardWriter` | 같은 이름 | Clipboard API | ClipboardManager | UIPasteboard |
| `BackupFiles` | 같은 이름 | 다운로드·파일 선택 | SAF | 문서 선택기 |
| `AutoLockSettingStore` | 같은 이름 | localStorage | SharedPreferences | UserDefaults |
| `crypto.getRandomValues` | cryptography-kotlin의 보안 난수 | WebCrypto | 플랫폼 | 플랫폼 |

- 웹 전용 개념(탭 간 통신, 탭 메모리)은 공통 인터페이스로 두고, 모바일은 "탭이 하나뿐"인 공통 기본 구현을 쓴다. 세션 코드에 플랫폼 분기가 생기지 않는다.
- Promise는 `suspend`로, 리스너(`onLock`, `onSyncChange` 등)는 `Flow`로 바꾼다. JS·Swift로 내보내는 일은 도메인이 아니라 그 위 RIB·브리지 층이 맡는다(ADR-0006·ADR-0009 방식).

## 4. 순서

의존이 적은 것부터 옮긴다. 각 단계는 같은 방식으로 진행한다: **TypeScript 테스트를 Kotlin `commonTest`로 옮김(RED) → 구현(GREEN) → JVM·JS에서 통과 → 문서 기록.**

| 단계 | 대상(웹 기준) | 규모(코드 / 테스트) | 확인 기준 |
| :--- | :--- | :--- | :--- |
| 0 | 기반: `server` 오류 타입, base64, 시간·난수 포트, kotlinx.serialization 설정, `domain` 모듈 뼈대 | 약 80 / 0 | 모듈이 JVM·JS·Android·Apple 대상으로 빌드 |
| 1 | `vault`의 순수 부분: `vaultData`, `itemList`, `mergeVaults` | 약 250 / 약 300 | 웹이 만든 볼트 JSON을 그대로 읽고, 다시 쓴 결과가 바이트 단위로 같다 |
| 2 | 작은 독립 모듈: `generator`, `lock`, `clipboard` | 375 / 588 | 옮긴 테스트 통과. 비밀번호 생성 규칙(GEN-001) 동일 |
| 3 | `auth`, `device`(`deviceRecord`, `reconcileDeviceRecord`, `devicePersistence`, `tabMemory`), `tabs` | 591 / 596 | 웹이 저장한 기기 기록을 그대로 읽는다(형식 버전 1) |
| 4 | `vaultSync`, `backup` | 523 / 약 880 | 웹이 만든 백업 파일을 Kotlin이 열고, 반대도 된다 |
| 5 | `session` 10개 파일 | 1,403 / 2,468 | 옮긴 세션 테스트 전부 통과. 상태 전이·되돌림·대기 전송·탭 동기화 동일 |
| 6 | `server-http`: `AuthApi`·`VaultApi` HTTP 구현 | 웹 `adapters/http` 약 300 | 로컬 백엔드에 대한 통합 테스트(가입 → 저장 → 잠금 해제) |

- 1~4단계는 서로 독립인 부분이 있어 단계 안에서는 순서를 바꿔도 된다. 5단계는 1~4가 끝나야 시작한다.
- 각 단계가 끝나도 웹·Apple 앱 코드는 아직 바꾸지 않는다. 공유 도메인이 TypeScript 테스트와 같은 행동을 한다는 것만 확인한다.

## 5. 앱 전환과 기존 코드 삭제

도메인을 다 옮긴 뒤(5·6단계 이후) 위 층부터 바꾼다.

1. 공유 RIB(잠금 해제부터)을 `commonMain`에 만든다. 이 RIB은 공유 세션을 직접 쓴다.
2. 웹: 해당 화면을 React 뷰 + 공유 RIB으로 바꾸고, 바꾼 범위의 `Apps/Web/src/domain`·`ui/session` 코드를 지운다. 기존 화면 테스트(`SessionRoot.test.tsx` 등)가 그대로 통과해야 한다.
3. Android: 같은 RIB에 Compose 뷰를 붙인다(새 앱).
4. iOS: 같은 RIB에 SwiftUI 뷰를 붙이고 `VaultLockManager`와 Swift 도메인을 지운다(Mac에서 진행).

옮기는 동안에는 TypeScript 도메인과 Kotlin 도메인이 함께 있다. 지우는 기준은 "그 코드를 쓰던 화면이 공유 RIB으로 바뀌었고, 그 화면 테스트가 통과한다"이다.

## 6. 바꾸지 않는 것

- 볼트 평문 JSON, 백업 파일, 기기 기록(버전 1)의 형식. 단계마다 웹이 만든 데이터로 교차 확인한다.
- 키 유도·암호 규격(이미 `crypto`에서 확인).
- 서버 API 계약.
- 정책(`docs/policies/`)의 경계값과 문구. 문구 상수는 `domain`으로 옮기되 내용은 그대로 둔다.

## 7. 정해야 할 것

| 항목 | 추천 | 이유 |
| :--- | :--- | :--- |
| HTTP 클라이언트(6단계) | Ktor Client(플랫폼별 엔진: JS fetch, OkHttp, Darwin) | KMP 표준 선택지이고 세 대상을 모두 지원한다. 직접 `expect/actual`로 만들면 재시도·타임아웃·오류 처리를 플랫폼마다 다시 써야 한다. 새 외부 의존성이라 ADR을 쓰고 확인받는다 |
| 직렬화 | kotlinx.serialization | `crypto` 테스트에서 이미 쓴다 |
| 시간 | `kotlin.time.Clock`(표준 라이브러리) | 별도 의존성이 필요 없다 |

## 8. 알려진 부담

1. 세션(5단계)이 전체의 40%다. 웹 세션은 Promise 동시성(직렬화된 기기 쓰기, 탭 잠금)에 기대므로 코루틴 `Mutex`로 옮길 때 순서가 바뀌지 않는지 테스트로 확인해야 한다.
2. TypeScript 테스트는 `vi.fn`·가짜 타이머를 많이 쓴다. Kotlin에서는 손으로 만든 가짜 구현과 `runTest`의 가상 시간으로 바꾼다.
3. Apple 도메인에만 있는 동작이 있는지 아직 대조하지 않았다. 5단계 전에 `VaultLockManager`를 웹 세션 테스트 목록과 대조해 빠진 동작을 찾는다.
4. 오류를 사용자 문구로 바꾸는 코드(`describeError`)는 웹 `ui`에 있다. 플랫폼 공통 문구라면 RIB 단계에서 `commonMain`으로 옮긴다.
