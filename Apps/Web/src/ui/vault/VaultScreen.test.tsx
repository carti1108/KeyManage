import { fireEvent, render, screen, within } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { addItem, emptyVault, type VaultData } from '../../domain/vault/vaultData'
import { VaultTooLargeError, type SyncedVault } from '../../domain/vault/vaultSync'
import { VaultScreen } from './VaultScreen'

const NOW = new Date('2026-10-31T00:00:00Z')

function sampleData(): VaultData {
  let data = emptyVault(NOW)
  data = addItem(data, { serviceName: 'GitHub', username: 'octo', password: 'gh-secret', url: 'javascript:alert(1)', notes: '회사 계정' }, NOW, 'gh')
  data = addItem(data, { serviceName: 'Apple', username: 'tim', password: 'apple-secret' }, NOW, 'ap')
  return data
}

// 실제 화면처럼 저장이 끝나면 새 볼트를 내려 주는 부모
function Harness({ initial, readOnly = false, onSave }: { initial: VaultData; readOnly?: boolean; onSave: (data: VaultData) => Promise<void> }) {
  const [vault, setVault] = useState<SyncedVault>({ revision: 1, data: initial, readOnly, payload: null })
  return (
    <VaultScreen
      vault={vault}
      now={() => NOW}
      onSave={async (data) => {
        await onSave(data)
        setVault((current) => ({ ...current, revision: current.revision + 1, data }))
      }}
    />
  )
}

function setup(options: { initial?: VaultData; readOnly?: boolean } = {}) {
  const onSave = vi.fn(async (_data: VaultData) => {})
  render(<Harness initial={options.initial ?? sampleData()} readOnly={options.readOnly} onSave={onSave} />)
  return { onSave, savedData: () => onSave.mock.lastCall![0] }
}

function type(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

function openItem(name: string) {
  fireEvent.click(screen.getByRole('button', { name: new RegExp(name) }))
}

describe('VaultScreen', () => {
  describe('목록', () => {
    it('항목을 이름순으로 보여 준다', () => {
      setup()

      const names = within(screen.getByRole('list', { name: '항목 목록' }))
        .getAllByRole('listitem')
        .map((item) => item.textContent)
      expect(names[0]).toContain('Apple')
      expect(names[1]).toContain('GitHub')
    })

    it('검색어로 거른다', () => {
      setup()

      type('항목 검색', 'git')

      expect(screen.queryByRole('button', { name: /Apple/ })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: /GitHub/ })).toBeInTheDocument()
    })

    it('항목이 없으면 안내한다', () => {
      setup({ initial: emptyVault(NOW) })

      expect(screen.getByText('저장된 항목이 없습니다.')).toBeInTheDocument()
    })

    it('검색 결과가 없으면 안내한다', () => {
      setup()

      type('항목 검색', 'zzz')

      expect(screen.getByText('검색 결과가 없습니다.')).toBeInTheDocument()
    })
  })

  describe('상세', () => {
    it('비밀번호는 가려서 보이고 URL은 눌러서 열리는 링크로 만들지 않는다', () => {
      setup()

      openItem('GitHub')

      expect(screen.getByRole('heading', { name: 'GitHub' })).toBeInTheDocument()
      expect(screen.getByText('octo')).toBeInTheDocument()
      expect(screen.getByLabelText('비밀번호')).toHaveAttribute('type', 'password')
      expect(screen.getByText('javascript:alert(1)')).toBeInTheDocument()
      expect(screen.queryByRole('link')).not.toBeInTheDocument()
      expect(screen.getByText('회사 계정')).toBeInTheDocument()
    })

    it('목록으로 돌아갈 수 있다', () => {
      setup()
      openItem('GitHub')

      fireEvent.click(screen.getByRole('button', { name: '목록으로' }))

      expect(screen.getByRole('list', { name: '항목 목록' })).toBeInTheDocument()
    })
  })

  describe('추가', () => {
    it('입력한 항목을 저장하고 그 항목의 상세로 간다', async () => {
      const { onSave, savedData } = setup()

      fireEvent.click(screen.getByRole('button', { name: '항목 추가' }))
      type('서비스 이름', '  Naver  ')
      type('아이디', 'me')
      type('비밀번호', 'naver-secret')
      fireEvent.click(screen.getByRole('button', { name: '저장' }))

      expect(await screen.findByRole('heading', { name: 'Naver' })).toBeInTheDocument()
      expect(onSave).toHaveBeenCalledOnce()
      const added = savedData().items.find((item) => item.serviceName === 'Naver')
      expect(added).toMatchObject({ username: 'me', password: 'naver-secret' })
    })

    it('서비스 이름이 없으면 저장하지 않고 정책 문구를 보인다', async () => {
      const { onSave } = setup()

      fireEvent.click(screen.getByRole('button', { name: '항목 추가' }))
      type('서비스 이름', '   ')
      fireEvent.click(screen.getByRole('button', { name: '저장' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('서비스 이름을 입력해 주세요.')
      expect(onSave).not.toHaveBeenCalled()
    })

    it('저장에 실패하면 입력을 유지한 채 정책 문구를 보인다', async () => {
      const { onSave } = setup()
      onSave.mockRejectedValueOnce(new VaultTooLargeError(6_000_000))

      fireEvent.click(screen.getByRole('button', { name: '항목 추가' }))
      type('서비스 이름', 'Naver')
      fireEvent.click(screen.getByRole('button', { name: '저장' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('볼트 허용 용량(5MB)을 초과했거나')
      expect(screen.getByLabelText('서비스 이름')).toHaveValue('Naver')
    })

    it('취소하면 저장하지 않고 목록으로 돌아간다', () => {
      const { onSave } = setup()

      fireEvent.click(screen.getByRole('button', { name: '항목 추가' }))
      fireEvent.click(screen.getByRole('button', { name: '취소' }))

      expect(screen.getByRole('list', { name: '항목 목록' })).toBeInTheDocument()
      expect(onSave).not.toHaveBeenCalled()
    })

    it('저장한 비밀번호를 브라우저가 로그인 비밀번호로 기억하지 않게 한다', () => {
      setup()

      fireEvent.click(screen.getByRole('button', { name: '항목 추가' }))

      expect(screen.getByLabelText('비밀번호')).toHaveAttribute('autocomplete', 'off')
    })
  })

  describe('수정', () => {
    it('기존 값을 채운 폼에서 고친 값만 바꿔 저장한다', async () => {
      const { savedData } = setup()
      openItem('GitHub')

      fireEvent.click(screen.getByRole('button', { name: '수정' }))
      expect(screen.getByLabelText('아이디')).toHaveValue('octo')
      type('아이디', 'octocat')
      fireEvent.click(screen.getByRole('button', { name: '저장' }))

      expect(await screen.findByText('octocat')).toBeInTheDocument()
      const edited = savedData().items.find((item) => item.id === 'gh')
      expect(edited).toMatchObject({ serviceName: 'GitHub', username: 'octocat', password: 'gh-secret', notes: '회사 계정' })
    })
  })

  describe('삭제', () => {
    it('확인을 받은 뒤에만 지우고 목록으로 돌아간다', async () => {
      const { onSave, savedData } = setup()
      openItem('GitHub')

      fireEvent.click(screen.getByRole('button', { name: '삭제' }))
      const dialog = screen.getByRole('alertdialog', { name: '항목 삭제' })
      expect(dialog).toHaveAccessibleDescription('이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다.')
      expect(within(dialog).getByRole('button', { name: '취소' })).toHaveFocus()
      expect(onSave).not.toHaveBeenCalled()

      fireEvent.click(within(dialog).getByRole('button', { name: '삭제' }))

      expect(await screen.findByRole('list', { name: '항목 목록' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /GitHub/ })).not.toBeInTheDocument()
      expect(savedData().items.find((item) => item.id === 'gh')?.deletedAt).toBeDefined()
    })

    it('확인 창에서 취소하면 지우지 않는다', () => {
      const { onSave } = setup()
      openItem('GitHub')

      fireEvent.click(screen.getByRole('button', { name: '삭제' }))
      fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '취소' }))

      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
      expect(onSave).not.toHaveBeenCalled()
    })
  })

  it('읽기 전용 볼트는 추가·수정·삭제를 보이지 않는다', () => {
    setup({ readOnly: true })

    expect(screen.queryByRole('button', { name: '항목 추가' })).not.toBeInTheDocument()
    openItem('GitHub')
    expect(screen.queryByRole('button', { name: '수정' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
  })

  it('보고 있던 항목이 다른 기기에서 지워지면 목록으로 돌아간다', () => {
    const initial = sampleData()
    const { rerender } = render(<VaultScreen vault={{ revision: 1, data: initial, readOnly: false, payload: null }} onSave={vi.fn()} />)
    openItem('GitHub')

    const withoutGitHub = { ...initial, items: initial.items.filter((item) => item.id !== 'gh') }
    rerender(<VaultScreen vault={{ revision: 2, data: withoutGitHub, readOnly: false, payload: null }} onSave={vi.fn()} />)

    expect(screen.getByRole('list', { name: '항목 목록' })).toBeInTheDocument()
  })
})
