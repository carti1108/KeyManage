# KeyManage

> **KeyManage**는 영지식(Zero-Knowledge) 보안 원칙에 기반하여 마스터 키 유도와 볼트 암·복호화를 오직 클라이언트에서만 수행하는 멀티 플랫폼(Web, Desktop, iOS, macOS) 종단간 암호화(E2EE) 패스워드 매니저입니다.

---

## 목차
- [주요 기능](#주요-기능)
- [아키텍처 및 폴더 구조](#아키텍처-및-폴더-구조)
- [기술 스택](#기술-스택)
- [사용법](#사용법)

---

## 주요 기능

<details>
<summary><strong>1. 영지식(Zero-Knowledge) 종단간 암·복호화 (SEC-001, vault-spec)</strong></summary>

- **클라이언트 전용 암·복호화**: 마스터 비밀번호와 암호화 키는 서버로 전송되지 않으며, 서버는 오직 암호문(Ciphertext)만 저장합니다.
- **표준 암호 알고리즘**:
  - 키 유도: `PBKDF2-HMAC-SHA256` (반복 횟수 600,000회 이상, 16바이트 솔트)
  - 도메인 키 분리: `HKDF-SHA256`을 통해 인증 해시(`authKey`)와 볼트 암호화 키(`encryptionKey`)를 엄격히 분리
  - 볼트 암호화: `AES-256-GCM` (12바이트 무작위 IV, 16바이트 인증 태그, AAD 무결성 검증)
- **교차 플랫폼 암호 벡터**: WebCrypto, CryptoKit(Apple), JDK 간 동일한 암호 테스트 벡터(`crypto-test-vectors.json`) 교차 검증 통과
</details>

<details>
<summary><strong>2. 낙관적 잠금 기반 볼트 동기화 및 다중 기기 충돌 병합 (VLT-001)</strong></summary>

- **Revision 기반 동기화**: 서버는 타임스탬프 대신 단조 증가하는 `revision` 정수로 볼트 버전을 관리합니다.
- **항목 단위 3-way 병합**: 동시 수정으로 `409 Conflict` 발생 시 서버의 최신 볼트를 조회하여 항목별 수정 시각(`updatedAt`) 기준으로 자동 병합한 뒤 재시도합니다.
- **상위 버전 읽기 전용 보호**: 클라이언트 버전보다 높은 스키마 버전의 볼트는 데이터 유실 방지를 위해 읽기 전용(`readOnly`)으로 보호됩니다.
- **안전한 삭제(Tombstone)**: 삭제된 항목은 `deletedAt` 기록을 남겨 다른 기기와의 동기화 시 되살아나는 문제를 방지하고, 민감 정보(비밀번호, 메모)는 즉시 소거합니다.
</details>

<details>
<summary><strong>3. 비밀번호 무작위 생성기 및 엔트로피 점수 진단 (GEN-001)</strong></summary>

- **클라이언트 전용 생성**: 평문 비밀번호가 네트워크를 타지 않도록 클라이언트 내부 암호학적 난수 생성기(`Crypto.getRandomValues`)로만 생성합니다.
- **표준 제약 조건**: 8~64자 길이, 4종 문자군(대문자·소문자·숫자·특수기호 26자) 선택, 거부 샘플링을 통한 편향 방지 및 전 문자군 필수 포함 보장
- **엔트로피 점수 계산 및 시각화**: Shannon 엔트로피 공식을 기반으로 0~100점 점수화 및 4단계 강도 게이지 제공
- **취약 패턴 안내**: `123`, `987` 등 3자리 이상 연속/역순 숫자가 포함된 경우 사용자 안내 문구 표기
</details>

<details>
<summary><strong>4. 보안 클립보드 복사 및 30초 자동 소각 (COM-001)</strong></summary>

- **자동 소각 타이머**: 비밀번호나 계정 정보 복사 시 30초 후 시스템 클립보드를 자동으로 비우며, 화면에 남은 시간 카운트다운을 제공합니다.
- **이벤트 즉시 소각**: 앱이 잠기거나 백그라운드로 전환될 경우 30초를 기다리지 않고 클립보드를 즉시 비웁니다.
</details>

<details>
<summary><strong>5. 오프라인 동기화 대기 및 기기 볼트 보존 (VLT-002, VLT-003)</strong></summary>

- **오프라인 수정 대기 큐**: 네트워크 단절이나 서버 일시 장애 시 수정을 로컬에 저장하고 `pendingSync` 상태로 유지하며, 재연결 시 자동으로 서버에 병합·업로드합니다.
- **암호화 기기 저장소**: 브라우저 종료나 앱 재시작 후에도 볼트를 유지할 수 있도록 클라이언트 로컬 저장소(IndexedDB / Keychain)에 암호화된 상태로 보존합니다.
</details>

<details>
<summary><strong>6. 자동 잠금 및 생체 인증 잠금 해제 (SEC-003)</strong></summary>

- **5종 유휴 타임아웃**: 즉시(0초), 1분, 5분, 15분, 1시간 유휴 상태 지속 시 자동으로 볼트 잠금
- **민감 메모리 소거**: 잠금 시 복호화된 키와 볼트 데이터를 메모리에서 즉시 `memset_s` 기반 0으로 덮어씁니다.
- **생체 인증(Touch ID/Face ID/Windows Hello)**: 편리한 잠금 해제를 지원하며, 보안을 위해 14일 주기 마스터 비밀번호 재인증 및 생체 정보 변경 시 무효화를 적용합니다.
</details>

<details>
<summary><strong>7. AES-256-GCM 암호화 백업 내보내기 및 복원 (VLT-004, ADR-0022)</strong></summary>

- **독립적인 백업 암호화**: 마스터 비밀번호와 별개의 백업 전용 비밀번호를 지정해 독립적인 `AES-256-GCM` 컨테이너(`.keymanage-backup`)로 내보냅니다.
- **안전한 복원**: 복원 시 백업 시점의 온전한 스냅샷으로 롤백 복원하며, 백업 파일 위·변조 검증을 수행합니다.
</details>

---

## 아키텍처 및 폴더 구조

KeyManage는 백엔드와 프론트엔드 전반에 걸쳐 비즈니스 도메인을 프레임워크와 완전히 격리하는 설계를 유지합니다.

<details>
<summary><strong>핵심 설계 원칙</strong></summary>

1. **영지식(Zero-Knowledge) 보안**: 마스터 키 유도 및 암·복호화는 클라이언트 내부에서만 수행하며, 서버는 암호문(Ciphertext)만 보관합니다.
2. **포트-어댑터(Hexagonal) 멀티 모듈**: 백엔드는 도메인 중심의 4개 모듈(`domain`, `api`, `infrastructure`, `app`)로 관심사를 엄격히 분리합니다.
3. **의존성 역전(DIP)**: 클라이언트 UI는 멍청한 껍데기(Dumb View)로 유지하며, 백엔드 통신 및 암호화 모듈을 추상화 인터페이스를 통해 주입받습니다.
</details>

<details>
<summary><strong>전체 시스템 흐름 다이어그램</strong></summary>

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

    subgraph Server["백엔드 서버 (Hexagonal Multi-Module)"]
        API["API Adapter (REST Controller)"]
        CoreDomain["Back Domain (Optimistic Lock)"]
        Infra["Infrastructure Adapter (File/DB)"]
        
        API --> CoreDomain
        Infra --> CoreDomain
    end

    Crypto -- "HTTPS / 암호문 전송 (E2EE)" --> API
```
</details>

<details>
<summary><strong>레포지토리 디렉터리 구조</strong></summary>

```text
KeyManage/
├── Apps/
│   ├── Apple/                 # Apple 네이티브 앱 (iOS, macOS)
│   │   ├── Projects/          # Tuist 4 마이크로 피처 모듈 (Core, Features, App)
│   │   └── docs/              # Apple 플랫폼 전용 ADR 및 명세
│   └── Web/                   # React 19 + Tauri v2 웹/데스크톱 앱
│       ├── src/               # Clean Architecture 계층화 소스코드
│       ├── src-tauri/         # Tauri v2 데스크톱 Rust 셸
│       └── docs/              # Web 플랫폼 전용 ADR 및 명세
├── Back/                      # Spring Boot 3.4 헥사고날 멀티모듈 백엔드
│   ├── domain/                # 순수 Kotlin 비즈니스 도메인 및 유스케이스
│   ├── api/                   # Spring Web MVC REST API 컨트롤러 및 필터
│   ├── infrastructure/        # 원자적 파일 볼트 저장소 어댑터
│   └── app/                   # 스프링 부트 애플리케이션 진입점 및 합성 루트
├── Docker/                    # 로컬 데모 실행용 Dockerfile 및 Caddyfile
├── Scripts/                   # 다중 플랫폼 정적 검증 및 거버넌스 스크립트
├── Tools/                     # 플랫폼 간 암호 테스트 벡터 검증기
├── docs/                      # 프로젝트 통합 문서 (정책 SSOT, 공통 ADR, 명세)
│   ├── policies/              # 서비스 정책 SSOT (SEC, VLT, GEN, COM)
│   ├── adr/                   # 시스템 공통 아키텍처 결정 기록
│   ├── ai-usage/              # AI 협업 및 검증 기록 로그
│   ├── vault-spec.md          # 볼트 암호화 및 병합 표준 명세서
│   └── api-spec.md            # 백엔드 REST API 계약 명세서
└── AGENTS.md                  # 범용 AI 에이전트 개발 가이드라인 (CLAUDE.md 호환)
```
</details>

---

## 기술 스택

<details>
<summary><strong>플랫폼별 기술 스택 요약</strong></summary>

| 영역 | 기술 스택 | 설명 |
| :--- | :--- | :--- |
| **Backend** | Kotlin 2.1, Spring Boot 3.4, Gradle | 4개 모듈 분리 헥사고날 아키텍처, PIT 뮤테이션 테스트 |
| **Web Client** | React 19, TypeScript 5.8, Vite 6 | Tailwind CSS v4, WebCrypto API, Vitest |
| **Desktop Shell**| Tauri v2, Rust | 경량 네이티브 웹뷰 래퍼, 시스템 트레이 |
| **Apple Client** | Swift 6, Tuist 4, Uber microRIBs | Strict Concurrency, CryptoKit, Swift Testing, LocalAuthentication |
| **Infra & Demo** | Docker Compose, Caddy 2, mkcert | 로컬 리버스 프록시, 자동 HTTPS 환경 구성 |
| **Verification** | Python 3, Bash, GitHub Actions | 5중 기계적 정적 게이트, 커밋 최신성 및 PR Rebase 강제 훅 |
</details>

---

## 사용법

> **요약** 로컬 PC에서 Docker Compose로 백엔드와 웹을 간편하게 실행할 수 있습니다. 웹만 사용하는 경우 Docker만 있으면 되며, 데스크톱 앱까지 연동할 경우 `mkcert`를 통해 로컬 HTTPS 환경을 구성합니다. 자세한 결정 배경은 [ADR-0021](docs/adr/ADR-0021-local-docker-compose-demo.md)에 기록되어 있습니다.

| 사용 환경 | 접속 주소 | 필수 준비물 |
| :--- | :--- | :--- |
| 웹 브라우저 단독 실행 | http://localhost:8080 | Docker Desktop |
| 웹 + 데스크톱 앱 (Windows) 연동 | https://localhost:8443 | Docker Desktop, mkcert |

<details>
<summary><strong>1. 웹 브라우저로만 사용하기 (포트 8080)</strong></summary>

1. [Docker Desktop](https://www.docker.com/products/docker-desktop/)을 설치하고 실행합니다.
2. 레포지토리 루트에서 컨테이너를 빌드하고 실행합니다:
   ```bash
   docker compose up -d --build
   ```
3. 빌드가 완료되면 웹 브라우저에서 [http://localhost:8080](http://localhost:8080)으로 접속합니다.
</details>

<details>
<summary><strong>2. 데스크톱 앱까지 함께 사용하기 (HTTPS 포트 8443, Windows)</strong></summary>

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
> **데스크톱 앱 빌드 방법 (직접 빌드 시)**:
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
<summary><strong>3. 신규 계정 가입 차단 설정</strong></summary>

관리자 또는 개인 계정을 생성한 후 추가 가입을 막으려면, 레포지토리 루트의 `.env` 파일에 다음 환경변수를 설정하고 다시 실행합니다:
```bash
KEYMANAGE_REGISTRATION_ENABLED=false
```
적용:
```bash
docker compose up -d
```
</details>

<details>
<summary><strong>4. 컨테이너 종료 및 데이터 초기화</strong></summary>

| 작업 내용 | 실행 명령 |
| :--- | :--- |
| 컨테이너 중지 (볼트 데이터 보존) | `docker compose down` |
| 컨테이너 중지 및 볼트 데이터 완전 삭제 | `docker compose down -v` |
| 로컬 mkcert 루트 인증서 삭제 | `mkcert -uninstall` (이후 `certs` 폴더 내부 pem 파일 삭제) |

> [!CAUTION]
> `docker compose down -v` 명령은 저장된 모든 계정과 볼트 데이터를 완전히 삭제하며 복구할 수 없습니다.
</details>
