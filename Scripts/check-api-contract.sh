#!/usr/bin/env bash
# API 명세(docs/api-spec.md) ↔ 백엔드·클라이언트 코드 계약 드리프트 정적 감사 (API Contract Drift Gate)
#
# docs/api-spec.md에 정의된 REST 엔드포인트(메서드·경로) 및 표준 오류 코드·메시지를 추출하여
# Back/api 및 클라이언트(Apps/Web, 그리고 HTTP 어댑터가 구현된 Apps/Apple·Apps/Android)와
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
web_src_dir = os.path.join(root, "Apps", "Web", "src")
if os.path.isdir(web_src_dir):
    # 4-1. SERVER_ERROR_CODES 대조
    server_errors_path = os.path.join(web_src_dir, "domain", "server", "serverErrors.ts")
    if not os.path.isfile(server_errors_path):
        violations.append("[Apps/Web] src/domain/server/serverErrors.ts 파일을 찾을 수 없습니다.")
    else:
        with open(server_errors_path, encoding="utf-8") as f:
            se_text = f.read()
        block_m = re.search(r"SERVER_ERROR_CODES\s*=\s*\[(.*?)\]\s*as\s*const", se_text, re.S)
        web_codes = set(re.findall(r"'([A-Z][A-Z0-9_]+)'", block_m.group(1))) if block_m else set()

        missing_in_web = sorted(spec_error_codes - web_codes)
        extra_in_web = sorted(web_codes - spec_error_codes)
        if missing_in_web:
            violations.append(f"[Apps/Web] docs/api-spec.md에 정의되었으나 SERVER_ERROR_CODES에 누락된 오류 코드: {', '.join(missing_in_web)}")
        if extra_in_web:
            violations.append(f"[Apps/Web] SERVER_ERROR_CODES에만 존재하고 docs/api-spec.md에 없는 오류 코드: {', '.join(extra_in_web)}")

    # 4-2. HTTP 어댑터 엔드포인트 대조
    http_dir = os.path.join(web_src_dir, "adapters", "http")
    if os.path.isdir(http_dir):
        web_endpoints = set()
        for tpath in glob.glob(os.path.join(http_dir, "*.ts")):
            if tpath.endswith(".test.ts"):
                continue
            with open(tpath, encoding="utf-8") as f:
                ttext = f.read()
            for m in re.finditer(r"client\.request\(\s*'(GET|POST|PUT|DELETE|PATCH)'\s*,\s*'(/[^']*)'", ttext):
                sub_path = m.group(2)
                full_path = sub_path if sub_path.startswith("/api/v1") else f"/api/v1{sub_path}"
                web_endpoints.add((m.group(1), full_path))

        missing_ep_web = sorted(spec_endpoints - web_endpoints)
        extra_ep_web = sorted(web_endpoints - spec_endpoints)
        if missing_ep_web:
            violations.append(f"[Apps/Web] docs/api-spec.md에 정의되었으나 HTTP 어댑터에 누락된 엔드포인트: {missing_ep_web}")
        if extra_ep_web:
            violations.append(f"[Apps/Web] HTTP 어댑터에만 존재하고 docs/api-spec.md에 없는 엔드포인트: {extra_ep_web}")

if violations:
    print("API 명세(docs/api-spec.md) 계약 드리프트 위반 발견:")
    for v in violations:
        print(f"  ✖ {v}")
    sys.exit(1)

print(
    f"✔ API 계약 정합성 감사 통과 (엔드포인트 {len(spec_endpoints)}종, 표준 오류 코드 {len(spec_error_codes)}종 — Back/api 및 Apps/Web 일치)"
)
PY
