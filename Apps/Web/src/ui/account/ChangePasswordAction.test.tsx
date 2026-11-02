import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { PasswordChangeOfflineError, SameMasterPasswordError, WrongMasterPasswordError } from '../../domain/session/vaultSession'
import { ServerError } from '../../domain/server/serverErrors'
import { VaultSyncConflictError } from '../../domain/vault/vaultSync'
import { ChangePasswordAction } from './ChangePasswordAction'

const CURRENT = 'correct horse battery staple'
const NEXT = 'a brand new master password'
const NOTICE =
  '비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다. 예전에 내보낸 백업은 예전 비밀번호로 엽니다.'

function renderAction({ readOnly = false, offline = false, onChange = vi.fn(async (_current: string, _next: string) => {}) } = {}) {
  render(<ChangePasswordAction readOnly={readOnly} offline={offline} onChange={onChange} />)
  return { onChange }
}

function open() {
  fireEvent.click(screen.getByRole('button', { name: '마스터 비밀번호 변경' }))
  return screen.getByRole('dialog', { name: '마스터 비밀번호 변경' })
}

function fill(dialog: HTMLElement, { current = CURRENT, next = NEXT, confirmation = next } = {}) {
  fireEvent.change(within(dialog).getByLabelText('현재 마스터 비밀번호'), { target: { value: current } })
  fireEvent.change(within(dialog).getByLabelText('새 마스터 비밀번호'), { target: { value: next } })
  fireEvent.change(within(dialog).getByLabelText('새 마스터 비밀번호 확인'), { target: { value: confirmation } })
}

function submit(dialog: HTMLElement) {
  fireEvent.click(within(dialog).getByRole('button', { name: '변경' }))
}

describe('ChangePasswordAction (SEC-004)', () => {
  it('변경 전 안내를 정책 문구 그대로 보이고 SEC-002 규칙을 알린다', () => {
    renderAction()

    const dialog = open()

    expect(dialog).toHaveAccessibleDescription(NOTICE)
    expect(within(dialog).getByText('마스터 비밀번호는 12자 이상이어야 합니다.')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '변경' })).toBeInTheDocument()
  })

  it('현재·새 비밀번호를 넘기고, 끝나면 대화상자를 닫고 완료 문구를 알린다', async () => {
    const { onChange } = renderAction()
    const dialog = open()

    fill(dialog)
    submit(dialog)

    await vi.waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('마스터 비밀번호를 바꿨습니다.'))
    expect(onChange).toHaveBeenCalledWith(CURRENT, NEXT)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('button', { name: '마스터 비밀번호 변경' })).toHaveFocus()
  })

  it('새 비밀번호가 12자보다 짧거나 확인이 다르거나 지금과 같으면 요청하지 않고 알린다', () => {
    const { onChange } = renderAction()
    const dialog = open()

    for (const [input, message] of [
      [{ next: 'short pw 11' }, '마스터 비밀번호는 12자 이상이어야 합니다.'],
      [{ confirmation: 'something else entirely' }, '비밀번호 확인이 일치하지 않습니다.'],
      [{ next: CURRENT }, '새 비밀번호가 지금 비밀번호와 같습니다.'],
    ] as const) {
      fill(dialog, input)
      submit(dialog)
      expect(within(dialog).getByRole('alert')).toHaveTextContent(message)
    }
    expect(onChange).not.toHaveBeenCalled()
  })

  it('오프라인이면 대화상자를 열지 않고 정책 문구로 알린다', () => {
    renderAction({ offline: true })

    fireEvent.click(screen.getByRole('button', { name: '마스터 비밀번호 변경' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('alert')).toHaveTextContent('인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.')
  })

  it('읽기 전용 볼트에서는 진입점을 막는다', () => {
    renderAction({ readOnly: true })

    expect(screen.getByRole('button', { name: '마스터 비밀번호 변경' })).toBeDisabled()
  })

  it.each([
    [new WrongMasterPasswordError(), '마스터 비밀번호가 올바르지 않습니다.'],
    [new VaultSyncConflictError(), '다른 기기의 변경 사항과 충돌했습니다. 잠시 후 다시 시도해 주세요.'],
    [new ServerError(429, 'TOO_MANY_ATTEMPTS', 900), '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.'],
    [new PasswordChangeOfflineError(), '인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.'],
    [new SameMasterPasswordError(), '새 비밀번호가 지금 비밀번호와 같습니다.'],
  ])('실패하면 대화상자에 정책 문구를 보이고 현재 비밀번호 칸을 비운다 (%s)', async (error, message) => {
    renderAction({ onChange: vi.fn(async () => Promise.reject(error)) })
    const dialog = open()

    fill(dialog)
    submit(dialog)

    await vi.waitFor(() => expect(within(dialog).getByRole('alert')).toHaveTextContent(message))
    expect(within(dialog).getByLabelText('현재 마스터 비밀번호')).toHaveValue('')
    expect(within(dialog).getByLabelText('현재 마스터 비밀번호')).toHaveFocus()
  })

  it('바꾸는 동안 진행 문구를 보이고 Esc로 닫히지 않는다', async () => {
    let finish!: () => void
    renderAction({ onChange: vi.fn(() => new Promise<void>((resolve) => (finish = resolve))) })
    const dialog = open()

    fill(dialog)
    submit(dialog)
    fireEvent.keyDown(dialog, { key: 'Escape' })

    expect(within(dialog).getByRole('status')).toHaveTextContent('마스터 비밀번호를 바꾸는 중입니다…')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    finish()
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('Esc나 취소로 닫으면 입력을 비우고 진입 버튼으로 포커스를 돌린다', () => {
    const { onChange } = renderAction()
    let dialog = open()
    fill(dialog)

    fireEvent.keyDown(dialog, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('button', { name: '마스터 비밀번호 변경' })).toHaveFocus()

    dialog = open()
    expect(within(dialog).getByLabelText('현재 마스터 비밀번호')).toHaveValue('')
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(onChange).not.toHaveBeenCalled()
  })
})
