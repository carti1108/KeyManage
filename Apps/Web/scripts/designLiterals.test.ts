// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { findDesignLiterals } from './designLiterals.ts'

const tokens = (source: string) => findDesignLiterals(source).map((v) => v.token)

describe('findDesignLiterals', () => {
  it('간격 척도(0, 1~6)와 시멘틱 색 클래스는 통과시킨다', () => {
    const source = `<div className="p-4 gap-2 -mt-1 px-0 rounded-md bg-destructive/10 text-muted-foreground border-border bg-overlay hover:bg-accent" />`

    expect(tokens(source)).toEqual([])
  })

  it('척도 밖 간격(반 단계, 8 이상)을 적발한다', () => {
    expect(tokens(`"gap-1.5 p-8 py-0.5 -mt-10"`)).toEqual(['gap-1.5', 'p-8', 'py-0.5', '-mt-10'])
  })

  it('기본 팔레트 색과 검정·흰색을 적발한다', () => {
    expect(tokens(`"bg-red-500 hover:text-emerald-600 bg-black/40 text-white"`)).toEqual([
      'bg-red-500',
      'text-emerald-600',
      'bg-black/40',
      'text-white',
    ])
  })

  it('간격·모서리·색의 임의 값을 적발하고 다른 임의 값은 둔다', () => {
    expect(tokens(`"p-[13px] rounded-[10px] bg-[#fff] active:scale-[0.99] transition-[width]"`)).toEqual([
      'p-[13px]',
      'rounded-[10px]',
      'bg-[#fff]',
    ])
  })

  it('기본 글자 크기를 적발하고 역할 토큰은 통과시킨다', () => {
    expect(tokens(`"text-sm md:text-2xl text-xs/6 text-label text-caption text-display font-semibold"`)).toEqual([
      'text-sm',
      'text-2xl',
      'text-xs/6',
    ])
  })

  it('위반 줄 번호를 1부터 센다', () => {
    expect(findDesignLiterals('ok\r\n"p-8"')[0].line).toBe(2)
  })
})
