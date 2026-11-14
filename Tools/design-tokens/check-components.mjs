#!/usr/bin/env node
// 컴포넌트 계약 게이트 (ADR-0025 결정 7). Node 표준 라이브러리만 쓴다.
//   node Tools/design-tokens/check-components.mjs
// docs/design-system.md 6.1절 구현 현황 표와 실제 코드가 맞는지 양방향으로 본다.
//   - '구현'이면 웹은 Apps/Web/src/ui/components/<이름>.tsx에 export function <이름>이,
//     Apple은 DesignSystem Sources에 public struct <KM이름>이 있어야 한다.
//     칸에 '구현 (`다른이름`)'처럼 적으면 그 이름으로 찾는다.
//   - '미구현'이면 해당 구현이 없어야 한다(문서가 낡지 않게).
//   - 표에 없는 웹 컴포넌트 파일이나 Apple KM 타입이 있으면 실패한다.
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const DOC = join(ROOT, 'docs/design-system.md');
const WEB_DIR = join(ROOT, 'Apps/Web/src/ui/components');
const APPLE_DIR = join(ROOT, 'Apps/Apple/Projects/Core/DesignSystem/Sources');

function fail(problems) {
  console.error('컴포넌트 계약 위반 (docs/design-system.md 6.1절):');
  for (const problem of problems) console.error(`  ✖ ${problem}`);
  process.exit(1);
}

export function parseContractTable(markdown) {
  const section = markdown.split(/\r?\n### 6\.1\./)[1]?.split(/\r?\n### /)[0];
  if (!section) return null;
  return section
    .split(/\r?\n/)
    .filter((line) => /^\| `/.test(line))
    .map((line) => {
      const [name, web, apple] = line.split('|').slice(1, 4).map((cell) => cell.trim());
      const [, spec, appleName] = name.match(/^`(\w+)` \(`(\w+)`\)$/) ?? [];
      const status = (cell, fallback) => ({
        implemented: cell.startsWith('구현'),
        name: cell.match(/^구현 \(`(\w+)`\)/)?.[1] ?? fallback,
      });
      return { row: name, spec, web: status(web, spec), apple: status(apple, appleName) };
    });
}

function webComponents() {
  const found = new Map();
  for (const file of readdirSync(WEB_DIR)) {
    if (!/^[A-Z]\w*\.tsx$/.test(file) || file.endsWith('.test.tsx')) continue;
    const name = file.replace(/\.tsx$/, '');
    found.set(name, new RegExp(`export function ${name}\\b`).test(readFileSync(join(WEB_DIR, file), 'utf8')));
  }
  return found;
}

function appleComponents() {
  const found = new Set();
  for (const file of readdirSync(APPLE_DIR)) {
    if (!file.endsWith('.swift')) continue;
    for (const match of readFileSync(join(APPLE_DIR, file), 'utf8').matchAll(/public struct (KM\w+)/g)) found.add(match[1]);
  }
  return found;
}

function main() {
  const rows = parseContractTable(readFileSync(DOC, 'utf8'));
  if (!rows || rows.length === 0) fail(['6.1절 구현 현황 표를 찾을 수 없습니다']);
  const problems = [];
  const web = webComponents();
  const apple = appleComponents();
  const listedWeb = new Set();
  const listedApple = new Set();

  for (const { row, spec, web: w, apple: a } of rows) {
    if (!spec) {
      problems.push(`${row}: 첫 칸은 \`규격이름\` (\`KM이름\`) 형식이어야 합니다`);
      continue;
    }
    listedWeb.add(w.name);
    listedApple.add(a.name);
    if (w.implemented && web.get(w.name) !== true) problems.push(`${row}: Web '구현'인데 components/${w.name}.tsx의 export function ${w.name}이 없습니다`);
    if (!w.implemented && web.has(w.name)) problems.push(`${row}: Web '미구현'인데 components/${w.name}.tsx가 있습니다`);
    if (a.implemented && !apple.has(a.name)) problems.push(`${row}: Apple '구현'인데 public struct ${a.name}이 없습니다`);
    if (!a.implemented && apple.has(a.name)) problems.push(`${row}: Apple '미구현'인데 public struct ${a.name}이 있습니다`);
  }
  for (const name of web.keys()) {
    if (!listedWeb.has(name)) problems.push(`Web components/${name}.tsx가 6.1절 표에 없습니다`);
  }
  for (const name of apple) {
    if (!listedApple.has(name)) problems.push(`Apple ${name}이 6.1절 표에 없습니다`);
  }

  if (problems.length > 0) fail(problems);
  console.log(`컴포넌트 계약 검사 통과 (${rows.length}개)`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) main();
