import { act, fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ServerError } from '../../domain/server/serverErrors'
import { SessionExpiredError, WrongMasterPasswordError } from '../../domain/session/vaultSession'
import { RevisionRollbackError } from '../../domain/vault/vaultSync'
import { createFakeSession, fakeVault } from '../../test/fakeSession'
import { SessionRoot } from './SessionRoot'

const EMAIL = 'me@example.com'
const PASSWORD = 'correct horse battery'

function type(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

function fillLogin(password = PASSWORD) {
  type('이메일', EMAIL)
  type('마스터 비밀번호', password)
}

describe('SessionRoot', () => {
  describe('로그인', () => {
    it('로그인하면 세션을 부르고 볼트 화면으로 넘어간다', async () => {
      const { session } = createFakeSession()
      render(<SessionRoot session={session} pageHidden={false} />)

      fillLogin()
      fireEvent.click(screen.getByRole('button', { name: '로그인' }))

      expect(await screen.findByRole('button', { name: '잠그기' })).toBeInTheDocument()
      expect(session.login).toHaveBeenCalledWith(EMAIL, PASSWORD, {})
    })

    it('인증에 실패하면 정책 문구를 보이고 로그인 화면에 머문다', async () => {
      const { session } = createFakeSession()
      session.login.mockRejectedValueOnce(new ServerError(401, 'AUTH_FAILED'))
      render(<SessionRoot session={session} pageHidden={false} />)

      fillLogin()
      fireEvent.click(screen.getByRole('button', { name: '로그인' }))

      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent('로그인 실패')
      expect(alert).toHaveTextContent('이메일 또는 마스터 비밀번호가 올바르지 않습니다.')
    })

    it('처리 중에는 다시 제출해도 한 번만 부른다', async () => {
      const { session } = createFakeSession()
      let finish = () => {}
      session.login.mockImplementationOnce(() => new Promise<void>((resolve) => (finish = resolve)))
      render(<SessionRoot session={session} pageHidden={false} />)

      fillLogin()
      // 버튼이 비활성으로 바뀌기 전의 Enter 연타처럼 폼을 두 번 제출한다
      const form = screen.getByRole('button', { name: '로그인' }).closest('form')!
      fireEvent.submit(form)
      fireEvent.submit(form)
      await act(async () => finish())

      expect(session.login).toHaveBeenCalledOnce()
    })

    it('서버 볼트 되돌림은 확인을 받은 뒤에만 받아들인다', async () => {
      const { session } = createFakeSession()
      session.login.mockRejectedValueOnce(new RevisionRollbackError(5, 3))
      render(<SessionRoot session={session} pageHidden={false} />)

      fillLogin()
      fireEvent.click(screen.getByRole('button', { name: '로그인' }))
      const dialog = await screen.findByRole('alertdialog', { name: '서버 볼트가 이전 상태로 돌아감' })
      // 받아들이지 않는 쪽에 먼저 포커스를 둔다
      expect(screen.getByRole('button', { name: '취소' })).toHaveFocus()
      expect(dialog).toHaveAccessibleDescription(/받아들이지 마세요/)

      fireEvent.click(screen.getByRole('button', { name: '서버 볼트 받아들이기' }))

      await screen.findByRole('button', { name: '잠그기' })
      expect(session.login).toHaveBeenLastCalledWith(EMAIL, PASSWORD, { acceptRollback: true })
    })

    it('되돌림 경고를 취소하면 받아들이지 않는다', async () => {
      const { session } = createFakeSession()
      session.login.mockRejectedValueOnce(new RevisionRollbackError(5, 3))
      render(<SessionRoot session={session} pageHidden={false} />)

      fillLogin()
      fireEvent.click(screen.getByRole('button', { name: '로그인' }))
      fireEvent.click(await screen.findByRole('button', { name: '취소' }))

      expect(screen.queryByText('서버 볼트가 이전 상태로 돌아감')).not.toBeInTheDocument()
      expect(session.login).toHaveBeenCalledOnce()
    })

    it('되돌림 경고가 뜬 채 가입으로 바꾸면 경고를 지운다', async () => {
      const { session } = createFakeSession()
      session.login.mockRejectedValueOnce(new RevisionRollbackError(5, 3))
      render(<SessionRoot session={session} pageHidden={false} />)

      fillLogin()
      fireEvent.click(screen.getByRole('button', { name: '로그인' }))
      await screen.findByRole('alertdialog')
      fireEvent.click(screen.getByRole('button', { name: '계정 만들기' }))

      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    })
  })

  describe('가입', () => {
    function openRegister() {
      fireEvent.click(screen.getByRole('button', { name: '계정 만들기' }))
    }

    it('12자 미만이면 가입을 부르지 않고 정책 문구를 보인다', async () => {
      const { session } = createFakeSession()
      render(<SessionRoot session={session} pageHidden={false} />)
      openRegister()

      type('이메일', EMAIL)
      type('마스터 비밀번호', 'a'.repeat(11))
      type('마스터 비밀번호 확인', 'a'.repeat(11))
      fireEvent.click(screen.getByRole('button', { name: '가입' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('마스터 비밀번호는 12자 이상이어야 합니다.')
      expect(session.register).not.toHaveBeenCalled()
    })

    it('확인 입력이 다르면 가입을 부르지 않는다', async () => {
      const { session } = createFakeSession()
      render(<SessionRoot session={session} pageHidden={false} />)
      openRegister()

      type('이메일', EMAIL)
      type('마스터 비밀번호', PASSWORD)
      type('마스터 비밀번호 확인', `${PASSWORD}!`)
      fireEvent.click(screen.getByRole('button', { name: '가입' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('비밀번호 확인이 일치하지 않습니다.')
      expect(session.register).not.toHaveBeenCalled()
    })

    it('조건을 만족하면 가입하고 볼트 화면으로 넘어간다', async () => {
      const { session } = createFakeSession()
      render(<SessionRoot session={session} pageHidden={false} />)
      openRegister()

      type('이메일', EMAIL)
      type('마스터 비밀번호', PASSWORD)
      type('마스터 비밀번호 확인', PASSWORD)
      fireEvent.click(screen.getByRole('button', { name: '가입' }))

      await screen.findByRole('button', { name: '잠그기' })
      expect(session.register).toHaveBeenCalledWith(EMAIL, PASSWORD)
    })

    it('새 비밀번호 칸은 브라우저가 새 비밀번호로 알아보게 한다', () => {
      const { session } = createFakeSession()
      render(<SessionRoot session={session} pageHidden={false} />)
      openRegister()

      expect(screen.getByLabelText('마스터 비밀번호')).toHaveAttribute('autocomplete', 'new-password')
    })
  })

  describe('잠금 해제', () => {
    it('잠긴 계정의 이메일과 정책 제목을 보인다', () => {
      const { session } = createFakeSession({ status: 'locked', email: EMAIL })
      render(<SessionRoot session={session} pageHidden={false} />)

      expect(screen.getByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()
      expect(screen.getByText(EMAIL)).toBeInTheDocument()
    })

    it('비밀번호가 틀리면 정책 문구를 보인다', async () => {
      const { session } = createFakeSession({ status: 'locked', email: EMAIL })
      session.unlock.mockRejectedValueOnce(new WrongMasterPasswordError())
      render(<SessionRoot session={session} pageHidden={false} />)

      type('마스터 비밀번호', 'wrong')
      fireEvent.click(screen.getByRole('button', { name: '잠금 해제' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('마스터 비밀번호가 올바르지 않습니다.')
    })

    it('서버에 닿지 못해 저장해 둔 볼트로 열었으면 오프라인 안내를 보인다', async () => {
      const { session, control } = createFakeSession({ status: 'locked', email: EMAIL })
      session.unlock.mockImplementationOnce(async () => {
        control.openVault(EMAIL)
        return { offline: true }
      })
      render(<SessionRoot session={session} pageHidden={false} />)

      type('마스터 비밀번호', PASSWORD)
      fireEvent.click(screen.getByRole('button', { name: '잠금 해제' }))

      expect(await screen.findByText('오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다.')).toBeInTheDocument()
    })

    it('로그인이 만료되면 로그인 화면으로 돌아가 이유를 알린다', async () => {
      const { session, control } = createFakeSession({ status: 'locked', email: EMAIL })
      session.unlock.mockImplementationOnce(async () => {
        control.signOut()
        throw new SessionExpiredError()
      })
      render(<SessionRoot session={session} pageHidden={false} />)

      type('마스터 비밀번호', PASSWORD)
      fireEvent.click(screen.getByRole('button', { name: '잠금 해제' }))

      expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument()
      expect(screen.getByRole('status')).toHaveTextContent('로그인이 만료되었습니다. 다시 로그인해 주세요.')

      // 다시 로그인을 시도하면 지난 안내는 지운다
      session.login.mockRejectedValueOnce(new ServerError(401, 'AUTH_FAILED'))
      fillLogin()
      fireEvent.click(screen.getByRole('button', { name: '로그인' }))
      await screen.findByRole('alert')
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    })

    it('잠금 해제가 실패하면 입력한 비밀번호를 지운다', async () => {
      const { session } = createFakeSession({ status: 'locked', email: EMAIL })
      session.unlock.mockRejectedValueOnce(new WrongMasterPasswordError())
      render(<SessionRoot session={session} pageHidden={false} />)

      type('마스터 비밀번호', 'wrong')
      fireEvent.click(screen.getByRole('button', { name: '잠금 해제' }))
      await screen.findByRole('alert')

      expect(screen.getByLabelText('마스터 비밀번호')).toHaveValue('')
    })

    it('잠금 해제 중 서버 볼트 되돌림을 받아들이면 확인 옵션으로 다시 연다', async () => {
      const { session } = createFakeSession({ status: 'locked', email: EMAIL })
      session.unlock.mockRejectedValueOnce(new RevisionRollbackError(5, 3))
      render(<SessionRoot session={session} pageHidden={false} />)

      type('마스터 비밀번호', PASSWORD)
      fireEvent.click(screen.getByRole('button', { name: '잠금 해제' }))
      fireEvent.click(await screen.findByRole('button', { name: '서버 볼트 받아들이기' }))

      await screen.findByRole('button', { name: '잠그기' })
      expect(session.unlock).toHaveBeenLastCalledWith(PASSWORD, { acceptRollback: true })
    })

    it('잠금 화면에서 로그아웃하면 서버 응답을 기다리지 않고 바로 로그인 화면으로 간다', async () => {
      const { session } = createFakeSession({ status: 'locked', email: EMAIL })
      render(<SessionRoot session={session} pageHidden={false} />)

      fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))

      expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument()
    })
  })

  describe('열린 볼트', () => {
    it('잠그기를 누르면 잠금 화면으로 바뀐다', async () => {
      const { session } = createFakeSession({ status: 'unlocked', email: EMAIL, vault: fakeVault() })
      render(<SessionRoot session={session} pageHidden={false} />)

      fireEvent.click(screen.getByRole('button', { name: '잠그기' }))

      expect(await screen.findByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()
      expect(session.lock).toHaveBeenCalledOnce()
    })

    it('자동 잠금처럼 바깥에서 잠겨도 잠금 화면으로 바뀐다', () => {
      const { session, control } = createFakeSession({ status: 'unlocked', email: EMAIL, vault: fakeVault() })
      render(<SessionRoot session={session} pageHidden={false} />)

      act(() => control.closeVault())

      expect(screen.getByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()
    })

    it('서버 로그아웃 요청이 실패해도 이 기기는 로그아웃 화면으로 간다', async () => {
      const { session, control } = createFakeSession({ status: 'unlocked', email: EMAIL, vault: fakeVault() })
      session.logout.mockImplementationOnce(async () => {
        control.signOut()
        throw new ServerError(500, 'INTERNAL_ERROR')
      })
      render(<SessionRoot session={session} pageHidden={false} />)

      fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))

      expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument()
    })

    it('새 버전 앱의 볼트는 읽기 전용 안내를 보인다', () => {
      const { session } = createFakeSession({ status: 'unlocked', email: EMAIL, vault: fakeVault({ readOnly: true }) })
      render(<SessionRoot session={session} pageHidden={false} />)

      expect(screen.getByText('새 버전 앱에서 저장한 볼트입니다. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다.')).toBeInTheDocument()
    })

    it('잠금·로그인 화면도 가려지면 덮는다 (평문으로 펼친 비밀번호 보호)', () => {
      const { session } = createFakeSession({ status: 'locked', email: EMAIL })
      render(<SessionRoot session={session} pageHidden />)

      expect(screen.getByTestId('privacy-cover')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '잠금 해제' })).not.toBeInTheDocument()
    })

    it('페이지가 가려지면 가림 화면으로 볼트 내용을 덮는다', () => {
      const { session } = createFakeSession({ status: 'unlocked', email: EMAIL, vault: fakeVault() })
      const { rerender } = render(<SessionRoot session={session} pageHidden={false} />)
      expect(screen.queryByTestId('privacy-cover')).not.toBeInTheDocument()

      rerender(<SessionRoot session={session} pageHidden />)

      expect(screen.getByTestId('privacy-cover')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '잠그기' })).not.toBeInTheDocument()
    })
  })
})
