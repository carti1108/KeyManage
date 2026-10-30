# 2026-10-30 · Apple 멀티플랫폼 암호 모듈 단일 에이전트 TDD 구현 (#40)

> 요약: ADR-0017에 정의된 단일 에이전트(Single Agent) 방식으로 외부 서브에이전트 호출 없이 Apple(iOS·macOS) 공용 영지식 암호 모듈을 TDD(RED ➡️ GREEN) 루프로 구현했다. 마스터 비밀번호 전처리(`PasswordPreprocessor`), PBKDF2 키 유도(`PBKDF2KeyDeriver`), HKDF 키 분리(`HKDFKeySeparator`), AES-256-GCM 볼트 암복호화(`VaultCipher`)를 완성하고 `docs/crypto-test-vectors.json`의 4개 테스트 벡터를 비트 단위 100% 재현했다.

## 사용 도구

- 도구: Google Antigravity (Gemini), Swift 6 Toolchain, Tuist 4.x, CommonCrypto, CryptoKit
- 플러그인/스킬: `modern-swift-testing`, `clean-architecture-layers`, `project-docs:ai-usage`

## 요청

- ADR-0014·ADR-0015 아키텍처에 따라 iOS·macOS가 공유하는 암호 엔진을 단일 에이전트 루프로 구현.
- `docs/crypto-test-vectors.json` 4개 벡터(전처리 결과, 마스터키, 암호화키, 인증 해시, 볼트 페이로드) 비트 단위 재현.
- `Scripts/verify-apple.sh` 및 `Scripts/check-docs.sh` exit 0 통과.

## AI 산출물

- `Apps/Apple/Projects/Core/Domain/Sources/Crypto/`:
  - `PasswordPreprocessor.swift`: NFC 정규화, 지정 유니코드 스칼라 공백 앞뒤 제거, 짝이 맞지 않는 단독 UTF-16 서로게이트 거절, UTF-8 변환.
  - `PBKDF2KeyDeriver.swift`: CommonCrypto 기반 PBKDF2-HMAC-SHA256 키 유도(반복 횟수 60만~1000만, salt 16바이트 경계 검증).
  - `HKDFKeySeparator.swift`: CryptoKit 기반 HKDF-SHA256 (`keymanage-enc`, `keymanage-auth`) 32바이트 대칭키 및 인증 해시 분리.
  - `VaultCipher.swift`: CryptoKit AES-256-GCM 기반 볼트 암복호화, 12바이트 무작위 IV 생성, 16바이트 인증 태그 분리 저장, `keymanage-vault|{version}|{cipher}` AAD 바인딩, 형식 오류와 인증(태그) 실패 오류 엄격 분리.
  - `EncryptedVaultPayload.swift`: 표준 암호화 볼트 전송/저장 컨테이너 모델.
- `Apps/Apple/Projects/Core/Domain/Tests/CryptoModuleTests.swift`:
  - 전처리 벡터·트리밍·서로게이트 거절 테스트 (4건)
  - PBKDF2 키 유도 벡터 및 salt/반복횟수 경계 테스트 (3건)
  - HKDF 키 분리 벡터 및 독립성 테스트 (2건)
  - AES-GCM 암복호화 벡터, 무작위 IV, 키/암호문/태그/헤더 변조 거절 및 형식 오류 테스트 (6건)
- `Apps/Apple/Projects/Core/Data/`:
  - Tuist 프로젝트 설정 및 `KeyManageDomain` 리익스포트 계층 구성.
- `.gitignore`: 로컬 볼트 저장 폴더 경로(`/data/`)를 루트 기준으로 한정하여 APFS 대소문자 무구분 환경에서 `Core/Data/`가 무시되던 결함 수정.
- `Scripts/verify-apple.sh`: `KeyManageDomain` 및 `KeyManageData` 타깃 단위 테스트 검증 단계 추가.

## 사람의 판단

- 터미널 실행 승인 팝업 관련 안내 후, 사용자의 "그냥 진행해 묻지말고" 지침에 따라 불필요한 중간 질의 없이 자율적으로 설계를 판단하고 TDD 루프를 완결했다.

## 진행 중 내린 가정 (Assumption)

1. **암호 엔진 모듈 위치**: ADR-0014(iOS·macOS 비즈니스 로직 100% 공유) 및 ADR-0015에 따라 암호 엔진의 핵심 계약 및 알고리즘 구현체를 `Core/Domain`에 배치하고, `Core/Data`는 이를 리익스포트하여 향후 영속성/원격 저장소 어댑터와 자연스럽게 연결되도록 구성했다.
2. **공백 제거 단위**: Swift에서 `\r\n`이 단일 Character(확장 그래핌 클러스터)로 결합되는 문제를 방지하기 위해 `Character`가 아닌 `UnicodeScalar` 단위로 지정된 6개 공백 코드포인트를 정확히 판별하여 제거했다.
3. **오류 분류 기준**: AAD 불일치, 암호문 비트 반전, 태그 불일치, 잘못된 키 입력은 모두 AEAD 무결성 검증 실패이므로 `VaultCipherError.authenticationFailed`로 통일하고, Base64 디코딩 실패나 길이 불일치는 `VaultCipherError.invalidFormat`으로 명확히 구분했다.
4. **.gitignore 수정**: 루트의 `data/` 규칙이 macOS APFS에서 `Projects/Core/Data`를 가리는 부작용을 해소하기 위해 `/data/`로 루트 경로를 한정했다.

## 검증

- `CryptoModuleTests` 15개 단위 테스트 전체 통과 (0.865초).
- `Scripts/verify-apple.sh` 4중 기계적 통합 검증 통과 (문서 검증, Tuist 생성, macOS 빌드, 단위 테스트 전수).
- `Scripts/check-docs.sh` 무결성 검사 통과.

## 검증하지 못한 것

- 실제 원격 백엔드 API와의 HTTPS 통신을 통한 볼트 동기화(향후 UseCase 및 Network Adapter 티켓에서 진행).

## AI의 오류와 정정

- **RED 단계**: 초기 `CryptoModuleTests.swift` 작성 시 Base64 디코딩 도우미 호출부 오타로 컴파일 에러 발생 ➡️ 즉시 문법 수정 후 의도된 실패(RED) 13건 확인.
- **GREEN 단계**: `HKDFKeySeparator.swift`에서 `authInfo` 대신 `authHash` 변수명을 잘못 참조한 컴파일 오류 발견 ➡️ 즉시 `authInfo`로 수정.
- **문자열 트리밍 오류**: `Character` 기반 트리밍 시 `\r\n`이 단일 그래핌 클러스터로 묶여 `\r`, `\n` 개별 검사에서 누락되는 현상 발견 ➡️ `UnicodeScalar` 기반 슬라이싱으로 전면 재작성하여 완벽 해결.

## 근거 링크

- 이슈 #40
- docs/vault-spec.md
- docs/crypto-test-vectors.json
- docs/adr/ADR-0011-password-preprocessing-and-header-aad.md
- docs/adr/ADR-0014-apple-multiplatform-microribs.md
- docs/adr/ADR-0015-tuist-micro-features-modularization.md
- docs/adr/ADR-0017-single-agent-vs-subagent-experiment.md
