// 디자인 토큰 리터럴 검사 (ADR-0025 결정 7)
// 화면 코드의 Tailwind 클래스가 tokens.json의 척도와 시멘틱 색만 쓰는지 본다.
// - 간격: 0과 space 1~6 단계만 허용 (p-8, gap-1.5 금지)
// - 색: Tailwind 기본 팔레트(bg-red-500, text-white 등) 금지, 시멘틱 토큰(bg-destructive 등)만 허용
// - 임의 값: 간격·모서리·색 접두사에 [..] 금지 (p-[13px], bg-[#fff])
import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'

export const SPACING_SCALE = new Set(['0', '1', '2', '3', '4', '5', '6'])

const SPACING_PREFIX = '(?:p|px|py|pt|pb|pl|pr|ps|pe|m|mx|my|mt|mb|ml|mr|ms|me|gap|gap-x|gap-y|space-x|space-y)'
const COLOR_PREFIX = '(?:bg|text|border|border-[xytblr]|ring|ring-offset|fill|stroke|from|via|to|outline|divide|shadow|accent|caret|decoration|placeholder)'
const PALETTE =
  '(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)'

const spacingClass = new RegExp(`(?<![\\w-])-?${SPACING_PREFIX}-(\\d+(?:\\.\\d+)?)(?![\\w.-])`, 'g')
const paletteClass = new RegExp(`(?<![\\w-])${COLOR_PREFIX}-(?:${PALETTE}-\\d{2,3}|black|white)(?:/\\d+)?(?![\\w-])`, 'g')
const arbitraryClass = new RegExp(`(?<![\\w-])-?(?:${SPACING_PREFIX}|rounded(?:-[a-z]+)?|${COLOR_PREFIX})-\\[[^\\]]+\\]`, 'g')

export type Violation = { line: number; token: string; reason: string }

export function findDesignLiterals(source: string): Violation[] {
  const violations: Violation[] = []
  source.split(/\r?\n/).forEach((text, index) => {
    const line = index + 1
    for (const match of text.matchAll(spacingClass)) {
      if (!SPACING_SCALE.has(match[1])) {
        violations.push({ line, token: match[0], reason: '간격은 0, 1~6 단계만 씁니다' })
      }
    }
    for (const match of text.matchAll(paletteClass)) {
      violations.push({ line, token: match[0], reason: '기본 팔레트 대신 시멘틱 색 토큰을 씁니다' })
    }
    for (const match of text.matchAll(arbitraryClass)) {
      violations.push({ line, token: match[0], reason: '임의 값 대신 토큰을 씁니다' })
    }
  })
  return violations
}

function listSources(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) return listSources(path)
    return /\.tsx$/.test(entry.name) && !/\.test\.tsx$/.test(entry.name) ? [path] : []
  })
}

export function checkDirectory(dir: string): string[] {
  return listSources(dir).flatMap((path) =>
    findDesignLiterals(readFileSync(path, 'utf8')).map(
      (v) => `${relative(process.cwd(), path)}:${v.line} ${v.token} — ${v.reason}`,
    ),
  )
}

if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('designLiterals.ts')) {
  const problems = checkDirectory('src')
  if (problems.length > 0) {
    console.error('디자인 토큰 리터럴 위반 (ADR-0025 결정 7):')
    for (const problem of problems) console.error(`  ✖ ${problem}`)
    process.exit(1)
  }
  console.log('✔ 디자인 토큰 리터럴 검사 통과')
}
