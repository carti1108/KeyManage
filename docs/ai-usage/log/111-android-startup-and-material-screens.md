# 2026-11-20 · Android 시작 오류 수정과 Material 3 화면 정리

- 이슈·PR: 없음
- 커밋: 없음(커밋 전)

> **요약** Android 앱이 시작하자마자 죽던 오류 두 개를 고치고, 화면을 디자인 시스템 문서 기준(모양은 Material 3, 구성은 목업)으로 다시 그렸다.

## 사용 도구

- 도구: Claude Code(Fable 5.2), Gradle, Android 에뮬레이터(adb), 내장 브라우저(Figma 열람)
- 플러그인·스킬: 없음

## 요청

에뮬레이터에서 앱을 띄워 확인하려는 요청에서 시작했다. 앱이 뜬 뒤 화면이 Figma 목업과 많이 다르다는 지적이 있었고, 목업과 대조해 고치라는 요청이 이어졌다.

## AI 산출물

- `:app`: 테마 부모를 AppCompat으로 바꾸고 `AppRootDependency`의 속성 초기화 순서를 고쳤다. 앱이 시작 직후 죽던 원인 두 개다
- `:core:designsystem`: `KMTopBar`·`KMScreen`(M3 `TopAppBar`), `KMTextField`(M3 Filled 필드), `KMIcons`(Material Icons 경로 7개, 의존성 추가 없음). 테마가 비워 두던 M3 표면·컨테이너 색을 무채색 토큰으로 채웠다
- `:feature:*`: 로그인·가입·잠금·볼트 목록·상세·편집·생성기·설정·백업·계정 삭제 화면을 위 컴포넌트로 다시 구성했다. 계정 삭제는 M3 `AlertDialog`, 탭 바는 아이콘이 있는 M3 `NavigationBar`다
- `:feature:vaultlist`: 목록 줄에서 비밀번호를 바로 복사한다(목업 4단계)
- `Apps/Android/docs/platform-bindings.md`: 화면 모양 기준, 아이콘 표, 색 매핑 표
- `Apps/Android/docs/device-checklist.md`: 기기 확인 항목 48개

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 교정 | 처음에는 목업의 모양까지 그대로 옮겼다(칸 위 이름표, 직접 그린 위쪽 바·아이콘, 화면 코드의 숫자 리터럴). 디자인 시스템 문서가 Android 표현 계층을 Material 3로 정해 두었고 목업은 M3를 정확히 그리지 못한 것일 수 있다는 지적을 받아, 모양은 M3·구성은 목업으로 다시 고쳤다. |
| 교정 | 이전 기록에서 Android 구현이 끝났다고 보고했으나 main의 앱은 시작조차 되지 않았다. 단위 테스트와 빌드만 보고 실행을 확인하지 않은 것이 원인이다. |
| AI 기본값 | 아이콘 라이브러리 의존성은 확인이 필요한 결정이라 추가하지 않고 경로 데이터만 넣었다. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew assembleDebug test lint` | 통과. 목록 줄 복사 테스트 3개 추가 |
| `bash Scripts/check-policy-acceptance.sh` | 통과 |
| `bash Scripts/check-docs.sh` | 통과 |
| 에뮬레이터 실행 | 시작 오류 없이 잠금 화면 표시. 접근성 트리로 요소 확인 |

## 검증하지 못한 것

1. 앱이 화면 캡처를 막아 실제 모양을 AI가 보지 못했다. 화면 확인은 사람이 에뮬레이터에서 했다.
2. `KMIcons` 경로 데이터는 기억에 의존해 넣었고 원본과 대조하지 않았다.
3. Figma 목업의 Android 줄은 아직 M3 모양으로 고치지 않았다.
