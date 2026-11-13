#!/usr/bin/env bash
# API 명세(docs/api-spec.md) ↔ 백엔드·클라이언트 코드 계약 드리프트 정적 감사 (API Contract Drift Gate)
#
# docs/api-spec.md에 정의된 REST 엔드포인트(메서드·경로) 및 표준 오류 코드·메시지를 추출하여
# Back/api 및 클라이언트(Apps/Web이 쓰는 공유 Kotlin Apps/Shared/server-http, 그리고 HTTP 어댑터가 구현된 Apps/Apple·Apps/Android)와
# 불일치(Drift)가 없는지 0초 만에 기계적으로 대조한다.

set -euo pipefail

ROOT_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SPEC_FILE="${ROOT_DIR}/docs/api-spec.md"

if [ ! -f "${SPEC_FILE}" ]; then
  echo "오류: ${SPEC_FILE} 파일을 찾을 수 없습니다." >&2
  exit 2
fi

PYTHON=""
for candidate in python3 python; do
  if command -v "$candidate" >/dev/null 2>&1 \
    && "$candidate" -c 'import sys; sys.exit(0 if sys.version_info[0] >= 3 else 1)' >/dev/null 2>&1; then
    PYTHON="$candidate"
    break
  fi
done

if [ -z "$PYTHON" ]; then
  echo "오류: 실행 가능한 python3를 찾을 수 없습니다." >&2
  exit 2
fi

PYTHONIOENCODING=utf-8 "$PYTHON" - "$ROOT_DIR" <<'PY'
import glob
import os
import re
import sys

for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

root = sys.argv[1]
spec_path = os.path.join(root, "docs", "api-spec.md")

with open(spec_path, encoding="utf-8") as f:
    spec_text = f.read()

violations = []

# 1. docs/api-spec.md에서 엔드포인트(메서드, 경로) 추출
spec_endpoints = set()
for line in spec_text.splitlines():
    m = re.match(r"^\|\s*`(/api/v1/[^`]+)`\s*\|\s*(GET|POST|PUT|DELETE|PATCH)\s*\|", line)
    if m:
        spec_endpoints.add((m.group(2), m.group(1)))

if not spec_endpoints:
    print("오류: docs/api-spec.md에서 엔드포인트 목록 표를 파싱할 수 없습니다.", file=sys.stderr)
    sys.exit(2)

# 2. docs/api-spec.md에서 표준 오류 코드 및 메시지 추출
spec_error_codes = set()
spec_error_messages = {}

for line in spec_text.splitlines():
    # 오류 표 행: | 400 | `INVALID_PARAMETER` | 메시지 | 조건 |
    m_row = re.match(r"^\|\s*(\d{3})\s*\|\s*`([A-Z][A-Z0-9_]+)`\s*\|\s*([^|]+?)\s*\|", line)
    if m_row:
        code = m_row.group(2)
        msg = m_row.group(3).strip()
        spec_error_codes.add(code)
        if not msg.startswith("("):
            spec_error_messages[code] = msg
    # 본문 내 `403 ORIGIN_NOT_ALLOWED` / "메시지" 형식
    m_inline = re.search(r"`\d{3}\s+([A-Z][A-Z0-9_]+)`\s*/\s*\"([^\"]+)\"", line)
    if m_inline:
        code = m_inline.group(1)
        spec_error_codes.add(code)
        spec_error_messages[code] = m_inline.group(2).strip()

if not spec_error_codes:
    print("오류: docs/api-spec.md에서 표준 오류 코드를 파싱할 수 없습니다.", file=sys.stderr)
    sys.exit(2)

# 3. Back/api 계약 대조
back_api_dir = os.path.join(root, "Back", "api", "src", "main", "kotlin")
if os.path.isdir(back_api_dir):
    # 3-1. ApiError.kt 오류 코드 및 한국어 메시지 대조
    api_error_files = glob.glob(os.path.join(back_api_dir, "**", "ApiError.kt"), recursive=True)
    if not api_error_files:
        violations.append("[Back/api] ApiError.kt 파일을 찾을 수 없습니다.")
    else:
        with open(api_error_files[0], encoding="utf-8") as f:
            api_error_text = f.read()
        back_errors = {}
        for m in re.finditer(r"^\s*([A-Z][A-Z0-9_]+)\(\s*HttpStatus\.[A-Z_]+\s*,\s*\"([^\"]+)\"\s*\)", api_error_text, re.M):
            back_errors[m.group(1)] = m.group(2)

        missing_in_back = sorted(spec_error_codes - set(back_errors.keys()))
        extra_in_back = sorted(set(back_errors.keys()) - spec_error_codes)
        if missing_in_back:
            violations.append(f"[Back/api] docs/api-spec.md에 정의되었으나 ApiError.kt에 누락된 오류 코드: {', '.join(missing_in_back)}")
        if extra_in_back:
            violations.append(f"[Back/api] ApiError.kt에만 존재하고 docs/api-spec.md에 문서화되지 않은 오류 코드: {', '.join(extra_in_back)}")

        for code, expected_msg in sorted(spec_error_messages.items()):
            if code in back_errors and back_errors[code] != expected_msg:
                violations.append(
                    f"[Back/api] 오류 코드 '{code}' 메시지 불일치: api-spec='{expected_msg}' ↔ ApiError.kt='{back_errors[code]}'"
                )

    # 3-2. Controller 엔드포인트 대조
    controller_files = glob.glob(os.path.join(back_api_dir, "**", "*Controller.kt"), recursive=True)
    back_endpoints = set()
    for cpath in controller_files:
        with open(cpath, encoding="utf-8") as f:
            ctext = f.read()
        base_m = re.search(r"@RequestMapping\(\s*\"([^\"]+)\"\s*\)", ctext)
        base_path = base_m.group(1).rstrip("/") if base_m else ""
        for m in re.finditer(r"@(Get|Post|Put|Delete|Patch)Mapping(?:\(\s*\"([^\"]*)\"\s*\))?", ctext):
            method = m.group(1).upper()
            sub = m.group(2) or ""
            full_path = f"{base_path}/{sub.lstrip('/')}" if sub else base_path
            back_endpoints.add((method, full_path))

    missing_ep_back = sorted(spec_endpoints - back_endpoints)
    extra_ep_back = sorted(back_endpoints - spec_endpoints)
    if missing_ep_back:
        violations.append(f"[Back/api] docs/api-spec.md에 정의되었으나 Controller에 누락된 엔드포인트: {missing_ep_back}")
    if extra_ep_back:
        violations.append(f"[Back/api] Controller에만 존재하고 docs/api-spec.md에 없는 엔드포인트: {extra_ep_back}")

# 4. Apps/Web 계약 대조
# 웹이 실제로 쓰는 서버 통신 구현은 공유 Kotlin(Apps/Shared/server-http의 HttpAdapters.kt, domain의 ServerErrors.kt)이다.
# 옛 TS 구현(Apps/Web/src/adapters/http, domain/server/serverErrors.ts)이 남아 있으면 그쪽도 같은 기준으로 대조한다.
web_src_dir = os.path.join(root, "Apps", "Web", "src")
shared_dir = os.path.join(root, "Apps", "Shared")
if os.path.isdir(web_src_dir) or os.path.isdir(shared_dir):
    # 4-1. 오류 코드 대조 (Kotlin ServerErrorCode enum, 옛 TS SERVER_ERROR_CODES)
    web_code_sources = []  # (표시 이름, 코드 집합)
    kt_error_files = glob.glob(
        os.path.join(shared_dir, "domain", "src", "commonMain", "**", "ServerErrors.kt"), recursive=True
    )
    for kpath in kt_error_files:
        with open(kpath, encoding="utf-8") as f:
            kt_text = f.read()
        enum_m = re.search(r"enum\s+class\s+ServerErrorCode\s*\{(.*?)\bcompanion\b", kt_text, re.S)
        # 정의에서 // 주석을 걷어내고 대문자 상수명만 모은다. UNKNOWN은 서버 코드가 아닌 클라이언트 대체값이다
        enum_body = re.sub(r"//[^\n]*", "", enum_m.group(1)) if enum_m else ""
        kt_codes = set(re.findall(r"\b([A-Z][A-Z0-9_]+)\b", enum_body)) - {"UNKNOWN"}
        if not kt_codes:
            violations.append("[Apps/Shared] ServerErrors.kt에서 ServerErrorCode enum을 파싱할 수 없습니다.")
        web_code_sources.append(("ServerErrorCode(Kotlin)", kt_codes))

    server_errors_path = os.path.join(web_src_dir, "domain", "server", "serverErrors.ts")
    if os.path.isfile(server_errors_path):
        with open(server_errors_path, encoding="utf-8") as f:
            se_text = f.read()
        block_m = re.search(r"SERVER_ERROR_CODES\s*=\s*\[(.*?)\]\s*as\s*const", se_text, re.S)
        web_code_sources.append(
            ("SERVER_ERROR_CODES(TS)", set(re.findall(r"'([A-Z][A-Z0-9_]+)'", block_m.group(1))) if block_m else set())
        )

    if not web_code_sources:
        violations.append(
            "[Apps/Web] 서버 오류 코드 정의(Apps/Shared/domain ServerErrors.kt 또는 src/domain/server/serverErrors.ts)를 찾을 수 없습니다."
        )
    for source_name, web_codes in web_code_sources:
        missing_in_web = sorted(spec_error_codes - web_codes)
        extra_in_web = sorted(web_codes - spec_error_codes)
        if missing_in_web:
            violations.append(f"[Apps/Web] docs/api-spec.md에 정의되었으나 {source_name}에 누락된 오류 코드: {', '.join(missing_in_web)}")
        if extra_in_web:
            violations.append(f"[Apps/Web] {source_name}에만 존재하고 docs/api-spec.md에 없는 오류 코드: {', '.join(extra_in_web)}")

    # 4-2. HTTP 어댑터 엔드포인트 대조 (Kotlin: client.request(HttpMethod.Post, "/auth/login" ...), 옛 TS: client.request('POST', '/auth/login' ...))
    web_endpoint_sources = []  # (표시 이름, 엔드포인트 집합)

    def to_full_path(sub_path):
        return sub_path if sub_path.startswith("/api/v1") else f"/api/v1{sub_path}"

    kt_http_files = [
        p
        for p in glob.glob(os.path.join(shared_dir, "server-http", "src", "commonMain", "**", "*.kt"), recursive=True)
        if os.path.isfile(p)
    ]
    if kt_http_files:
        kt_endpoints = set()
        for kpath in kt_http_files:
            with open(kpath, encoding="utf-8") as f:
                ktext = f.read()
            for m in re.finditer(r'client\.request\(\s*HttpMethod\.(Get|Post|Put|Delete|Patch)\s*,\s*"(/[^"]*)"', ktext):
                kt_endpoints.add((m.group(1).upper(), to_full_path(m.group(2))))
        web_endpoint_sources.append(("Apps/Shared/server-http HTTP 어댑터", kt_endpoints))

    http_dir = os.path.join(web_src_dir, "adapters", "http")
    ts_http_files = [p for p in glob.glob(os.path.join(http_dir, "*.ts")) if not p.endswith(".test.ts")]
    ts_endpoints = set()
    for tpath in ts_http_files:
        with open(tpath, encoding="utf-8") as f:
            ttext = f.read()
        for m in re.finditer(r"client\.request\(\s*'(GET|POST|PUT|DELETE|PATCH)'\s*,\s*'(/[^']*)'", ttext):
            ts_endpoints.add((m.group(1), to_full_path(m.group(2))))
    # 옛 TS 어댑터는 엔드포인트가 남아 있을 때만 대조한다 (Kotlin으로 옮긴 뒤 지워도 감사가 깨지지 않게)
    if ts_endpoints:
        web_endpoint_sources.append(("Apps/Web HTTP 어댑터(TS)", ts_endpoints))

    if not web_endpoint_sources:
        violations.append("[Apps/Web] HTTP 어댑터(Apps/Shared/server-http HttpAdapters.kt)를 찾을 수 없습니다.")
    for source_name, web_endpoints in web_endpoint_sources:
        missing_ep_web = sorted(spec_endpoints - web_endpoints)
        extra_ep_web = sorted(web_endpoints - spec_endpoints)
        if missing_ep_web:
            violations.append(f"[Apps/Web] docs/api-spec.md에 정의되었으나 {source_name}에 누락된 엔드포인트: {missing_ep_web}")
        if extra_ep_web:
            violations.append(f"[Apps/Web] {source_name}에만 존재하고 docs/api-spec.md에 없는 엔드포인트: {extra_ep_web}")

if violations:
    print("API 명세(docs/api-spec.md) 계약 드리프트 위반 발견:")
    for v in violations:
        print(f"  ✖ {v}")
    sys.exit(1)

print(
    f"✔ API 계약 정합성 감사 통과 (엔드포인트 {len(spec_endpoints)}종, 표준 오류 코드 {len(spec_error_codes)}종 — Back/api 및 Apps/Web 일치)"
)
PY
