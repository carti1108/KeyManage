import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { addItem, deleteItem, emptyVault, type VaultData } from '../../domain/vault/vaultData'
import { VaultTooLargeError, type SyncedVault } from '../../domain/vault/vaultSync'
import { VaultScreen } from './VaultScreen'

const NOW = new Date('2026-10-31T00:00:00Z')

function sampleData(): VaultData {
  let data = emptyVault(NOW)
  data = addItem(data, { serviceName: 'GitHub', username: 'octo', password: 'gh-secret', url: 'javascript:alert(1)', notes: '회사 계정' }, NOW, 'gh')
  data = addItem(data, { serviceName: 'Apple', username: 'tim', password: 'apple-secret' }, NOW, 'ap')
  // 새 버전 앱이 넣은 필드. 화면에서 고쳐도 남아야 한다
  return { ...data, items: data.items.map((item) => (item.id === 'gh' ? { ...item, totp: 'FUTURE' } : item)) }
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
  beforeEach(() => vi.stubGlobal('CSS', { supports: (property: string) => property === '-webkit-text-security' }))
  afterEach(() => vi.unstubAllGlobals())

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

    it('상세를 열면 제목에 포커스를 두고, 목록으로 돌아오면 보던 항목에 포커스를 돌려준다', () => {
      setup()
      openItem('GitHub')
      expect(screen.getByRole('heading', { name: 'GitHub' })).toHaveFocus()

      fireEvent.click(screen.getByRole('button', { name: '목록으로' }))

      expect(screen.getByRole('list', { name: '항목 목록' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /GitHub/ })).toHaveFocus()
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

    it('브라우저가 다른 사이트 자격 증명을 이 앱의 로그인으로 저장하지 않도록 폼 제출로 다루지 않는다', () => {
      setup()

      fireEvent.click(screen.getByRole('button', { name: '항목 추가' }))

      const password = screen.getByLabelText('비밀번호')
      expect(password.closest('form')).toBeNull()
      // 비밀번호 칸(type=password)이 있으면 Chrome이 저장을 제안하므로 CSS로 가린다
      expect(password).toHaveAttribute('type', 'text')
      expect(password).toHaveAttribute('autocomplete', 'off')
      expect(password).toHaveAttribute('data-1p-ignore')
      expect(password).toHaveAttribute('data-lpignore', 'true')
    })

    it('Enter로도 저장하고, 연달아 눌러도 한 번만 저장한다', async () => {
      const { onSave } = setup()
      let finish = () => {}
      onSave.mockImplementationOnce(() => new Promise<void>((resolve) => (finish = resolve)))

      fireEvent.click(screen.getByRole('button', { name: '항목 추가' }))
      type('서비스 이름', 'Naver')
      fireEvent.keyDown(screen.getByLabelText('서비스 이름'), { key: 'Enter' })
      fireEvent.keyDown(screen.getByLabelText('서비스 이름'), { key: 'Enter' })
      await act(async () => finish())

      expect(onSave).toHaveBeenCalledOnce()
    })

    it('메모의 Enter는 줄바꿈이라 저장하지 않는다', () => {
      const { onSave } = setup()

      fireEvent.click(screen.getByRole('button', { name: '항목 추가' }))
      type('서비스 이름', 'Naver')
      fireEvent.keyDown(screen.getByLabelText('메모'), { key: 'Enter' })

      expect(onSave).not.toHaveBeenCalled()
    })

    it('아이디·URL·메모는 맞춤법 검사로 외부에 보내지 않는다', () => {
      setup()

      fireEvent.click(screen.getByRole('button', { name: '항목 추가' }))

      for (const label of ['아이디', 'URL', '메모']) {
        expect(screen.getByLabelText(label)).toHaveAttribute('spellcheck', 'false')
      }
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
      expect(edited).toMatchObject({ serviceName: 'GitHub', username: 'octocat', password: 'gh-secret', notes: '회사 계정', totp: 'FUTURE' })
    })

    it('비어 있던 선택 필드는 빈 문자열로 만들지 않는다', async () => {
      const { savedData } = setup()
      openItem('Apple')

      fireEvent.click(screen.getByRole('button', { name: '수정' }))
      type('아이디', 'timcook')
      fireEvent.click(screen.getByRole('button', { name: '저장' }))

      await screen.findByText('timcook')
      const edited = savedData().items.find((item) => item.id === 'ap')
      expect(edited).not.toHaveProperty('url')
      expect(edited).not.toHaveProperty('notes')
    })

    it('지운 선택 필드는 필드째 없앤다', async () => {
      const { savedData } = setup()
      openItem('GitHub')

      fireEvent.click(screen.getByRole('button', { name: '수정' }))
      type('URL', '')
      fireEvent.click(screen.getByRole('button', { name: '저장' }))

      await screen.findByRole('heading', { name: 'GitHub' })
      expect(savedData().items.find((item) => item.id === 'gh')).not.toHaveProperty('url')
    })

    it('바뀐 것이 없으면 저장하지 않는다. 다른 기기의 변경을 덮어쓰지 않게 하기 위해서다', async () => {
      const { onSave } = setup()
      openItem('GitHub')

      fireEvent.click(screen.getByRole('button', { name: '수정' }))
      fireEvent.click(screen.getByRole('button', { name: '저장' }))

      expect(await screen.findByRole('heading', { name: 'GitHub' })).toBeInTheDocument()
      expect(onSave).not.toHaveBeenCalled()
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

    it('삭제하는 동안에는 다른 화면으로 가지 못하고, 실패하면 이유를 보인다', async () => {
      const { onSave } = setup()
      let fail = (_error: Error) => {}
      onSave.mockImplementationOnce(() => new Promise<void>((_, reject) => (fail = reject)))
      openItem('GitHub')

      fireEvent.click(screen.getByRole('button', { name: '삭제' }))
      fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }))

      expect(screen.getByRole('button', { name: '목록으로' })).toBeDisabled()
      expect(screen.getByRole('button', { name: '수정' })).toBeDisabled()

      await act(async () => fail(new VaultTooLargeError(6_000_000)))

      expect(screen.getByRole('alert')).toHaveTextContent('저장 실패')
      expect(screen.getByRole('heading', { name: 'GitHub' })).toBeInTheDocument()
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

    // 동기화는 항목을 빼지 않고 삭제 표시만 남긴다
    const synced = deleteItem(initial, 'gh', NOW)
    rerender(<VaultScreen vault={{ revision: 2, data: synced, readOnly: false, payload: null }} onSave={vi.fn()} />)

    expect(screen.getByRole('list', { name: '항목 목록' })).toBeInTheDocument()
  })
})
