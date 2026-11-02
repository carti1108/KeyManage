# 🔐 KeyManage

> **KeyManage**는 영지식(Zero-Knowledge) 보안 원칙에 기반하여 마스터 키 유도와 볼트 암·복호화를 오직 클라이언트 내부에서만 수행하고 서버에는 암호문(Ciphertext)만을 저장하는 멀티 플랫폼(Web, Desktop, iOS, macOS) 종단간 암호화(E2EE) 패스워드 매니저입니다.

---

## 📑 목차
- [✨ 주요 기능](#-주요-기능)
- [🏛️ 아키텍처 및 폴더 구조](#️-아키텍처-및-폴더-구조)
- [🛠️ 기술 스택](#️-기술-스택)
- [🚀 사용법](#-사용법)
- [🔧 트러블 슈팅](#-트러블-슈팅)

---

## ✨ 주요 기능

- **영지식(Zero-Knowledge) 종단간 암·복호화 ([SEC-001](docs/policies/SEC/SEC-001-client-side-zero-knowledge-encryption.md))**
- **인증 해시 분리 및 이중 해싱 계정 인증 ([SEC-002](docs/policies/SEC/SEC-002-account-auth-and-session.md))**
- **자동 유휴 잠금 및 생체 인증 접근 제어 ([SEC-003](docs/policies/SEC/SEC-003-unlock-and-auto-lock.md))**
- **마스터 비밀번호 변경 및 원격 KDF 변경 감지 ([SEC-004](docs/policies/SEC/SEC-004-master-password-change.md))**
- **계정 삭제 및 서버·기기 기록 영구 소거 ([SEC-005](docs/policies/SEC/SEC-005-account-deletion.md))**
- **낙관적 잠금 기반 볼트 동기화 및 3-way 병합 ([VLT-001](docs/policies/VLT/VLT-001-cross-platform-vault-sync.md))**
- **오프라인 대기 큐 및 재연결 동기화 ([VLT-002](docs/policies/VLT/VLT-002-offline-pending-sync.md))**
- **암호화 기기 저장소 영속 보존 ([VLT-003](docs/policies/VLT/VLT-003-device-vault-persistence.md))**
- **독립 암호화 백업 내보내기 및 복원 ([VLT-004](docs/policies/VLT/VLT-004-encrypted-backup-export-restore.md))**
- **CSPRNG 비밀번호 생성기 및 엔트로피 진단 ([GEN-001](docs/policies/GEN/GEN-001-password-generation-and-entropy-scoring.md))**
- **보안 클립보드 버퍼 및 30초 자동 소각 ([COM-001](docs/policies/COM/COM-001-clipboard-auto-burn.md))**

---

## 🏛️ 아키텍처 및 폴더 구조

KeyManage는 백엔드와 프론트엔드 전반에 걸쳐 비즈니스 도메인을 프레임워크와 완전히 격리하는 설계를 유지합니다.

### 🧭 핵심 설계 원칙

1. **영지식(Zero-Knowledge) 보안 격리**: 마스터 키 유도 및 암·복호화 연산은 클라이언트 내부에서만 수행하며, 중앙 서버는 암호문 외 어떠한 키 데이터도 보관하지 않습니다.
2. **포트-어댑터(Hexagonal) 멀티 모듈**: 백엔드는 비즈니스 도메인(`domain`)을 중심으로 인바운드/아웃바운드 어댑터(`api`, `infrastructure`, `app`)의 관심사를 엄격히 분리합니다.
3. **의존성 역전(DIP) 및 단방향 상태 관리**: 프론트엔드 UI 계층은 비즈니스 로직을 배제한 상태 표출 껍데기(Dumb View)로 유지하며, 백엔드 통신 및 암호화 모듈은 추상화 인터페이스를 통해 주입받습니다.

### 🔄 전체 시스템 흐름도

```mermaid
flowchart TD
    subgraph Client["클라이언트 (Web / Desktop / Apple)"]
        UI["UI View / Component"]
        Domain["Domain Logic / UseCase"]
        Crypto["Zero-Knowledge Crypto Engine<br/>(PBKDF2 / HKDF / AES-GCM)"]
        LocalStorage["Local Secure Storage<br/>(IndexedDB / Keychain)"]
        
        UI --> Domain
        Domain --> Crypto
        Domain --> LocalStorage
    end

    subgraph Server["중앙 백엔드 서버 (Hexagonal Multi-Module)"]
        API["API Adapter (REST Controller)"]
        CoreDomain["Back Domain (Optimistic Lock Arbiter)"]
        Infra["Infrastructure Adapter (Atomic File/DB)"]
        
        API --> CoreDomain
        Infra --> CoreDomain
    end

    Crypto -- "HTTPS / 암호문 전송 (E2EE)" --> API
```

<details>
<summary><strong>📂 레포지토리 디렉터리 구조 및 플랫폼별 세부 문서</strong></summary>

```text
KeyManage/
├── Apps/
│   ├── Apple/                 # Apple 네이티브 앱 (iOS, macOS) ➔ [Apple README](Apps/Apple/README.md)
│   │   ├── Projects/          # Tuist 4 마이크로 피처 모듈 (Core, Features, App)
│   │   └── docs/              # Apple 플랫폼 전용 ADR 및 명세
│   └── Web/                   # React 19 + Tauri v2 웹/데스크톱 앱 ➔ [Web README](Apps/Web/README.md)
│       ├── src/               # Clean Architecture 계층화 소스코드
│       ├── src-tauri/         # Tauri v2 데스크톱 네이티브 셸
│       └── docs/              # Web 플랫폼 전용 ADR 및 명세
├── Back/                      # Spring Boot 3.4 헥사고날 백엔드 ➔ [Backend README](Back/README.md)
│   ├── domain/                # 순수 Kotlin 비즈니스 도메인 및 유스케이스 코어
│   ├── api/                   # Spring Web MVC REST API 컨트롤러 및 필터
│   ├── infrastructure/        # 원자적 파일 볼트 저장소 어댑터
│   └── app/                   # 조립 루트(Composition Root)
├── Docker/                    # 로컬 데모 실행용 Dockerfile 및 Caddyfile
├── Scripts/                   # 다중 플랫폼 정적 검증 및 품질 통제 스크립트
├── Tools/                     # 교차 플랫폼 암호 테스트 벡터 검증기 및 디자인 빌더
├── docs/                      # 프로젝트 통합 문서 (정책 SSOT, 공통 ADR, 명세)
│   ├── policies/              # 서비스 정책 SSOT (SEC, VLT, GEN, COM)
│   ├── adr/                   # 시스템 공통 아키텍처 결정 기록
│   ├── ai-usage/              # AI 협업 및 검증 기록 로그
│   ├── vault-spec.md          # 볼트 암호화 및 병합 표준 명세서
│   └── api-spec.md            # 백엔드 REST API 계약 명세서
└── AGENTS.md                  # 범용 개발 및 협업 가이드라인 (CLAUDE.md 호환)
```
</details>

---

## 🛠️ 기술 스택

각 플랫폼별 상세 아키텍처 및 세부 모듈 사양은 해당 플랫폼의 README 링크에서 확인할 수 있습니다.

| 영역 | 기술 스택 | 설명 | 상세 가이드 |
| :--- | :--- | :--- | :--- |
| **Backend** | Kotlin 2.1, Spring Boot 3.4, Gradle | 4개 모듈 분리 헥사고날 아키텍처, PIT 뮤테이션 테스트 | [Backend README](Back/README.md) |
| **Web Client** | React 19, TypeScript 5.8, Vite 6 | Clean Architecture, Tailwind CSS v4, WebCrypto API, Vitest | [Web README](Apps/Web/README.md) |
| **Desktop Shell**| Tauri v2, Rust | 경량 네이티브 웹뷰 래퍼, 엄격한 CSP 출처 격리 | [Desktop README](Apps/Web/README.md#-데스크톱-앱-tauri-v2) |
| **Apple Client** | Swift 6, Tuist 4, Uber microRIBs | Strict Concurrency, State-Action 단방향 RIBs, CryptoKit, Swift Testing | [Apple README](Apps/Apple/README.md) |
| **Infra & Demo** | Docker Compose, Caddy 2, mkcert | 로컬 리버스 프록시, 자동 상호 HTTPS 환경 구성 | [ADR-0021](docs/adr/ADR-0021-local-docker-compose-demo.md) |
| **Verification** | Python 3, Bash, GitHub Actions | 6중 정적 검증 게이트, 최신성 및 PR Rebase 강제 훅 | [ADR-0009](docs/adr/ADR-0009-verification-delegation-pipeline.md) |

---

## 🚀 사용법

> **요약** 로컬 환경에서 Docker Compose로 백엔드와 웹을 간편하게 실행할 수 있습니다. 웹만 사용하는 경우 Docker만 있으면 되며, 데스크톱 앱까지 연동할 경우 `mkcert`를 통해 로컬 HTTPS 환경을 구성합니다 ([ADR-0021](docs/adr/ADR-0021-local-docker-compose-demo.md)).

| 사용 환경 | 접속 인터페이스 | 필수 준비물 |
| :--- | :--- | :--- |
| 웹 브라우저 단독 실행 | http://localhost:8080 | Docker Desktop |
| 웹 + 데스크톱 앱 (Windows) 연동 | https://localhost:8443 | Docker Desktop, mkcert |

<details>
<summary><strong>1. 🌐 웹 브라우저로만 사용하기 (포트 8080)</strong></summary>

1. [Docker Desktop](https://www.docker.com/products/docker-desktop/)을 설치하고 실행합니다.
2. 레포지토리 루트에서 컨테이너를 빌드하고 실행합니다:
   ```bash
   docker compose up -d --build
   ```
3. 빌드가 완료되면 웹 브라우저에서 [http://localhost:8080](http://localhost:8080)으로 접속합니다.
</details>

<details>
<summary><strong>2. 🖥️ 데스크톱 앱까지 함께 사용하기 (HTTPS 포트 8443, Windows)</strong></summary>

데스크톱 앱은 보안 정책상 `https://localhost:8443`으로만 서버와 통신합니다. 로컬 신뢰 인증서를 생성한 후 데모를 실행합니다.

1. **mkcert 설치**:
   - Windows: `winget install FiloSottile.mkcert`
   - macOS: `brew install mkcert`
2. **로컬 CA 루트 인증서 등록**:
   ```bash
   mkcert -install
   ```
3. **로컬 인증서 발급 (`certs` 디렉터리)**:
   ```bash
   cd certs
   mkcert -cert-file localhost.pem -key-file localhost-key.pem localhost
   cd ..
   ```
4. **컨테이너 실행 및 확인**:
   ```bash
   docker compose up -d --build
   ```
   (이미 실행 중인 경우: `docker compose restart caddy`)
5. 웹 브라우저에서 [https://localhost:8443](https://localhost:8443) 접속 확인 후 데스크톱 앱을 실행합니다.

> [!NOTE]
> **데스크톱 앱 직접 빌드 시**:
> Node.js 22.18+, Rust, Visual Studio C++ 빌드 도구가 설치된 환경에서 `Apps/Web` 폴더로 이동 후 실행합니다:
> ```powershell
> cd Apps/Web
> npm ci
> $env:VITE_API_BASE_URL = 'https://localhost:8443'
> npm run tauri:build
> ```
> 빌드된 설치 파일(NSIS)은 `Apps/Web/src-tauri/target/release/bundle/nsis/`에 생성됩니다.
</details>

<details>
<summary><strong>3. 🔒 신규 계정 가입 차단 설정 (Registration Lockout)</strong></summary>

내 계정을 생성한 후 추가 가입을 막으려면, 레포지토리 루트의 `.env` 파일에 다음 환경변수를 설정하고 다시 실행합니다:
```bash
KEYMANAGE_REGISTRATION_ENABLED=false
```
적용:
```bash
docker compose up -d
```
</details>

<details>
<summary><strong>4. 🧹 컨테이너 종료 및 데이터 초기화</strong></summary>

| 작업 내용 | 실행 명령 | 비고 |
| :--- | :--- | :--- |
| 컨테이너 중지 (볼트 데이터 보존) | `docker compose down` | 볼트 암호화 데이터 유지 |
| 컨테이너 중지 및 볼트 데이터 완전 삭제 | `docker compose down -v` | 모든 사용자 및 볼트 데이터 영구 삭제 |
| 로컬 mkcert 루트 인증서 삭제 | `mkcert -uninstall` (이후 `certs` 폴더 내부 pem 파일 삭제) | 로컬 TLS 신뢰 체인 해제 |

> [!CAUTION]
> `docker compose down -v` 명령은 저장된 모든 계정과 볼트 데이터를 완전히 삭제하며 복구할 수 없습니다.
</details>

---

## 🔧 트러블 슈팅

각 플랫폼별 주요 트러블 슈팅 및 문제 해결 가이드는 아래 링크에서 확인할 수 있습니다.

- [Web / 데스크톱 클라이언트 트러블 슈팅](Apps/Web/README.md#-트러블-슈팅)
- [Apple 클라이언트 트러블 슈팅](Apps/Apple/README.md#-트러블-슈팅)
- [Backend 트러블 슈팅](Back/README.md#-트러블-슈팅)
