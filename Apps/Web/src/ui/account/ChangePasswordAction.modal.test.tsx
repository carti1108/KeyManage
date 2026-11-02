import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ChangePasswordAction } from './ChangePasswordAction'

// 변경을 기다리는 동안 배경에서 편집·저장해 옛 키로 요청이 나가지 않게, 대화상자는 모달이다 (SEC-004)
function renderWithBackground() {
  render(
    <>
      <button type="button">배경 저장</button>
      <ChangePasswordAction readOnly={false} offline={false} onChange={vi.fn(async () => {})} />
    </>,
  )
  fireEvent.click(screen.getByRole('button', { name: '마스터 비밀번호 변경' }))
  return screen.getByRole('dialog', { name: '마스터 비밀번호 변경' })
}

function focusables(dialog: HTMLElement): HTMLElement[] {
  return Array.from(dialog.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled])'))
}

describe('ChangePasswordAction 모달 (SEC-004)', () => {
  it('대화상자는 aria-modal이고 열린 동안 배경을 inert로 막는다', () => {
    const dialog = renderWithBackground()

    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByText('배경 저장').closest('[inert]')).not.toBeNull()
    expect(dialog.closest('[inert]')).toBeNull()

    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))
    expect(screen.getByText('배경 저장').closest('[inert]')).toBeNull()
  })

  it('Tab과 Shift+Tab은 대화상자 안에서만 돈다', () => {
    const dialog = renderWithBackground()
    fireEvent.change(within(dialog).getByLabelText('현재 마스터 비밀번호'), { target: { value: 'something' } })
    const items = focusables(dialog)
    const first = items[0]
    const last = items[items.length - 1]

    last.focus()
    fireEvent.keyDown(last, { key: 'Tab' })
    expect(first).toHaveFocus()

    fireEvent.keyDown(first, { key: 'Tab', shiftKey: true })
    expect(last).toHaveFocus()
  })
})
