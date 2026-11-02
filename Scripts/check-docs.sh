#!/bin/bash
# 문서 검사 (project-docs 플러그인)
#
#   1. 문서의 상대 링크가 실제 파일이나 폴더를 가리킨다.
#   2. 정책·ADR·AI 로그 목록 표가 실제 파일(번호, 제목, 상태, 날짜)과 일치한다.
#   3. 정책 번호(SUB-001 등)와 ADR 번호(ADR-0001 등) 참조가 실제로 있는 번호를 가리킨다.
#      아직 없는 정책 번호는 <문서 폴더>/policies/open-questions.md에 예정으로 적혀 있을 때만 허용한다.
#      "CAT-001부터 발급한다" 같은 번호 발급 안내문("부터 발급"이 들어간 줄)은 참조로 보지 않는다.
#
# 플랫폼 문서 폴더: 루트에서 두 단계 아래까지의 docs/(예: Apps/Web/docs/, Back/docs/)도 링크·번호 검사 대상이다.
#   그 폴더에 adr/README.md가 있으면 그 폴더의 ADR 목록 표도 검사한다.
#   ADR 번호는 공통(ADR-0001)과 플랫폼 접두어(ADR-WEB-001 등)를 모두 인식하며, 모든 ADR 폴더에서 겹치면 안 된다.
#
# 문서 폴더는 docs/ 또는 Docs/ 중 있는 것을 쓴다. adr/, policies/, ai-usage/는 있는 것만 검사한다.
# 정책 접두어는 policies/README.md의 "영역과 접두어" 표와 실제 정책 파일 이름에서 읽는다(코드에 고정하지 않는다).
#
# 검사 대상: README.md, CLAUDE.md, AGENTS.md, AGENT.md, 문서 폴더 아래 모든 .md. 단, 과거 기록인
# ai-usage/log/NNN-*.md 본문은 옛 경로와 번호가 그대로 남아 있으므로 링크·번호 검사에서 제외한다(목록 표 검사에는 쓰인다).
#
# 목록 표의 열: 정책 번호·제목·상태[·영역], ADR 번호·제목·상태·날짜. ADR·정책 파일 이름의 영문 슬러그는 있어도 없어도 된다.
#
# 종료 코드: 0 통과, 1 위반, 2 검사 불가(문서 폴더나 검사할 구성 요소가 없음, 또는 실행 가능한 python이 없음)
# 사용법: Scripts/check-docs.sh [루트 경로]

set -u

ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)}"

# 이름만 있고 실행되지 않는 python3(Windows Store 스텁)를 거르려고 실제로 한 번 실행해 본다. python3 → python 순.
PYTHON=""
for candidate in python3 python; do
    if command -v "$candidate" >/dev/null 2>&1 \
        && "$candidate" -c 'import sys; sys.exit(0 if sys.version_info[0] >= 3 else 1)' >/dev/null 2>&1; then
        PYTHON="$candidate"
        break
    fi
done
if [ -z "$PYTHON" ]; then
    echo "오류: 실행 가능한 python3(또는 python)를 찾을 수 없어 문서를 검사할 수 없음"
    exit 2
fi

PYTHONIOENCODING=utf-8 "$PYTHON" - "$ROOT" <<'PY'
import glob, os, re, sys
from urllib.parse import unquote

# 콘솔 코드페이지(cp949 등)와 상관없이 UTF-8로 출력한다.
for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

root = sys.argv[1]
violations = []

def report(message):
    violations.append(message)

def rel(path):
    return os.path.relpath(path, root).replace(os.sep, "/")

def read(path):
    with open(path, encoding="utf-8-sig") as f:
        return f.read()

def field(lines, label):
    """'- 라벨: 값' 줄의 값. 줄이 없거나 비어 있으면 빈 문자열."""
    pattern = re.compile(r"^- " + re.escape(label) + r":[ \t]*(.*)$")
    return next((m.group(1).strip() for m in map(pattern.match, lines) if m), "")

def normalize_status(text):
    """템플릿에서 복사된 HTML 주석을 떼고 공백을 정리한다."""
    return " ".join(re.sub(r"<!--.*?-->", "", text).split())

def same_status(file_status, table_status):
    """ADR·정책 공통: 정리한 값이 같거나 첫 단어가 같으면 일치로 본다('대체됨 (→ ADR-0002)'의 '대체됨' 등)."""
    a, b = normalize_status(file_status).split(), normalize_status(table_status).split()
    return a == b or (bool(a) and bool(b) and a[0] == b[0])

# 문서 폴더: 디스크에 적힌 이름 그대로 쓴다(대소문자를 구분하지 않는 파일 시스템에서도 메시지 경로가 일관되도록).
docs = next((d for d in ("docs", "Docs") if d in os.listdir(root)), None)
if docs is None:
    print("오류: 문서 폴더(docs/ 또는 Docs/)를 찾을 수 없음")
    sys.exit(2)

def path_of(*parts):
    return os.path.join(root, docs, *parts)

has_policies = os.path.isfile(path_of("policies", "README.md"))
has_adr = os.path.isfile(path_of("adr", "README.md"))
has_log = os.path.isfile(path_of("ai-usage", "log", "README.md"))

# 검사할 구성 요소가 하나도 없으면 위반이 없어서가 아니라 검사를 못 한 것이므로 실패시킨다(거짓 통과 방지).
if not (has_policies or has_adr or has_log):
    print(f"오류: 검사할 구성 요소가 없음({docs}/policies/README.md, {docs}/adr/README.md, {docs}/ai-usage/log/README.md 모두 없음)")
    sys.exit(2)

# 폴더는 있는데 목록 표의 README만 없는 경우도 검사를 못 한 것이다.
for folder, ok, readme in (
    ("policies", has_policies, "policies/README.md"),
    ("adr", has_adr, "adr/README.md"),
    (os.path.join("ai-usage", "log"), has_log, "ai-usage/log/README.md"),
):
    if os.path.isdir(path_of(folder)) and not ok:
        print(f"오류: {docs}/{folder}/ 폴더는 있는데 {docs}/{readme}가 없어 검사할 수 없음")
        sys.exit(2)

LOG_ENTRY = re.compile(r".*[\\/]" + re.escape(docs) + r"[\\/]ai-usage[\\/]log[\\/]\d{3}-[^\\/]+\.md$")
scope = [os.path.join(root, f) for f in ("README.md", "CLAUDE.md", "AGENTS.md", "AGENT.md") if os.path.isfile(os.path.join(root, f))]
scope += sorted(glob.glob(os.path.join(root, docs, "**", "*.md"), recursive=True))
# 플랫폼 문서 폴더(루트에서 두 단계 아래까지의 docs/, 예: Apps/Web/docs/, Back/docs/). 빌드 산출물·의존성 폴더는 건너뛴다.
SKIP_DIRS = {"node_modules", ".git", "build", "dist", "target", ".gradle", "Derived", "DerivedData"}
platform_docs = sorted(
    d for pattern in ("*/docs", "*/*/docs")
    for d in glob.glob(os.path.join(root, pattern))
    if os.path.isdir(d)
    # 폴더 이름이 정확히 docs일 때만(대소문자를 구분하지 않는 Windows에서도 Linux CI와 같은 결과를 내도록)
    and "docs" in os.listdir(os.path.dirname(d))
    and not (set(os.path.relpath(d, root).replace(os.sep, "/").split("/")) & SKIP_DIRS)
    # 루트 문서 폴더와 그 아래는 이미 검사 대상이다
    and os.path.relpath(d, root).replace(os.sep, "/").split("/")[0] != docs
)
for d in platform_docs:
    scope += sorted(glob.glob(os.path.join(d, "**", "*.md"), recursive=True))
scope = [p for p in scope if not LOG_ENTRY.match(p)]

def without_fences(text):
    out, in_fence = [], False
    for line in text.split("\n"):
        if re.match(r"^\s*(```|~~~)", line):
            in_fence = not in_fence
            out.append("")
        else:
            out.append("" if in_fence else line)
    return out

# ---------- 1. 상대 링크
LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
for path in scope:
    for number, line in enumerate(without_fences(read(path)), start=1):
        line = re.sub(r"`[^`]*`", "", line)
        for target in LINK.findall(line):
            if re.match(r"^[A-Za-z][A-Za-z0-9+.-]*:", target) or target.startswith("#"):
                continue
            resolved = os.path.normpath(os.path.join(os.path.dirname(path), unquote(target.split("#")[0])))
            if not os.path.exists(resolved):
                report(f"깨진 링크: {rel(path)}:{number} → {target}")

# ---------- 2. 목록 표
def section(text, heading):
    match = re.search(r"^## " + re.escape(heading) + r"\n(.*?)(?=^## |\Z)", text, re.S | re.M)
    return match.group(1) if match else None

def check_table(label, readme, heading, row_pattern, actual):
    """표의 행(키: 번호)과 실제 파일에서 읽은 값(actual)을 비교한다. readme는 문서 폴더 기준 경로 조각이거나 파일 경로다."""
    readme_path = readme if isinstance(readme, str) else path_of(*readme)
    text = section(read(readme_path), heading)
    shown = rel(readme_path)
    if text is None:
        report(f"{label} 목록 표를 찾을 수 없음: {shown}의 '## {heading}'")
        return
    rows = {}
    for line in text.split("\n"):
        if line.startswith("| ") and not line.startswith("| 번호") and not re.match(r"^\|\s*:?-{3,}", line):
            m = row_pattern.match(line)
            if m is None:
                report(f"{label} 목록 표의 행 형식이 올바르지 않음: {line[:60]}")
            else:
                rows[m.group(1)] = m.groups()
    for key in sorted(set(actual) - set(rows)):
        report(f"{label} 목록 표에 없는 항목: {key}")
    for key in sorted(set(rows) - set(actual)):
        report(f"{label} 목록 표에만 있고 파일이 없는 항목: {key}")
    for key in sorted(set(rows) & set(actual)):
        for name, expected, got in zip(actual[key]["fields"], actual[key]["values"], rows[key][1:]):
            if not (same_status(expected, got) if name == "상태" else expected == got):
                report(f"{label} 목록 표 불일치({key} {name}): 파일 '{expected}' ↔ 표 '{got}'")

POLICY_FILE = re.compile(r"^[A-Z]{2,4}-\d{3}(-.+)?\.md$")
policies = {}
prefixes = set()
if has_policies:
    policies_dir = path_of("policies")
    policy_files = sorted(
        os.path.join(folder, name)
        for folder, _, names in os.walk(policies_dir)
        for name in names
        if POLICY_FILE.match(name) and os.path.dirname(os.path.join(folder, name)) != policies_dir
    )
    for path in policy_files:
        lines = read(path).split("\n")
        head = re.match(r"# ([A-Z]{2,4}-\d{3}) (.+)$", lines[0])
        if head is None:
            report(f"정책 파일의 첫 줄이 '# 번호 제목' 형식이 아님: {rel(path)}")
            continue
        status = field(lines, "상태")
        if not re.match(re.escape(head.group(1)) + r"(-|\.md$)", os.path.basename(path)):
            report(f"정책 파일명과 번호가 다름: {rel(path)}")
        link = os.path.relpath(path, policies_dir).replace(os.sep, "/")
        policies[head.group(1)] = {"fields": ["링크", "제목", "상태"], "values": [link, head.group(2), status]}
        prefixes.add(head.group(1).split("-")[0])
    check_table("정책", ("policies", "README.md"), "정책 목록",
                re.compile(r"^\| \[([A-Z]{2,4}-\d{3})\]\(([^)]+)\) \| (.+?) \| (.+?)(?: \| .*?)? \|$"), policies)
    # 아직 정책이 없는 영역의 접두어도 "영역과 접두어" 표에서 읽는다.
    area = section(read(path_of("policies", "README.md")), "영역과 접두어") or ""
    for line in area.split("\n"):
        m = re.match(r"^\| ([A-Z]{2,4}) \|", line)
        if m:
            prefixes.add(m.group(1))

# ADR 번호: 공통은 ADR-0001, 플랫폼은 ADR-WEB-001처럼 접두어를 붙인다.
# 번호는 모든 ADR 폴더(공통 + 플랫폼)에서 겹치면 안 되고, 번호 참조는 모든 폴더의 번호를 기준으로 확인한다.
ADR_ID = r"ADR-(?:\d{4}|[A-Z]{2,4}-\d{3})"
adr_dirs = [path_of("adr")] if has_adr else []
for d in platform_docs:
    if os.path.isfile(os.path.join(d, "adr", "README.md")):
        adr_dirs.append(os.path.join(d, "adr"))
    elif os.path.isdir(os.path.join(d, "adr")):
        report(f"ADR 폴더는 있는데 목록 표의 README.md가 없음: {rel(os.path.join(d, 'adr'))}")
adrs = {}
for adr_dir in adr_dirs:
    found = {}
    for path in sorted(glob.glob(os.path.join(adr_dir, "ADR-*.md"))):
        if not re.match(ADR_ID + r"(-.+)?\.md$", os.path.basename(path)):
            continue
        lines = read(path).split("\n")
        head = re.match(r"### (" + ADR_ID + r") (.+)$", lines[0])
        if head is None:
            report(f"ADR 파일의 첫 줄이 '### 번호 제목' 형식이 아님: {rel(path)}")
            continue
        if re.match(ADR_ID, os.path.basename(path)).group(0) != head.group(1):
            report(f"ADR 파일 이름과 첫 줄의 번호가 다름: {rel(path)} → {head.group(1)}")
            continue
        if head.group(1) in adrs or head.group(1) in found:
            report(f"ADR 번호가 겹침: {head.group(1)} ({rel(path)})")
            continue
        status = field(lines, "상태")
        date = field(lines, "날짜")
        found[head.group(1)] = {"fields": ["링크", "제목", "상태", "날짜"], "values": [os.path.basename(path), head.group(2), status, date]}
    check_table("ADR", os.path.join(adr_dir, "README.md"), "ADR 목록",
                re.compile(r"^\| \[(" + ADR_ID + r")\]\(([^)]+)\) \| (.+?) \| (.+?) \| (\d{4}-\d{2}-\d{2}) \|$"), found)
    adrs.update(found)

if has_log:
    logs = {}
    for path in sorted(glob.glob(path_of("ai-usage", "log", "[0-9][0-9][0-9]-*.md"))):
        first = read(path).split("\n")[0]
        if not first.startswith("# ") or " · " not in first:
            report(f"로그 항목의 첫 줄이 '# 날짜 · 제목' 형식이 아님: {rel(path)}")
            continue
        date, _, name = first[2:].partition(" · ")
        number = os.path.basename(path)[:3]
        logs[number] = {"fields": ["날짜", "제목", "링크"], "values": [date, name, os.path.basename(path)]}
    check_table("AI 로그", ("ai-usage", "log", "README.md"), "항목 목록",
                re.compile(r"^\| (\d{3}) \| (\S+) \| \[(.+)\]\(([^)]+)\) \|$"), logs)

# ---------- 3. 정책·ADR 번호 참조
open_questions = path_of("policies", "open-questions.md")
planned = set()
if os.path.isfile(open_questions):
    planned = set(re.findall(r"\b[A-Z]{2,4}-\d{3}\b", read(open_questions)))
policy_ref = re.compile(r"(?<!ADR-)\b(?:" + "|".join(sorted(re.escape(p) for p in prefixes)) + r")-\d{3}\b") if prefixes else None
for path in scope:
    if os.path.abspath(path) == os.path.abspath(open_questions):
        continue
    for number, line in enumerate(without_fences(read(path)), start=1):
        if "부터 발급" in line:
            continue  # "정책 번호는 CAT-001부터 발급한다" 같은 번호 발급 안내문은 참조가 아니다.
        if has_policies and policy_ref:
            for pid in policy_ref.findall(line):
                if pid not in policies and pid not in planned:
                    report(f"없는 정책 번호 참조: {rel(path)}:{number} → {pid}")
        if adr_dirs:
            # 숫자가 들어간 ADR 번호 후보를 넓게 잡아, 형식이 틀린 번호(ADR-WEB-0001 등)도 놓치지 않는다.
            # 'ADR-xxxx', 'ADR-WEB-NNN' 같은 자리표시는 숫자가 없어 후보가 아니다.
            for aid in re.findall(r"\bADR-(?:[A-Z]+-)?\d+\b", line):
                if not re.fullmatch(ADR_ID, aid):
                    report(f"ADR 번호 형식이 틀림: {rel(path)}:{number} → {aid}")
                elif aid not in adrs:
                    report(f"없는 ADR 번호 참조: {rel(path)}:{number} → {aid}")

for message in violations:
    print("위반: " + message)
if violations:
    print(f"문서 검사 위반 {len(violations)}건")
    sys.exit(1)
print("문서 검사 통과")
PY
