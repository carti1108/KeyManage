import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AUTO_LOCK_OPTIONS_MS } from '../../domain/lock/autoLock'
import { AutoLockSelect } from './AutoLockSelect'

describe('AutoLockSelect (SEC-003)', () => {
  it('이름표가 붙은 선택 칸에 다섯 선택지를 순서대로 보인다', () => {
    render(<AutoLockSelect timeoutMs={AUTO_LOCK_OPTIONS_MS.fiveMinutes} onChange={() => undefined} />)

    const select = screen.getByRole('combobox', { name: '자동 잠금' })
    const labels = within(select).getAllByRole('option').map((option) => option.textContent)
    expect(labels).toEqual(['즉시', '1분', '5분', '15분', '1시간'])
  })

  it('현재 값을 선택해 보인다', () => {
    render(<AutoLockSelect timeoutMs={AUTO_LOCK_OPTIONS_MS.fifteenMinutes} onChange={() => undefined} />)

    expect(screen.getByRole('option', { name: '15분' })).toHaveProperty('selected', true)
  })

  it('바꾸면 밀리초로 알린다', () => {
    const onChange = vi.fn()
    render(<AutoLockSelect timeoutMs={AUTO_LOCK_OPTIONS_MS.fiveMinutes} onChange={onChange} />)

    fireEvent.change(screen.getByRole('combobox', { name: '자동 잠금' }), { target: { value: String(AUTO_LOCK_OPTIONS_MS.oneHour) } })
    fireEvent.change(screen.getByRole('combobox', { name: '자동 잠금' }), { target: { value: String(AUTO_LOCK_OPTIONS_MS.immediately) } })

    expect(onChange.mock.calls).toEqual([[AUTO_LOCK_OPTIONS_MS.oneHour], [AUTO_LOCK_OPTIONS_MS.immediately]])
  })
})
