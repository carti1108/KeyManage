# 웹 플랫폼 바인딩

- 대상 명세: [vault-spec.md](../../../docs/vault-spec.md), [design-system.md](../../../docs/design-system.md)
- 대상 코드: `Apps/Web/src/`

> **요약** 웹·데스크톱 클라이언트가 공통 암호 규격과 디자인 시스템을 어떤 라이브러리와 파일로 구현하는지 적는다.

## 암호 라이브러리

| 규격 | 구현 |
| :--- | :--- |
| PBKDF2 | Web Crypto API (`subtle.deriveKey`) |
| AES-256-GCM | Web Crypto API (`subtle.encrypt({ name: "AES-GCM" })`) |

## 색상 토큰

1. 토큰은 `Apps/Web/src/index.css`에 shadcn/ui와 같은 이름의 CSS 변수로 둔다. `:root`가 라이트, `.dark`가 다크다. 기본은 다크(`index.html`의 `<html class="dark">`)다.
2. Tailwind v4는 설정 파일 없이 CSS의 `@theme inline`으로 변수를 유틸리티에 연결한다(`bg-card`, `text-primary` 등).
3. 컴포넌트는 `Apps/Web/src/ui/components/`에 둔다. 클래스 병합은 `clsx`와 `tailwind-merge`를 쓴다.
4. Radix는 다이얼로그처럼 필요한 컴포넌트가 생길 때 추가한다.

관련 결정: [ADR-0012](adr/ADR-0012-desktop-shell-tauri.md)

## 글꼴

| 용도 | 글꼴 |
| :--- | :--- |
| 헤더 | `Poppins` (목표) |
| 본문 | `Inter` (목표) |
| 비밀번호 | `font-mono` (JetBrains Mono / SF Mono) |

> **현재 상태** 웹 글꼴(Poppins·Inter)은 데스크톱 CSP와 외부 요청 문제로 아직 넣지 않았다. 지금은 시스템 글꼴을 쓴다. 비밀번호는 `font-mono`로 표시한다.

## 아이콘

아이콘은 `lucide-react`를 쓴다.

| 용도 | 아이콘 |
| :--- | :--- |
| 비밀번호 보기 | `<Eye />` |
| 비밀번호 숨김 | `<EyeOff />` |
| 클립보드 복사 | `<Copy />` |
| 복사 완료 | `<CheckCircle />` |
| 암호 재생성 | `<RefreshCw />` |
| 볼트 잠금 | `<Lock />` |
| 경고·취약 | `<AlertTriangle />` |
| 오프라인 배너 | `<WifiOff />` |
