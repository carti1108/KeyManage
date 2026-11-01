# 2026-11-01 · 로컬 Docker Compose 데모 실행 구성

- 이슈·PR: PR #87
- 커밋: 436fdb0, 55b2ffd, 03077a9, fdc233b, 1e56e2a, 0a1e598, ac0a596, 62ec6ab, 246b1af, 4943b1b, acb93e7, edbb605

> **요약** 데모용 서버를 따로 운영하지 않고, 레포를 받은 사람이 `docker compose up`으로 자기 PC에 백엔드와 웹을 띄우게 했다. 데스크톱 앱은 각자의 로컬 HTTPS 서버(`https://localhost:8443`, mkcert 인증서)에 접속한다. 사람이 배포 방식을 데모 기준으로 고르고 mkcert 안내로 HTTPS 문제를 푸는 방향을 제시했다.

## 사용 도구

- 도구: Claude Code (Opus 5.5). 구현 서브에이전트 1개, 독립 리뷰 서브에이전트 1개, Cloudflare Tunnel 웹 조사 서브에이전트 1개
- 플러그인·스킬: adr-manager, project-docs:ai-usage

## 요청

배포 방식을 물었고, 무료 배포 방법과 받은 사람이 로컬에서 백엔드를 띄우는 방법을 비교해 달라고 했다. 받은 사람이 자기 로컬에서 쓸 수 있도록 Docker 설정과 README를 먼저 만들고, Cloudflare Tunnel 방식은 따로 알아보도록 했다.

## AI 산출물

- 배포 방식 비교: 무료 VM, 디스크를 붙이는 PaaS, 서버리스(저장소 교체 필요), 집 PC와 Cloudflare Tunnel, 로컬 실행(Docker Compose, 소스 실행, jar)
- Cloudflare Tunnel 조사: 고정 주소에는 도메인 필요, 토큰 방식 Compose 연결, TLS가 Cloudflare에서 끝나는 점, 요청 제한의 클라이언트 IP 헤더 문제
- ADR-0021: 로컬 Docker Compose 데모, 데스크톱 릴리스 주소 `https://localhost:8443`
- `docker-compose.yml`, 백엔드·Caddy Dockerfile, Caddyfile과 인증서 유무에 따른 엔트리포인트, `.dockerignore`, `certs/.gitkeep`
- 루트 README 사용법(웹만, 데스크톱 앱까지, 끄기와 데이터 지우기, 인증서 위험, 문제 해결), `Apps/Web/README.md` 수동 절차 정리

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 목적 결정 | 배포 목적을 데모로 정했다. |
| 방향 제시 | 호스팅 대신 Docker 설정을 공유해 받은 사람이 자기 로컬에서 서버를 띄우는 방식을 제안했다. |
| 수정 | 데스크톱 앱이 로컬 `http://localhost`에 붙지 못한다는 설명에, mkcert와 Caddy 사용법을 안내하면 된다고 지적해 HTTPS 규칙을 유지하는 방향이 됐다. |
| 채택 | 배포 방식은 언제든 바뀔 수 있다는 전제로 로컬 데모를 먼저 하고 Cloudflare는 따로 검토하기로 했다. ADR-0021 초안을 승인했다. |
| 확인 | Compose로 띄운 웹에서 가입·저장·잠금·새로고침이 정상인지 직접 확인했다. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `docker compose up -d --build`(인증서 없음) | 8080 웹 200, API 200, 8443 미기동 |
| 인증서 있음(스크래치 CA, 신뢰 저장소 변경 없음) | 8443 200, 데스크톱 출처 CORS 허용, 다른 출처 403 |
| 보안 경계 | 루프백 포트만 노출, 다른 Host 421, 위조한 X-Forwarded-For로 요청 제한 우회 안 됨, 백엔드 비루트·서버 비밀키 0600 |
| 보안 헤더 | 8080·8443 응답에 CSP·nosniff·Referrer-Policy, 헤드리스 Edge에서 CSP 위반 없이 첫 화면 렌더링 |
| 볼륨 보존 | 컨테이너를 다시 만들어도 서버 비밀키 유지 |
| 사람의 브라우저 확인(8080) | 가입·저장·잠금·새로고침 정상 |
| `bash Scripts/check-docs.sh` | 통과 |
| 독립 리뷰 서브에이전트 | CRITICAL·HIGH 없음. MEDIUM 3, LOW 8 중 LOW 1건(이미지 태그 고정)을 빼고 반영 |

## 검증하지 못한 것

1. Compose 구성의 8443으로 데스크톱 앱을 실제로 붙여 보지 않았다. 같은 구성(mkcert, Caddy, CORS)은 로그 049에서 수동으로 확인했다.
2. macOS와 Linux에서의 빌드·실행. macOS 데스크톱 앱은 허용 출처에 없어 지원하지 않는다고 명시했다.
3. 고정 서브넷 충돌(`Pool overlaps`)은 재현하지 않고 문제 해결 안내만 넣었다.
4. 리뷰 반영 커밋의 2차 독립 리뷰는 하지 않았다.
5. GitHub Releases에 데스크톱 앱을 올리는 일은 하지 않았다. README에 예정으로 적었다.

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| 받은 사람의 데스크톱 앱은 로컬 서버에 붙을 수 없다고 안내하며 보안 규칙 변경을 대안으로 냄 | 사람이 mkcert·Caddy 안내로 해결된다고 지적 | HTTPS 규칙을 유지하는 `https://localhost:8443` 방식으로 정함 |
| ADR-0021이 `http://localhost` 거절의 근거를 SEC-002로 잘못 적고, 8080 평문 웹과 기각 이유가 어긋남 | 독립 리뷰 | 머지 전에 근거와 위협 설명을 바로잡음 |
| ADR 목록 표에 빈 줄을 두고 행을 추가해 표 밖으로 빠짐(문서 검사는 통과) | 독립 리뷰 | 표 안으로 옮김 |
| README가 아직 없는 GitHub Releases를 안내하고, 폴더 생성 단계가 실패할 수 있었음 | 독립 리뷰 | 릴리스 예정 표시와 직접 빌드 방법, `certs/.gitkeep`으로 바꿈 |
| 웹 응답에 CSP 등 보안 헤더가 없었고 다른 Host 요청도 받았음 | 독립 리뷰 | 헤더 추가, 허용 Host 제한 |
