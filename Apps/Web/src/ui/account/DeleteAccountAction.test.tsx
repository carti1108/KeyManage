import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ServerError } from '../../domain/server/serverErrors'
import { AccountDeletionOfflineError, WrongMasterPasswordError } from '../../domain/session/vaultSession'
import { DeleteAccountAction } from './DeleteAccountAction'

const PASSWORD = 'correct horse battery staple'
const NOTICE =
  '계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다. 필요하면 먼저 백업을 내보내 주세요.'
const PHRASE_LABEL = "확인을 위해 '계정 삭제'를 입력해 주세요."

function renderAction({ offline = false, onDelete = vi.fn(async (_password: string) => {}) } = {}) {
  render(
    <>
      <button type="button">배경 저장</button>
      <DeleteAccountAction offline={offline} onDelete={onDelete} />
    </>,
  )
  return { onDelete }
}

function open() {
  fireEvent.click(screen.getByRole('button', { name: '계정 삭제' }))
  return screen.getByRole('dialog', { name: '계정 삭제' })
}

function fill(dialog: HTMLElement, { password = PASSWORD, phrase = '계정 삭제' } = {}) {
  fireEvent.change(within(dialog).getByLabelText('마스터 비밀번호'), { target: { value: password } })
  fireEvent.change(within(dialog).getByLabelText(PHRASE_LABEL), { target: { value: phrase } })
}

function deleteButton(dialog: HTMLElement) {
  return within(dialog).getByRole('button', { name: '삭제' })
}

describe('DeleteAccountAction (SEC-005)', () => {
  it('삭제 전 안내를 정책 문구 그대로 보이고 확인 문구 칸의 이름은 정책 문구다', () => {
    renderAction()

    const dialog = open()

    expect(dialog).toHaveAccessibleDescription(NOTICE)
    expect(within(dialog).getByLabelText(PHRASE_LABEL)).toBeInTheDocument()
    expect(deleteButton(dialog)).toBeInTheDocument()
  })

  it('확인 문구가 정확히 계정 삭제일 때만 삭제 버튼이 켜진다', () => {
    renderAction()
    const dialog = open()

    for (const phrase of ['', '계정삭제', '계정 삭제 ', ' 계정 삭제', '계정 삭제.', '계정  삭제']) {
      fill(dialog, { phrase })
      expect(deleteButton(dialog), JSON.stringify(phrase)).toBeDisabled()
    }
    fill(dialog, { phrase: '계정 삭제' })
    expect(deleteButton(dialog)).toBeEnabled()
  })

  it('비밀번호가 비어 있으면 확인 문구가 맞아도 삭제 버튼이 꺼져 있다', () => {
    renderAction()
    const dialog = open()

    fill(dialog, { password: '' })

    expect(deleteButton(dialog)).toBeDisabled()
  })

  it('비밀번호를 넘기고 지우는 동안 진행 문구를 보이며 Esc로 닫히지 않는다', async () => {
    let finish!: () => void
    const { onDelete } = renderAction({ onDelete: vi.fn(() => new Promise<void>((resolve) => (finish = resolve))) })
    const dialog = open()

    fill(dialog)
    fireEvent.click(deleteButton(dialog))
    fireEvent.keyDown(dialog, { key: 'Escape' })

    expect(onDelete).toHaveBeenCalledWith(PASSWORD)
    expect(within(dialog).getByRole('status')).toHaveTextContent('계정을 삭제하는 중입니다…')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    finish()
    await vi.waitFor(() => expect(screen.queryByRole('status')).toBeNull())
  })

  it('오프라인이면 대화상자를 열지 않고 정책 문구로 알린다', () => {
    const { onDelete } = renderAction({ offline: true })

    fireEvent.click(screen.getByRole('button', { name: '계정 삭제' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('alert')).toHaveTextContent('인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다.')
    expect(onDelete).not.toHaveBeenCalled()
  })

  it.each([
    [new WrongMasterPasswordError(), '마스터 비밀번호가 올바르지 않습니다.'],
    [new ServerError(429, 'TOO_MANY_ATTEMPTS', 900), '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.'],
    [new AccountDeletionOfflineError(), '인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다.'],
  ])('실패하면 대화상자에 정책 문구를 보이고 비밀번호 칸을 비운다 (%s)', async (error, message) => {
    renderAction({ onDelete: vi.fn(async () => Promise.reject(error)) })
    const dialog = open()

    fill(dialog)
    fireEvent.click(deleteButton(dialog))

    await vi.waitFor(() => expect(within(dialog).getByRole('alert')).toHaveTextContent(message))
    expect(within(dialog).getByLabelText('마스터 비밀번호')).toHaveValue('')
    expect(within(dialog).getByLabelText('마스터 비밀번호')).toHaveFocus()
  })

  it('Esc나 취소로 닫으면 입력을 비우고 진입 버튼으로 포커스를 돌린다', () => {
    const { onDelete } = renderAction()
    let dialog = open()
    fill(dialog)

    fireEvent.keyDown(dialog, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('button', { name: '계정 삭제' })).toHaveFocus()

    dialog = open()
    expect(within(dialog).getByLabelText('마스터 비밀번호')).toHaveValue('')
    expect(within(dialog).getByLabelText(PHRASE_LABEL)).toHaveValue('')
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(onDelete).not.toHaveBeenCalled()
  })

  it('대화상자는 aria-modal이고 열린 동안 배경을 inert로 막는다', () => {
    renderAction()
    const dialog = open()

    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByText('배경 저장').closest('[inert]')).not.toBeNull()

    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))
    expect(screen.getByText('배경 저장').closest('[inert]')).toBeNull()
  })
})
