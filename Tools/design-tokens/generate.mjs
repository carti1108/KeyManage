#!/usr/bin/env node
// 디자인 토큰 생성기 (ADR-0026). Node 표준 라이브러리만 쓴다.
//   node Tools/design-tokens/generate.mjs           tokens.json으로 플랫폼 파일을 다시 쓴다
//   node Tools/design-tokens/generate.mjs --check   다시 만든 결과가 커밋된 파일과 같은지만 본다
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = dirname(fileURLToPath(import.meta.url));
const ROOT = join(TOOL_DIR, '..', '..');
const SOURCE = join(TOOL_DIR, 'tokens.json');
const OUTPUTS = {
  css: join(ROOT, 'Apps/Web/src/styles/tokens.css'),
  swift: join(ROOT, 'Apps/Apple/Projects/Core/DesignSystem/Sources/Generated/DesignToken+Generated.swift'),
};
const NOTICE = '생성 파일입니다. 직접 고치지 말고 Tools/design-tokens/tokens.json을 고친 뒤 node Tools/design-tokens/generate.mjs를 실행하세요 (ADR-0026).';

const HEX = /^#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$/;
const RGBA = /^rgba\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*(?:0|1|0?\.\d+)\s*\)$/;
const NAME = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

function fail(message) {
  console.error(`design-tokens: ${message}`);
  process.exit(1);
}

function assertName(group, name) {
  if (!NAME.test(name)) fail(`${group}.${name}: 토큰 이름은 소문자 kebab-case여야 합니다`);
}

function readColors(group) {
  return Object.entries(group).map(([name, token]) => {
    assertName('color', name);
    if (token.$type !== 'color') fail(`color.${name}: $type은 color여야 합니다`);
    for (const mode of ['light', 'dark']) {
      const value = token.$value?.[mode];
      if (typeof value !== 'string' || !(HEX.test(value) || RGBA.test(value))) {
        fail(`color.${name}.${mode}: #RRGGBB, #RRGGBBAA, rgba(r, g, b, a) 중 하나여야 합니다 (받은 값: ${value})`);
      }
    }
    return { name, light: token.$value.light, dark: token.$value.dark };
  });
}

function readDimensions(groupName, group) {
  return Object.entries(group).map(([key, token]) => {
    const name = `${groupName}-${key}`;
    assertName(groupName, key);
    const { value, unit } = token.$value ?? {};
    if (token.$type !== 'dimension' || unit !== 'px' || !Number.isFinite(value) || value < 0) {
      fail(`${name}: $type dimension, unit px, 0 이상의 숫자여야 합니다`);
    }
    return { name, key, value };
  });
}

const APPLE_TEXT_STYLES = new Set([
  'largeTitle', 'title', 'title2', 'title3', 'headline', 'subheadline', 'body', 'callout', 'footnote', 'caption', 'caption2',
]);
const FONT_WEIGHTS = { 100: 'ultraLight', 200: 'thin', 300: 'light', 400: 'regular', 500: 'medium', 600: 'semibold', 700: 'bold', 800: 'heavy', 900: 'black' };

function readRem(name, field, dimension) {
  const { value, unit } = dimension ?? {};
  if (unit !== 'rem' || !Number.isFinite(value) || value <= 0) fail(`typography.${name}.${field}: unit rem, 0보다 큰 숫자여야 합니다`);
  return value;
}

function readTypography(group) {
  return Object.entries(group).map(([name, token]) => {
    assertName('typography', name);
    if (token.$type !== 'typography') fail(`typography.${name}: $type은 typography여야 합니다`);
    const { fontSize, lineHeight, fontWeight } = token.$value ?? {};
    if (!(fontWeight in FONT_WEIGHTS)) fail(`typography.${name}.fontWeight: 100~900의 100 단위여야 합니다`);
    const textStyle = token.$extensions?.['com.keymanage.apple']?.textStyle;
    if (!APPLE_TEXT_STYLES.has(textStyle)) {
      fail(`typography.${name}: $extensions["com.keymanage.apple"].textStyle이 Dynamic Type 스타일이 아닙니다 (받은 값: ${textStyle})`);
    }
    return { name, fontSize: readRem(name, 'fontSize', fontSize), lineHeight: readRem(name, 'lineHeight', lineHeight), fontWeight, textStyle };
  });
}

function loadTokens() {
  let raw;
  try {
    raw = JSON.parse(readFileSync(SOURCE, 'utf8'));
  } catch (error) {
    fail(`tokens.json을 읽을 수 없습니다: ${error.message}`);
  }
  for (const group of ['color', 'space', 'radius', 'typography']) {
    if (typeof raw[group] !== 'object' || raw[group] === null) fail(`${group} 그룹이 없습니다`);
  }
  return {
    colors: readColors(raw.color),
    space: readDimensions('space', raw.space),
    radius: readDimensions('radius', raw.radius),
    typography: readTypography(raw.typography),
  };
}

const cssColor = (value) => (HEX.test(value) ? value.toLowerCase() : value);
const camel = (name) => name.replace(/-([a-z0-9])/g, (_, c) => c.toUpperCase());

function renderCss({ colors, space, radius, typography }) {
  const vars = (mode) => colors.map((c) => `  --${c.name}: ${cssColor(c[mode])};`).join('\n');
  return `/* ${NOTICE} */

:root {
  color-scheme: light;
${vars('light')}
${space.map((s) => `  --${s.name}: ${s.value}px;`).join('\n')}
}

.dark {
  color-scheme: dark;
${vars('dark')}
}

@theme inline {
${colors.map((c) => `  --color-${c.name}: var(--${c.name});`).join('\n')}
${radius.map((r) => `  --${r.name}: ${r.value}px;`).join('\n')}
${typography
  .map((t) =>
    [
      `  --text-${t.name}: ${t.fontSize}rem;`,
      `  --text-${t.name}--line-height: ${t.lineHeight}rem;`,
      `  --text-${t.name}--font-weight: ${t.fontWeight};`,
    ].join('\n'),
  )
  .join('\n')}
}
`;
}

function renderSwift({ colors, space, radius, typography }) {
  const font = (t, design) => `.system(.${t.textStyle}, design: .${design}).weight(.${FONT_WEIGHTS[t.fontWeight]})`;
  const swiftColor = (value) => (HEX.test(value) ? value.toUpperCase() : value);
  const dims = (list) => list.map((d) => `        public static let ${camel(d.name)}: CGFloat = ${d.value}`).join('\n');
  return `// ${NOTICE}
import SwiftUI

extension DesignToken {
    public enum Colors {
${colors
  .map((c) => `        public static let ${camel(c.name)} = ColorToken(lightHex: "${swiftColor(c.light)}", darkHex: "${swiftColor(c.dark)}")`)
  .join('\n')}
    }

    public enum Spacing {
${dims(space)}
    }

    public enum Radius {
${dims(radius.map((r) => ({ ...r, name: r.key })))}
    }
}

extension DesignToken.Typography {
${typography
  .map((t) =>
    [
      `    public static var ${camel(t.name)}: Font { ${font(t, 'default')} }`,
      `    public static var ${camel(t.name)}Mono: Font { ${font(t, 'monospaced')} }`,
    ].join('\n'),
  )
  .join('\n')}
}
`;
}

const normalize = (text) => text.replace(/\r\n/g, '\n');

function main() {
  const check = process.argv.includes('--check');
  const tokens = loadTokens();
  const rendered = { css: renderCss(tokens), swift: renderSwift(tokens) };
  const stale = [];
  for (const [kind, path] of Object.entries(OUTPUTS)) {
    const current = existsSync(path) ? normalize(readFileSync(path, 'utf8')) : null;
    if (current === rendered[kind]) continue;
    if (check) {
      stale.push(relative(ROOT, path));
      continue;
    }
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, rendered[kind]);
    console.log(`생성: ${relative(ROOT, path)}`);
  }
  if (stale.length > 0) {
    fail(`생성 파일이 tokens.json과 다릅니다. node Tools/design-tokens/generate.mjs를 실행하세요:\n  ${stale.join('\n  ')}`);
  }
  console.log(check ? '디자인 토큰 검사 통과' : '디자인 토큰 생성 완료');
}

main();
