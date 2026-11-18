# 2026-11-18 · Android 암호 엔진 구현

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** Android `:core:domain`에 비밀번호 전처리와 KDF 파라미터 검사를, `:core:data`에 PBKDF2·HKDF·AES-256-GCM 구현을 넣고 플랫폼 공용 테스트 벡터를 모두 재현했다. 백업 포맷은 볼트 스키마와 함께 6단계로 넘겼다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

이슈 #3 계획의 3단계(암호 엔진)를 진행하는 요청이었다.

## AI 산출물

- `PasswordPreprocessor`: NFC 정규화, 지정 공백 6종 trim, 짝 없는 서로게이트 거절
- `KdfParameters`: salt 16바이트, 반복 횟수 600,000~10,000,000 검사(SEC-002)
- `CryptoError`: 전처리·파라미터·입력·인증 실패 구분
- `Pbkdf2MasterKeyDeriver`, `HkdfKeySeparator`, `AesGcmVaultCipher`(javax.crypto, IV는 SecureRandom)
- 테스트: 도메인 단위 테스트, `docs/crypto-test-vectors.json`을 읽는 벡터 테스트, AES-GCM 변조·길이 검사 테스트

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | 3단계 진행 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint` | 통과. 공용 벡터 키 유도 5건과 암호화 1건 재현 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. 실제 기기에서 PBKDF2 600,000회 소요 시간을 재지 않았다. 잠금 해제 응답 시간은 화면 구현 때 확인한다.
2. 키 바이트를 메모리에서 지우는 처리는 함수 안 임시값만 다뤘다. 세션 키 수명 관리는 잠금 기능(SEC-003)에서 다룬다.

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| 처음 계획에서 백업 포맷을 3단계에 넣음 | 백업 명세를 읽으며 AI가 발견 | 볼트 스키마·복원 병합에 묶여 있어 6단계로 옮김 |
| 바인딩 문서에 PBKDF2를 `SecretKeyFactory`로 적어 둠 | 구현 중 AI가 발견 | `char[]` 입력 문제로 HMAC 직접 계산으로 바꾸고 문서를 고침 |
