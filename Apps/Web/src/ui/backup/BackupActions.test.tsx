import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { BackupDecryptError, MAX_BACKUP_FILE_BYTES, type BackupFile, type BackupFileSource, type VaultBackup } from '../../domain/backup/backupFile'
import { BACKUP, backupText, fileSource as source } from '../../test/backupFixtures'
import { BackupActions } from './BackupActions'

const NOW = new Date(2026, 10, 1, 9, 30)
const FILE: BackupFile = { name: 'keymanage-backup-20261101.json', text: '{}' }
const PASSWORD = 'correct horse battery staple'

function renderActions({ readOnly = false, picked = source(backupText()) as BackupFileSource | null } = {}) {
  const files = { save: vi.fn(), pick: vi.fn(async () => picked) }
  const onExport = vi.fn(async () => FILE)
  const onRestore = vi.fn(async (_backup: VaultBackup, _password: string, _now: Date) => 2)
  render(<BackupActions readOnly={readOnly} files={files} onExport={onExport} onRestore={onRestore} now={() => NOW} />)
  return { files, onExport, onRestore }
}

/** 진행 중 문구도 status라 완료 문구로 바뀔 때까지 기다린다 */
async function expectStatus(text: string) {
  await vi.waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(text))
}

async function openRestore() {
  fireEvent.click(screen.getByRole('button', { name: '백업에서 복원' }))
  return screen.findByRole('dialog', { name: '백업에서 복원' })
}

function enterPassword(dialog: HTMLElement, value = PASSWORD) {
  fireEvent.change(within(dialog).getByLabelText('백업 파일 비밀번호'), { target: { value } })
}

describe('BackupActions (VLT-004)', () => {
  describe('내보내기', () => {
    it('내보내기 전 안내를 정책 문구 그대로 보이고, 확인하면 지금 시각으로 만든 파일을 저장한다', async () => {
      const { files, onExport } = renderActions()

      fireEvent.click(screen.getByRole('button', { name: '백업 내보내기' }))
      const prompt = screen.getByRole('alertdialog', { name: '백업 내보내기' })
      expect(prompt).toHaveAccessibleDescription(
        '이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다. 비밀번호를 바꾼 뒤에도 이 파일은 예전 비밀번호로 엽니다.',
      )
      expect(within(prompt).queryByLabelText(/비밀번호/)).toBeNull()
      fireEvent.click(within(prompt).getByRole('button', { name: '내보내기' }))

      await vi.waitFor(() => expect(files.save).toHaveBeenCalledWith(FILE))
      expect(onExport).toHaveBeenCalledWith(NOW)
      expect(screen.queryByRole('alertdialog')).toBeNull()
    })

    it('취소하면 내보내지 않는다', () => {
      const { files, onExport } = renderActions()

      fireEvent.click(screen.getByRole('button', { name: '백업 내보내기' }))
      fireEvent.click(screen.getByRole('button', { name: '취소' }))

      expect(screen.queryByRole('alertdialog')).toBeNull()
      expect(onExport).not.toHaveBeenCalled()
      expect(files.save).not.toHaveBeenCalled()
    })

    it('읽기 전용 볼트도 내보낼 수 있지만 복원은 막는다', () => {
      renderActions({ readOnly: true })

      expect(screen.getByRole('button', { name: '백업 내보내기' })).toBeEnabled()
      expect(screen.getByRole('button', { name: '백업에서 복원' })).toBeDisabled()
    })
  })

  describe('복원', () => {
    it('파일을 고르고 비밀번호를 넣으면 복원 시각과 함께 복원하고 복원한 항목 수를 알린다', async () => {
      const { onRestore } = renderActions()

      const dialog = await openRestore()
      enterPassword(dialog)
      fireEvent.click(within(dialog).getByRole('button', { name: '복원' }))

      await expectStatus('백업에서 항목 2개를 복원했습니다.')
      expect(onRestore).toHaveBeenCalledWith(BACKUP, PASSWORD, NOW)
      expect(screen.queryByRole('dialog')).toBeNull()
    })

    it('복원 전 안내를 정책 문구 그대로 비밀번호 대화상자에 보인다', async () => {
      renderActions()

      const dialog = await openRestore()

      expect(dialog).toHaveAccessibleDescription(
        '백업에 있던 항목은 백업 시점 내용으로 돌아갑니다. 백업 뒤에 고치거나 지운 내용은 되돌아가고, 백업 뒤에 새로 만든 항목은 그대로 남습니다.',
      )
      expect(within(dialog).getByRole('button', { name: '복원' })).toBeInTheDocument()
    })

    it('파일 선택을 취소하면 아무것도 하지 않는다', async () => {
      const { files } = renderActions({ picked: null })

      fireEvent.click(screen.getByRole('button', { name: '백업에서 복원' }))

      await vi.waitFor(() => expect(files.pick).toHaveBeenCalled())
      expect(screen.queryByRole('dialog')).toBeNull()
      expect(screen.queryByRole('alert')).toBeNull()
    })

    it.each([
      ['JSON 아님', source('not json')],
      ['format 다름', source(backupText((raw) => (raw.format = 'other')))],
      ['필드 누락', source(backupText((raw) => delete raw.vault))],
      ['파일 읽기 한도 초과', source(backupText(), MAX_BACKUP_FILE_BYTES + 1)],
      ['반복 횟수 범위 밖', source(backupText((raw) => ((raw.kdf as Record<string, unknown>).iterations = 1000)))],
    ])('%s → 비밀번호를 묻지 않고 형식 오류 문구', async (_name, picked) => {
      renderActions({ picked })

      fireEvent.click(screen.getByRole('button', { name: '백업에서 복원' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('KeyManage 백업 파일이 아닙니다.')
      expect(screen.queryByRole('dialog')).toBeNull()
    })

    it('더 높은 형식 버전이면 업데이트 안내 문구', async () => {
      renderActions({ picked: source(backupText((raw) => (raw.formatVersion = 2))) })

      fireEvent.click(screen.getByRole('button', { name: '백업에서 복원' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('더 새 버전의 앱에서 만든 백업입니다. 앱을 업데이트한 뒤 복원해 주세요.')
    })

    it('열지 못하면 정책 문구를 보이고, 비운 비밀번호 칸으로 다시 시도할 수 있다', async () => {
      const { onRestore } = renderActions()
      onRestore.mockRejectedValueOnce(new BackupDecryptError())

      const dialog = await openRestore()
      enterPassword(dialog, 'wrong')
      fireEvent.click(within(dialog).getByRole('button', { name: '복원' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('백업을 열 수 없습니다. 비밀번호가 맞는지, 파일이 손상되지 않았는지 확인해 주세요.')
      expect(within(dialog).getByLabelText('백업 파일 비밀번호')).toHaveValue('')

      enterPassword(dialog)
      fireEvent.click(within(dialog).getByRole('button', { name: '복원' }))
      await expectStatus('백업에서 항목 2개를 복원했습니다.')
      expect(screen.queryByRole('alert')).toBeNull()
    })

    it('복원 중에는 진행을 보이고 버튼을 막으며, 다시 제출해도 한 번만 복원한다. 비밀번호는 바로 비운다', async () => {
      const { onRestore } = renderActions()
      let finish: (count: number) => void = () => {}
      onRestore.mockImplementationOnce(() => new Promise<number>((resolve) => (finish = resolve)))

      const dialog = await openRestore()
      enterPassword(dialog)
      const form = within(dialog).getByRole('button', { name: '복원' }).closest('form')!
      fireEvent.submit(form)
      fireEvent.submit(form)

      expect(onRestore).toHaveBeenCalledTimes(1)
      expect(within(dialog).getByLabelText('백업 파일 비밀번호')).toHaveValue('')
      expect(within(dialog).getByRole('button', { name: '복원' })).toHaveAttribute('aria-busy', 'true')
      expect(within(dialog).getByRole('button', { name: '복원' })).toBeDisabled()
      expect(within(dialog).getByRole('button', { name: '취소' })).toBeDisabled()
      expect(screen.getByRole('button', { name: '백업에서 복원' })).toBeDisabled()
      expect(screen.getByRole('button', { name: '백업 내보내기' })).toBeDisabled()

      await act(async () => finish(1))
      await expectStatus('백업에서 항목 1개를 복원했습니다.')
      expect(onRestore).toHaveBeenCalledTimes(1)
    })

    it('비밀번호 없이 제출하면 복원하지 않는다', async () => {
      const { onRestore } = renderActions()

      const dialog = await openRestore()
      fireEvent.click(within(dialog).getByRole('button', { name: '복원' }))

      expect(onRestore).not.toHaveBeenCalled()
    })

    it('취소하면 비밀번호와 고른 파일을 버린다', async () => {
      const { onRestore } = renderActions()

      const dialog = await openRestore()
      enterPassword(dialog)
      fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))

      expect(screen.queryByRole('dialog')).toBeNull()
      const reopened = await openRestore()
      expect(within(reopened).getByLabelText('백업 파일 비밀번호')).toHaveValue('')
      expect(onRestore).not.toHaveBeenCalled()
    })
  })

  describe('접근성', () => {
    it('내보내기 안내를 닫거나 내보내면 포커스가 백업 내보내기 버튼으로 돌아온다', async () => {
      const { files } = renderActions()
      const opener = screen.getByRole('button', { name: '백업 내보내기' })

      fireEvent.click(opener)
      fireEvent.click(screen.getByRole('button', { name: '취소' }))
      expect(opener).toHaveFocus()

      fireEvent.click(opener)
      fireEvent.click(screen.getByRole('button', { name: '내보내기' }))
      await vi.waitFor(() => expect(files.save).toHaveBeenCalled())
      await vi.waitFor(() => expect(opener).toHaveFocus())
    })

    it('Esc로 내보내기 안내와 복원 대화상자를 닫고 연 버튼으로 포커스를 돌린다', async () => {
      const { onExport } = renderActions()

      fireEvent.click(screen.getByRole('button', { name: '백업 내보내기' }))
      fireEvent.keyDown(screen.getByRole('alertdialog'), { key: 'Escape' })
      expect(screen.queryByRole('alertdialog')).toBeNull()
      expect(screen.getByRole('button', { name: '백업 내보내기' })).toHaveFocus()
      expect(onExport).not.toHaveBeenCalled()

      const dialog = await openRestore()
      enterPassword(dialog)
      fireEvent.keyDown(within(dialog).getByLabelText('백업 파일 비밀번호'), { key: 'Escape' })
      expect(screen.queryByRole('dialog')).toBeNull()
      expect(screen.getByRole('button', { name: '백업에서 복원' })).toHaveFocus()
    })

    it('복원 대화상자는 비밀번호 칸에 포커스를 두고, 복원이 끝나면 백업에서 복원 버튼으로 돌린다', async () => {
      renderActions()

      const dialog = await openRestore()
      expect(within(dialog).getByLabelText('백업 파일 비밀번호')).toHaveFocus()
      enterPassword(dialog)
      fireEvent.click(within(dialog).getByRole('button', { name: '복원' }))

      await expectStatus('백업에서 항목 2개를 복원했습니다.')
      expect(screen.getByRole('button', { name: '백업에서 복원' })).toHaveFocus()
    })

    it('복원에 실패하면 비밀번호 칸으로 포커스를 옮긴다', async () => {
      const { onRestore } = renderActions()
      onRestore.mockRejectedValueOnce(new BackupDecryptError())

      const dialog = await openRestore()
      enterPassword(dialog, 'wrong')
      const field = within(dialog).getByLabelText('백업 파일 비밀번호')
      fireEvent.click(within(dialog).getByRole('button', { name: '복원' }))

      await screen.findByRole('alert')
      await vi.waitFor(() => expect(field).toHaveFocus())
    })

    it('복원 중에는 진행 문구를 status로 알리고, Esc로 닫지 않는다', async () => {
      const { onRestore } = renderActions()
      let finish: (count: number) => void = () => {}
      onRestore.mockImplementationOnce(() => new Promise<number>((resolve) => (finish = resolve)))

      const dialog = await openRestore()
      enterPassword(dialog)
      fireEvent.click(within(dialog).getByRole('button', { name: '복원' }))

      expect(screen.getByRole('status')).toHaveTextContent('백업을 복원하는 중입니다…')
      fireEvent.keyDown(dialog, { key: 'Escape' })
      expect(screen.getByRole('dialog')).toBeInTheDocument()

      await act(async () => finish(1))
      await expectStatus('백업에서 항목 1개를 복원했습니다.')
    })
  })

  describe('파일 선택 대기 (cancel 이벤트가 없거나 늦은 브라우저)', () => {
    /** 테스트가 끝낼 때까지 기다리는 파일 선택 */
    function controlledPicks() {
      const pending: Array<(file: BackupFileSource | null) => void> = []
      const pick = vi.fn(() => new Promise<BackupFileSource | null>((resolve) => pending.push(resolve)))
      return { pick, pending }
    }

    function renderWithPicks() {
      const picks = controlledPicks()
      const onRestore = vi.fn(async () => 1)
      render(<BackupActions readOnly={false} files={{ save: vi.fn(), pick: picks.pick }} onExport={vi.fn(async () => FILE)} onRestore={onRestore} now={() => NOW} />)
      return picks
    }

    it('선택이 끝나지 않아도 버튼을 막지 않는다', async () => {
      const { pick } = renderWithPicks()

      fireEvent.click(screen.getByRole('button', { name: '백업에서 복원' }))
      await vi.waitFor(() => expect(pick).toHaveBeenCalledOnce())

      expect(screen.getByRole('button', { name: '백업에서 복원' })).toBeEnabled()
      expect(screen.getByRole('button', { name: '백업 내보내기' })).toBeEnabled()
      expect(screen.queryByRole('status')).toBeNull()
    })

    it('늦게 온 선택 결과도 그대로 처리한다', async () => {
      const { pending } = renderWithPicks()

      fireEvent.click(screen.getByRole('button', { name: '백업에서 복원' }))
      await vi.waitFor(() => expect(pending).toHaveLength(1))
      await act(async () => pending[0](source(backupText())))

      expect(await screen.findByRole('dialog', { name: '백업에서 복원' })).toBeInTheDocument()
    })

    it('다시 눌러 새로 고르면 이전 선택 결과는 나중에 와도 무시한다', async () => {
      const { pick, pending } = renderWithPicks()

      fireEvent.click(screen.getByRole('button', { name: '백업에서 복원' }))
      fireEvent.click(screen.getByRole('button', { name: '백업에서 복원' }))
      await vi.waitFor(() => expect(pick).toHaveBeenCalledTimes(2))
      await act(async () => pending[1](source(backupText())))
      const dialog = await screen.findByRole('dialog', { name: '백업에서 복원' })
      await act(async () => pending[0](source('not json')))

      expect(screen.queryByRole('alert')).toBeNull()
      expect(dialog).toBeInTheDocument()
    })

    it('선택을 기다리는 사이 내보내기를 시작하면 늦게 온 선택 결과는 무시한다', async () => {
      const { pending } = renderWithPicks()

      fireEvent.click(screen.getByRole('button', { name: '백업에서 복원' }))
      await vi.waitFor(() => expect(pending).toHaveLength(1))
      fireEvent.click(screen.getByRole('button', { name: '백업 내보내기' }))
      await act(async () => pending[0](source(backupText())))

      expect(screen.getByRole('alertdialog', { name: '백업 내보내기' })).toBeInTheDocument()
      expect(screen.queryByRole('dialog')).toBeNull()
    })
  })
})
