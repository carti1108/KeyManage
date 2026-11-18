# 2026-11-18 · Android 평문 볼트 스키마와 병합

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 6단계 첫 PR로 Android `:core:domain`에 평문 볼트 모델·VLT-001 항목 병합·삭제 표시·검증 규칙을, `:core:data`에 org.json 기반 볼트 JSON 코덱을 넣었다. 모르는 필드 보존과 시각 형식 규칙을 테스트로 고정했다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

5단계 PR을 머지하고 6단계(데이터·동기화)를 진행하는 요청이었다. 6단계는 PR을 나눠 진행한다.

## AI 산출물

- `VaultData`·`VaultItem`(모르는 필드는 원래 JSON 조각으로 보관), `VaultFormatError`
- `VaultMerger`: 최종 변경 시각이 늦은 쪽, 같으면 서버, 한쪽에만 있는 항목은 유지. 웹 `mergeVaults`와 같은 순서
- `VaultRules`: 변경 시각(현재와 이전 + 1초 중 큰 값), 삭제 표시(id·시각만 남김), 저장 전 검증(서비스 이름, id 중복, 버전)
- `VaultSerializer` 포트와 `JsonVaultSerializer`(org.json, 시간대 없는 시각·타입 오류 거절, null은 필드 없음으로 처리)

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #134 머지 후 6단계 진행 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 도메인 테스트 14개, 코덱 테스트 10개 추가 |
| 암호화 벡터 평문을 코덱으로 읽기 | 통과 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. 실제 기기의 Android org.json 구현으로는 돌리지 않았다. 단위 테스트는 같은 API의 org.json 라이브러리로 돌렸다.
2. 다른 플랫폼이 쓴 볼트와의 병합 결과 비교는 8단계 교차 검증에서 한다.

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| 테스트에서 `JSONObject.keySet()`을 씀 | 컴파일(Android 스텁에 없음) | `keys()`로 바꿈 |
