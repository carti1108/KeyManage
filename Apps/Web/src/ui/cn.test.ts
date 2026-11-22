import { describe, expect, it } from 'vitest'
import { cn } from './cn'

describe('cn', () => {
  it('글자 크기 토큰과 글자색을 함께 두면 둘 다 남긴다', () => {
    expect(cn('text-primary-foreground', 'text-label')).toBe('text-primary-foreground text-label')
    expect(cn('text-label', 'text-accent-foreground')).toBe('text-label text-accent-foreground')
  })

  it('글자 크기 토큰끼리는 뒤의 것만 남긴다', () => {
    expect(cn('text-label', 'text-body')).toBe('text-body')
  })

  it('글자색끼리는 뒤의 것만 남긴다', () => {
    expect(cn('text-muted-foreground', 'text-foreground')).toBe('text-foreground')
  })
})
