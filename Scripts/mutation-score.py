#!/usr/bin/env python3
"""PIT 뮤테이션 리포트(mutations.xml)를 읽어 점수와 살아남은 결함 목록을 Markdown으로 출력한다 (ADR-0009).

CI에서는 GitHub Actions 작업 요약($GITHUB_STEP_SUMMARY)에 붙이고, 로컬에서는 터미널에서 바로 본다.

사용법: python3 Scripts/mutation-score.py Back/domain/build/reports/pitest/mutations.xml
"""
import sys
import xml.etree.ElementTree as ET

# Windows 콘솔에서도 한글이 깨지지 않도록 출력 인코딩을 고정한다
sys.stdout.reconfigure(encoding="utf-8")

if len(sys.argv) != 2:
    print("사용법: mutation-score.py <mutations.xml>")
    sys.exit(2)

try:
    mutations = list(ET.parse(sys.argv[1]).getroot())
except (OSError, ET.ParseError) as error:
    print(f"뮤테이션 리포트를 읽을 수 없음: {error}")
    sys.exit(2)

killed = [m for m in mutations if m.get("status") == "KILLED"]
survivors = [m for m in mutations if m.get("status") != "KILLED"]
score = round(100 * len(killed) / len(mutations)) if mutations else 0

print("## 뮤테이션 테스트 (domain)")
print()
print(f"**점수 {score}%**: 심은 결함 {len(mutations)}개 중 {len(killed)}개를 테스트가 잡음")
if survivors:
    print()
    print("<details><summary>살아남은 결함</summary>")
    print()
    print("| 상태 | 클래스 | 메서드 | 줄 | 바꾼 내용 |")
    print("|---|---|---|---|---|")
    for m in survivors:
        cls = m.findtext("mutatedClass", "").split(".")[-1]
        print(f"| {m.get('status')} | {cls} | {m.findtext('mutatedMethod', '')} | {m.findtext('lineNumber', '')} | {m.findtext('description', '')} |")
    print()
    print("</details>")
