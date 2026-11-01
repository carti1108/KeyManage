# KeyManage

## 목차
- [실행화면](#실행화면)
- [주요기능](#주요기능)
- [아키텍처 및 폴더 구조](#아키텍처-및-폴더-구조)
- [기술 스택](#기술-스택)
- [사용법](#사용법)
- [트러블 슈팅](#트러블-슈팅)

## 실행화면

## 주요기능

## 아키텍처 및 폴더 구조

## 기술 스택

## 사용법

> **요약** 내 PC에서 Docker Compose로 백엔드와 웹을 띄운다. 웹만 쓰면 Docker만 있으면 된다. 데스크톱 앱까지 쓰면 mkcert로 HTTPS 인증서를 더 만든다. 이 방식을 고른 이유는 [ADR-0021](docs/adr/ADR-0021-local-docker-compose-demo.md)에 있다.

| 쓰는 것 | 주소 | 필요한 것 |
| :--- | :--- | :--- |
| 웹 | http://localhost:8080 | Docker Desktop |
| 웹, 데스크톱 앱(Windows) | https://localhost:8443 | Docker Desktop, mkcert |

두 주소는 이 PC에서만 열린다. 같은 네트워크의 다른 기기는 접속할 수 없다. 주소는 표의 `localhost` 주소로 쓴다. 서버는 다른 이름으로 온 요청을 거절한다.

### 웹만 쓰기

1. [Docker Desktop](https://www.docker.com/products/docker-desktop/)을 설치하고 켠다.
2. 레포 루트에서 실행한다. 첫 실행은 이미지를 빌드하므로 몇 분 걸린다.

   ```bash
   docker compose up -d --build
   ```

3. 브라우저에서 http://localhost:8080 을 연다.

### 데스크톱 앱까지 쓰기

데스크톱 앱은 `https://localhost:8443`에만 접속한다. 이 주소에는 이 PC가 신뢰하는 인증서가 필요하다. 데스크톱 앱은 지금 Windows에서만 쓸 수 있다. 서버가 허용하는 앱 출처가 Windows 앱의 `http://tauri.localhost`뿐이기 때문이다.

> **주의** `mkcert -install`은 이 PC에 루트 인증서를 등록한다. `certs/localhost-key.pem`이나 mkcert 루트 키가 새면, 다른 사람이 이 PC에서 믿는 가짜 HTTPS 사이트를 만들 수 있다. 다 쓰면 아래 "끄기와 지우기"대로 지운다.

1. mkcert를 설치한다.

   | OS | 명령 |
   | :--- | :--- |
   | Windows | `winget install FiloSottile.mkcert` |
   | macOS(웹 8443만) | `brew install mkcert` |

2. 루트 인증서를 등록한다: `mkcert -install`
3. 레포 루트의 `certs` 폴더(이미 있다) 안에서 인증서를 만든다.

   ```bash
   cd certs
   mkcert -cert-file localhost.pem -key-file localhost-key.pem localhost
   cd ..
   ```

4. 실행한다: `docker compose up -d --build`. 이미 떠 있으면 `docker compose restart caddy`로 인증서를 읽게 한다.
5. 브라우저에서 https://localhost:8443 을 연다.
6. 데스크톱 앱을 실행한다. 앱의 서버 주소는 `https://localhost:8443`으로 고정되어 있다.
   - GitHub Releases 배포는 예정이다. 아직 올린 릴리스가 없다.
   - 지금은 직접 빌드한다. Node 22.18 이상, Rust, Visual Studio C++ 빌드 도구가 필요하다. PowerShell에서 `Apps/Web` 폴더로 가서 실행한다.

     ```powershell
     npm ci
     $env:VITE_API_BASE_URL = 'https://localhost:8443'
     npm run tauri:build
     ```

     설치 파일(NSIS)은 `Apps/Web/src-tauri/target/release/bundle/nsis/`에 생긴다.

`certs` 폴더의 인증서 파일은 git에 올라가지 않는다. 인증서 파일이 없으면 8080만 뜬다. 둘 중 하나만 있으면 `caddy`가 재시작을 반복한다. `docker compose logs caddy`로 이유를 확인한다.

### 새 가입 막기

내 계정을 만든 뒤에는 새 가입을 막을 수 있다. 레포 루트의 `.env` 파일에 아래 줄을 넣고 `docker compose up -d`로 다시 띄운다.

```bash
KEYMANAGE_REGISTRATION_ENABLED=false
```

### 끄기와 지우기

| 할 일 | 명령 |
| :--- | :--- |
| 끄기(데이터는 남는다) | `docker compose down` |
| 끄고 데이터까지 지우기 | `docker compose down -v` |
| 루트 인증서 지우기 | `mkcert -uninstall`. 그다음 `certs` 폴더를 지운다. |

> **주의** `docker compose down -v`는 계정과 볼트를 모두 지운다. 되돌릴 수 없다.

끄지 않으면 Docker Desktop이 켜질 때마다 데모도 다시 뜬다.

### 문제 해결

| 증상 | 원인과 해결 |
| :--- | :--- |
| `docker compose up`이 `port is already allocated`로 실패한다 | 다른 프로그램이 8080이나 8443 포트를 쓴다. 그 프로그램을 끄고 다시 실행한다. |
| 데스크톱 앱이 서버에 접속하지 못한다 | 8443이 떠 있지 않다. `certs`의 인증서 파일 두 개와 `docker compose ps`를 확인한다. 다른 프로그램이 8443을 쓰면 데스크톱 앱은 쓸 수 없다. |
| 브라우저가 8443에서 인증서 경고를 띄운다 | `mkcert -install`을 하지 않았거나, 인증서를 만든 뒤 `-uninstall`했다. |
| `docker compose up`이 `Pool overlaps with other one on this address space`로 실패한다 | 다른 Docker 네트워크나 VPN이 `172.29.83.0/24`를 쓴다. `docker-compose.yml`의 서브넷과 caddy IP, `KEYMANAGE_HTTP_TRUSTED_PROXIES`를 같은 새 대역으로 바꾼다. |
| 페이지가 `Misdirected Request`(421)를 띄운다 | `localhost`가 아닌 주소로 접속했다. `http://localhost:8080`이나 `https://localhost:8443`으로 접속한다. |

## 트러블 슈팅
