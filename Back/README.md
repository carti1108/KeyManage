# ⚙️ KeyManage 백엔드 (Backend)

포트-어댑터(Hexagonal) 멀티 모듈 백엔드 서버입니다 ([ADR-0002](docs/adr/ADR-0002-hexagonal-multi-module-backend.md)).

서버는 영지식(Zero-Knowledge) 보안 원칙에 따라 복호화 키나 평문 볼트를 일절 다루지 않으며, 클라이언트가 전송한 암호문(Ciphertext)의 원자적 저장, 이중 해싱 기반 계정 인증, 그리고 낙관적 잠금 동시성 제어(`revision` 관리)만 수행합니다 ([SEC-001](../docs/policies/SEC/SEC-001-client-side-zero-knowledge-encryption.md)).

---

## 🛠️ 기술 스택

| 분류 | 기술 규격 및 사양 |
| :--- | :--- |
| **Language** | Kotlin 2.1 (JVM 21) |
| **Framework** | Spring Boot 3.4 |
| **Build Tool** | Gradle (Kotlin DSL) |
| **Security & Auth** | Argon2id + 서버 비밀키(Pepper) 이중 해싱, Bearer 세션 토큰 ([ADR-0008](../docs/adr/ADR-0008-account-auth-and-server-sessions.md)) |
| **Testing** | JUnit 5, MockK, PIT (뮤테이션 테스트) |
| **Storage Engine** | 원자적 파일 저장소 (Atomic File-Based Storage, [ADR-0003](docs/adr/ADR-0003-atomic-file-based-vault-storage.md), [ADR-0007](docs/adr/ADR-0007-vault-optimistic-locking.md)) |

---

## 🧩 모듈 구조

비즈니스 코어인 `domain` 모듈을 중심에 두고, 외부 프레임워크와 저장소 의존성을 포트-어댑터로 격리한 4모듈 헥사고날 아키텍처를 따릅니다. `domain` 모듈은 Spring 등 외부 웹·인프라 프레임워크 의존성을 일절 갖지 않습니다.

```text
Back/
├── domain/            # 순수 비즈니스 코어: 계정 인증, 마스터 비밀번호 변경, 계정 삭제, 볼트 낙관적 잠금 유스케이스 및 포트
├── api/               # 인바운드 어댑터: Spring Web MVC REST 컨트롤러(Auth, Vault), 요청/응답 DTO, 인증 필터, Rate Limiter
├── infrastructure/    # 아웃바운드 어댑터: 임시 파일 쓰기 + 원자적 교체(Atomic Move) 기반 볼트·계정·세션 저장소, Argon2id 해셔
└── app/               # 조립 루트(Composition Root): Spring Boot 애플리케이션 진입점 및 빈 조립 설정
```

---

## 📡 주요 제공 API

상세 요청·응답 스키마 및 표준 오류 코드는 [`docs/api-spec.md`](../docs/api-spec.md)에 정의되어 있습니다.

- `POST /api/v1/auth/prelogin`: 계정 KDF 파라미터(`salt`, `iterations`) 조회 (미존재 이메일도 결정론적 가짜 솔트 반환으로 열거 공격 차단)
- `POST /api/v1/auth/register`: 신규 계정 등록 및 초기 세션 발급 (`KEYMANAGE_REGISTRATION_ENABLED` 토글 지원)
- `POST /api/v1/auth/login`: 인증 해시 검증 및 세션 토큰 발급
- `POST /api/v1/auth/logout`: 현재 세션 토큰 즉시 폐기
- `POST /api/v1/auth/change-password`: 현재 인증 해시 검증, 새 KDF·재암호화 볼트 원자적 교체 및 타 기기 세션 일괄 무효화 ([SEC-004](../docs/policies/SEC/SEC-004-master-password-change.md))
- `POST /api/v1/auth/delete-account`: 인증 해시 재검증 후 계정·볼트·전체 세션 영구 삭제 ([SEC-005](../docs/policies/SEC/SEC-005-account-deletion.md))
- `GET /api/v1/vault`: 현재 계정의 암호화 볼트 및 `revision` 조회
- `POST /api/v1/vault`: `expectedRevision` 낙관적 잠금 검증 후 암호화 볼트 원자적 저장 및 `revision` 단조 증가 ([VLT-001](../docs/policies/VLT/VLT-001-cross-platform-vault-sync.md))

---

## 🧪 빌드 및 검증

```bash
cd Back
./gradlew check :domain:pitest
python3 ../Scripts/mutation-score.py domain/build/reports/pitest/mutations.xml
```

---

## 🔧 트러블 슈팅

- **동시 저장 경합 시 파일 손상 및 부분 쓰기 방지**: 볼트 및 계정 파일 저장 시 대상 파일을 직접 덮어쓰지 않고 임시 파일(`.tmp`)에 전체 바이트를 기록·플러시한 뒤 `ATOMIC_MOVE`로 교체하며, 계정 단위 락을 통해 동시 요청 시 `expectedRevision` 불일치를 `409 REVISION_CONFLICT`로 안전하게 격리했습니다 ([ADR-0003](docs/adr/ADR-0003-atomic-file-based-vault-storage.md)).
- **이메일 열거 공격(Enumeration Attack) 차단**: `/api/v1/auth/prelogin`과 `/api/v1/auth/login`에서 미가입 이메일 요청 시에도 서버 비밀키 기반 HMAC으로 결정론적 가짜 솔트를 반환하고 더미 Argon2id 연산을 수행하여 응답 형태와 타이밍 차이를 제거했습니다 ([SEC-002](../docs/policies/SEC/SEC-002-account-auth-and-session.md)).
