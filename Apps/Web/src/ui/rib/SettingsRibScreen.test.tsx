import { act, fireEvent, render, screen, within } from '@testing-library/react'
import type {
  WebChangePasswordDialogText,
  WebConfirmText,
  WebDeleteAccountDialogText,
  WebRestoreDialogText,
  WebSettingsState,
  WebSettingsView,
} from 'keymanage-web-session'
import { describe, expect, it, vi } from 'vitest'
import {
  asState,
  CHANGE_PASSWORD_DIALOG_TEXT,
  DELETE_ACCOUNT_DIALOG_TEXT,
  EXPORT_NOTICE_TEXT,
  RESTORE_DIALOG_TEXT,
  SETTINGS_SECURITY_POLICY_TEXT,
  withFields,
} from './policyTextFixtures'
import { SettingsRibScreen } from './SettingsRibScreen'

type Plain = Omit<WebSettingsState, 'copy' | 'hashCode' | 'equals'>

const BASE: Plain = {
  email: 'user@example.com',
  serverAddress: 'https://keymanage.example.com',
  syncOffline: false,
  syncStatus: '최신 상태 (동기화 완료)',
  readOnly: false,
  accountOffline: false,
  autoLockOptionsMs: [0, 60_000, 300_000, 900_000, 3_600_000],
  autoLockTimeoutMs: 300_000,
  modal: 'none',
  inProgress: false,
  restorePassword: '',
  currentPassword: '',
  newPassword: '',
  passwordConfirmation: '',
  deletionPassword: '',
  deletionPhrase: '',
  error: null,
  notice: null,
  noticeKind: 'info',
  canExportBackup: true,
  canRestoreBackup: true,
  canChangePassword: true,
  canDeleteAccount: true,
  canStartChangePassword: true,
  canStartDeleteAccount: true,
  canSubmitRestore: false,
  canSubmitChangePassword: false,
  canSubmitDeletion: false,
  securityPolicy: SETTINGS_SECURITY_POLICY_TEXT,
  exportNotice: EXPORT_NOTICE_TEXT,
  restoreDialog: RESTORE_DIALOG_TEXT,
  changePasswordDialog: CHANGE_PASSWORD_DIALOG_TEXT,
  deleteAccountDialog: DELETE_ACCOUNT_DIALOG_TEXT,
}

function fakeStore<S>(initial: S) {
  let snapshot = initial
  const listeners = new Set<() => void>()
  return {
    subscribe: (listener: () => void) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    getSnapshot: () => snapshot,
    set(next: S) {
      snapshot = next
      listeners.forEach((listener) => listener())
    },
  }
}

function setup(overrides: Partial<Plain> = {}) {
  const store = fakeStore<Plain>({ ...BASE, ...overrides })
  const view = {
    store,
    selectAutoLock: vi.fn(),
    requestLock: vi.fn(),
    requestLogout: vi.fn(),
    startExport: vi.fn(),
    confirmExport: vi.fn(),
    startRestore: vi.fn(),
    editRestorePassword: vi.fn(),
    submitRestore: vi.fn(),
    startChangePassword: vi.fn(),
    editCurrentPassword: vi.fn(),
    editNewPassword: vi.fn(),
    editPasswordConfirmation: vi.fn(),
    submitChangePassword: vi.fn(),
    startDeleteAccount: vi.fn(),
    editDeletionPassword: vi.fn(),
    editDeletionPhrase: vi.fn(),
    submitDeleteAccount: vi.fn(),
    cancelModal: vi.fn(),
    dismissMessage: vi.fn(),
  }
  render(<SettingsRibScreen view={view as unknown as WebSettingsView} />)
  return { view, update: (next: Partial<Plain>) => act(() => store.set({ ...store.getSnapshot(), ...next })) }
}

describe('SettingsRibScreen', () => {
  describe('정보', () => {
    it('이메일·동기화 상태·서버 주소와 고정 보안 정책을 보인다', () => {
      setup()

      expect(screen.getByRole('heading', { name: '설정' })).toBeInTheDocument()
      expect(screen.getByText('user@example.com')).toBeInTheDocument()
      expect(screen.getByText('최신 상태 (동기화 완료)')).toBeInTheDocument()
      expect(screen.getByText('https://keymanage.example.com')).toBeInTheDocument()
      expect(screen.getByText('30초 (고정, COM-001)')).toBeInTheDocument()
    })

    it('오프라인이면 동기화 상태를 오프라인으로 보인다', () => {
      setup({ syncOffline: true, syncStatus: '오프라인' })
      expect(screen.getByText('오프라인')).toBeInTheDocument()
    })

    it('동기화 상태와 보안 정책 줄 문구는 화면이 아니라 State 값을 그대로 그린다', () => {
      setup({
        syncStatus: '상태 문구 샘플',
        securityPolicy: asState({ clipboardLabel: '이름 가', clipboardValue: '값 가', encryptionLabel: '이름 나', encryptionValue: '값 나' }),
      })

      expect(screen.getByText('상태 문구 샘플')).toBeInTheDocument()
      expect(screen.getByText('이름 가')).toBeInTheDocument()
      expect(screen.getByText('값 가')).toBeInTheDocument()
      expect(screen.getByText('이름 나')).toBeInTheDocument()
      expect(screen.getByText('값 나')).toBeInTheDocument()
      expect(screen.queryByText('30초 (고정, COM-001)')).not.toBeInTheDocument()
    })

    it('서버 주소가 비어 있으면 현재 출처를 보인다', () => {
      setup({ serverAddress: '' })
      expect(screen.getByText(window.location.origin)).toBeInTheDocument()
    })
  })

  describe('세션 제어와 자동 잠금', () => {
    it('잠그기·로그아웃을 뷰에 보낸다', () => {
      const { view } = setup()

      fireEvent.click(screen.getByRole('button', { name: '잠그기' }))
      fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))

      expect(view.requestLock).toHaveBeenCalledTimes(1)
      expect(view.requestLogout).toHaveBeenCalledTimes(1)
    })

    it('현재 자동 잠금 시간을 보이고 바꾸면 밀리초로 보낸다', () => {
      const { view } = setup()
      const select = screen.getByLabelText('자동 잠금')
      expect(select).toHaveValue('300000')

      fireEvent.change(select, { target: { value: '60000' } })

      expect(view.selectAutoLock).toHaveBeenCalledWith(60_000)
    })
  })

  describe('백업', () => {
    it('내보내기·복원 버튼은 각각 시작을 보낸다', () => {
      const { view } = setup()

      fireEvent.click(screen.getByRole('button', { name: '백업 내보내기' }))
      fireEvent.click(screen.getByRole('button', { name: '백업에서 복원' }))

      expect(view.startExport).toHaveBeenCalledTimes(1)
      expect(view.startRestore).toHaveBeenCalledTimes(1)
    })

    it('읽기 전용 볼트도 내보낼 수 있지만 복원은 막는다', () => {
      setup({ readOnly: true, canRestoreBackup: false })
      expect(screen.getByRole('button', { name: '백업 내보내기' })).toBeEnabled()
      expect(screen.getByRole('button', { name: '백업에서 복원' })).toBeDisabled()
    })

    it('내보내기 안내를 정책 문구 그대로 보이고 확인·취소를 보낸다', () => {
      const { view } = setup({ modal: 'exportConfirm' })
      const prompt = screen.getByRole('alertdialog', { name: '백업 내보내기' })
      expect(prompt).toHaveTextContent('이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다.')

      fireEvent.click(within(prompt).getByRole('button', { name: '내보내기' }))
      expect(view.confirmExport).toHaveBeenCalledTimes(1)

      fireEvent.click(within(prompt).getByRole('button', { name: '취소' }))
      expect(view.cancelModal).toHaveBeenCalledTimes(1)
    })

    it('복원 대화상자는 비밀번호 입력과 제출을 보낸다', () => {
      const { view, update } = setup({ modal: 'restorePassword' })
      const dialog = screen.getByRole('dialog', { name: '백업에서 복원' })
      expect(dialog).toHaveTextContent('백업에 있던 항목은 백업 시점 내용으로 돌아갑니다.')
      expect(within(dialog).getByLabelText('백업 파일 비밀번호')).toHaveFocus()
      expect(within(dialog).getByRole('button', { name: '복원' })).toBeDisabled()

      fireEvent.change(within(dialog).getByLabelText('백업 파일 비밀번호'), { target: { value: 'pw' } })
      expect(view.editRestorePassword).toHaveBeenCalledWith('pw')

      update({ restorePassword: 'pw', canSubmitRestore: true })
      fireEvent.click(within(dialog).getByRole('button', { name: '복원' }))
      expect(view.submitRestore).toHaveBeenCalledTimes(1)

      fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))
      expect(view.cancelModal).toHaveBeenCalledTimes(1)
    })

    it('복원 중에는 진행을 status로 알리고 입력을 막는다', () => {
      setup({ modal: 'restorePassword', inProgress: true, restorePassword: 'pw', canSubmitRestore: false })
      const dialog = screen.getByRole('dialog', { name: '백업에서 복원' })

      expect(within(dialog).getByRole('status')).toHaveTextContent('백업을 복원하는 중입니다')
      expect(within(dialog).getByLabelText('백업 파일 비밀번호')).toBeDisabled()
      expect(within(dialog).getByRole('button', { name: '취소' })).toBeDisabled()
    })

    it('복원 대화상자의 오류는 대화상자 안에 보인다', () => {
      setup({ modal: 'restorePassword', error: '백업을 열 수 없습니다.' })
      expect(within(screen.getByRole('dialog', { name: '백업에서 복원' })).getByRole('alert')).toHaveTextContent('백업을 열 수 없습니다.')
    })

    it('Esc로 복원 대화상자를 닫는다', () => {
      const { view } = setup({ modal: 'restorePassword' })
      fireEvent.keyDown(screen.getByRole('dialog', { name: '백업에서 복원' }), { key: 'Escape' })
      expect(view.cancelModal).toHaveBeenCalledTimes(1)
    })
  })

  describe('마스터 비밀번호 변경', () => {
    it('진입 버튼은 시작을 보낸다', () => {
      const { view } = setup()
      fireEvent.click(screen.getByRole('button', { name: '마스터 비밀번호 변경' }))
      expect(view.startChangePassword).toHaveBeenCalledTimes(1)
    })

    it('읽기 전용 볼트에서는 진입점을 막는다', () => {
      setup({ readOnly: true, canChangePassword: false, canStartChangePassword: false })
      expect(screen.getByRole('button', { name: '마스터 비밀번호 변경' })).toBeDisabled()
    })

    it('오프라인이어도 누를 수 있어 안내를 받는다', () => {
      setup({ accountOffline: true, canChangePassword: false })
      expect(screen.getByRole('button', { name: '마스터 비밀번호 변경' })).toBeEnabled()
    })

    it('안내를 정책 문구 그대로 보이고 입력 세 칸을 각각 보낸다', () => {
      const { view } = setup({ modal: 'changePassword' })
      const dialog = screen.getByRole('dialog', { name: '마스터 비밀번호 변경' })
      expect(dialog).toHaveAttribute('aria-modal', 'true')
      expect(dialog).toHaveTextContent('다른 기기는 모두 로그아웃되고')
      expect(within(dialog).getByLabelText('현재 마스터 비밀번호')).toHaveFocus()

      fireEvent.change(within(dialog).getByLabelText('현재 마스터 비밀번호'), { target: { value: 'a' } })
      fireEvent.change(within(dialog).getByLabelText('새 마스터 비밀번호'), { target: { value: 'b' } })
      fireEvent.change(within(dialog).getByLabelText('새 마스터 비밀번호 확인'), { target: { value: 'c' } })

      expect(view.editCurrentPassword).toHaveBeenCalledWith('a')
      expect(view.editNewPassword).toHaveBeenCalledWith('b')
      expect(view.editPasswordConfirmation).toHaveBeenCalledWith('c')
    })

    it('변경 전 안내 본문을 정책 문구 그대로 전부 보이고 대화상자 설명으로 읽어 준다 (SEC-004)', () => {
      setup({ modal: 'changePassword' })
      const dialog = screen.getByRole('dialog', { name: '마스터 비밀번호 변경' })
      const body =
        '비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다. 예전에 내보낸 백업은 예전 비밀번호로 엽니다.'

      expect(dialog).toHaveTextContent(body)
      expect(dialog).toHaveAccessibleDescription(body)
    })

    it('제출 가능할 때만 변경 버튼이 켜지고 제출은 새로 고침 없이 보낸다', () => {
      const { view, update } = setup({ modal: 'changePassword' })
      expect(screen.getByRole('button', { name: '변경' })).toBeDisabled()

      update({ canSubmitChangePassword: true })
      const form = screen.getByRole('button', { name: '변경' }).closest('form')!
      expect(fireEvent.submit(form)).toBe(false)

      expect(view.submitChangePassword).toHaveBeenCalledTimes(1)
    })

    it('바꾸는 동안 진행 문구를 보이고 입력을 막는다', () => {
      setup({ modal: 'changePassword', inProgress: true })
      expect(screen.getByRole('status')).toHaveTextContent('마스터 비밀번호를 바꾸는 중입니다')
      expect(screen.getByLabelText('현재 마스터 비밀번호')).toBeDisabled()
    })

    it('취소와 Esc는 모달 닫기를 보낸다', () => {
      const { view } = setup({ modal: 'changePassword' })
      const dialog = screen.getByRole('dialog', { name: '마스터 비밀번호 변경' })

      fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))
      fireEvent.keyDown(dialog, { key: 'Escape' })

      expect(view.cancelModal).toHaveBeenCalledTimes(2)
    })

    it('열린 동안 배경을 inert로 막고 닫히면 풀며, 닫히면 진입 버튼에 포커스를 돌린다', () => {
      const { update } = setup({ modal: 'changePassword' })
      const dialog = screen.getByRole('dialog', { name: '마스터 비밀번호 변경' })
      expect(dialog.closest('[inert]')).toBeNull()
      expect(document.body.querySelector('[inert]')).not.toBeNull()

      update({ modal: 'none' })

      expect(document.body.querySelector('[inert]')).toBeNull()
      expect(screen.getByRole('button', { name: '마스터 비밀번호 변경' })).toHaveFocus()
    })

    it('실패하면 오류를 대화상자 안에 보이고 현재 비밀번호 칸에 포커스를 둔다', () => {
      const { update } = setup({ modal: 'changePassword', inProgress: true })
      update({ inProgress: false, error: '비밀번호를 확인해 주세요.' })

      const dialog = screen.getByRole('dialog', { name: '마스터 비밀번호 변경' })
      expect(within(dialog).getByRole('alert')).toHaveTextContent('비밀번호를 확인해 주세요.')
      expect(within(dialog).getByLabelText('현재 마스터 비밀번호')).toHaveFocus()
    })
  })

  describe('계정 삭제', () => {
    it('진입 버튼은 시작을 보낸다', () => {
      const { view } = setup()
      fireEvent.click(screen.getByRole('button', { name: '계정 삭제' }))
      expect(view.startDeleteAccount).toHaveBeenCalledTimes(1)
    })

    it('오프라인이어도 누를 수 있어 안내를 받는다', () => {
      setup({ accountOffline: true, canDeleteAccount: false })
      expect(screen.getByRole('button', { name: '계정 삭제' })).toBeEnabled()
    })

    it('삭제 전 안내를 정책 문구 그대로 보이고 확인 문구 칸의 이름도 정책 문구다', () => {
      setup({ modal: 'deleteAccount' })
      const dialog = screen.getByRole('dialog', { name: '계정 삭제' })

      expect(dialog).toHaveAttribute('aria-modal', 'true')
      expect(dialog).toHaveTextContent('계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다.')
      expect(within(dialog).getByLabelText("확인을 위해 '계정 삭제'를 입력해 주세요.")).toBeInTheDocument()
      expect(within(dialog).getByLabelText('마스터 비밀번호')).toHaveFocus()
    })

    it('비밀번호와 확인 문구 입력을 보내고, 제출 가능할 때만 삭제 버튼이 켜진다', () => {
      const { view, update } = setup({ modal: 'deleteAccount' })
      const dialog = screen.getByRole('dialog', { name: '계정 삭제' })
      expect(within(dialog).getByRole('button', { name: '삭제' })).toBeDisabled()

      fireEvent.change(within(dialog).getByLabelText('마스터 비밀번호'), { target: { value: 'pw' } })
      fireEvent.change(within(dialog).getByLabelText("확인을 위해 '계정 삭제'를 입력해 주세요."), { target: { value: '계정 삭제' } })
      expect(view.editDeletionPassword).toHaveBeenCalledWith('pw')
      expect(view.editDeletionPhrase).toHaveBeenCalledWith('계정 삭제')

      update({ canSubmitDeletion: true })
      expect(within(dialog).getByRole('button', { name: '삭제' })).toBeEnabled()
      const form = within(dialog).getByRole('button', { name: '삭제' }).closest('form')!
      expect(fireEvent.submit(form)).toBe(false)
      expect(view.submitDeleteAccount).toHaveBeenCalledTimes(1)
    })

    it('삭제 전 안내 본문을 정책 문구 그대로 전부 보이고 대화상자 설명으로 읽어 준다 (SEC-005)', () => {
      setup({ modal: 'deleteAccount' })
      const dialog = screen.getByRole('dialog', { name: '계정 삭제' })
      const body =
        '계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다. 필요하면 먼저 백업을 내보내 주세요.'

      expect(dialog).toHaveTextContent(body)
      expect(dialog).toHaveAccessibleDescription(body)
    })

    it('확인 문구가 일치해 상태가 제출 가능이면 삭제 버튼이 켜지고, 아니면 입력해 둔 문구가 있어도 꺼져 있다 (SEC-005)', () => {
      // 일치 여부(앞뒤 공백 포함)는 Kotlin이 판정하고 화면은 그 결과(canSubmitDeletion)만 따른다
      const { update } = setup({ modal: 'deleteAccount', deletionPassword: 'pw', deletionPhrase: '계정 삭제 ', canSubmitDeletion: false })
      const dialog = screen.getByRole('dialog', { name: '계정 삭제' })
      expect(within(dialog).getByRole('button', { name: '삭제' })).toBeDisabled()

      update({ deletionPhrase: '계정 삭제', canSubmitDeletion: true })

      expect(within(dialog).getByLabelText("확인을 위해 '계정 삭제'를 입력해 주세요.")).toHaveValue('계정 삭제')
      expect(within(dialog).getByRole('button', { name: '삭제' })).toBeEnabled()
    })

    // 정책 SEC-005: 자모가 분리된(NFD) 확인 문구도 NFC로 맞춘 뒤 비교한다. 입력 칸이 맞춰 보낸다
    it('입력 칸이 자모가 분리된(NFD) 확인 문구를 NFC로 맞춰 뷰에 보낸다 (SEC-005)', () => {
      const { view } = setup({ modal: 'deleteAccount' })
      const dialog = screen.getByRole('dialog', { name: '계정 삭제' })
      const decomposed = '계정 삭제'.normalize('NFD')
      expect(decomposed).not.toBe('계정 삭제')

      fireEvent.change(within(dialog).getByLabelText("확인을 위해 '계정 삭제'를 입력해 주세요."), { target: { value: decomposed } })

      expect(view.editDeletionPhrase).toHaveBeenCalledWith('계정 삭제')
    })

    it('지우는 동안 진행 문구를 보이고 입력을 막는다', () => {
      setup({ modal: 'deleteAccount', inProgress: true })
      expect(screen.getByRole('status')).toHaveTextContent('계정을 삭제하는 중입니다')
      expect(screen.getByLabelText('마스터 비밀번호')).toBeDisabled()
    })

    it('취소는 모달 닫기를 보내고, 닫히면 진입 버튼에 포커스를 돌린다', () => {
      const { view, update } = setup({ modal: 'deleteAccount' })
      fireEvent.click(within(screen.getByRole('dialog', { name: '계정 삭제' })).getByRole('button', { name: '취소' }))
      expect(view.cancelModal).toHaveBeenCalledTimes(1)

      update({ modal: 'none' })
      expect(screen.getByRole('button', { name: '계정 삭제' })).toHaveFocus()
    })
  })

  describe('오류와 안내', () => {
    it('모달이 없으면 오류를 버튼 아래 경고로 보인다', () => {
      setup({ error: '파일을 읽지 못했습니다.' })
      expect(screen.getByRole('alert')).toHaveTextContent('파일을 읽지 못했습니다.')
    })

    it('완료·오프라인 안내를 status로 보인다', () => {
      setup({ notice: '마스터 비밀번호를 바꿨습니다.' })
      expect(screen.getByRole('status')).toHaveTextContent('마스터 비밀번호를 바꿨습니다.')
    })

    it('아무것도 없으면 알림을 그리지 않는다', () => {
      setup()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    })
  })
})

// 문구와 오프라인 판단은 Kotlin State가 한다. 화면이 직접 들고 있지 않다면 State 값을 바꿨을 때 그대로 따라간다
describe('SettingsRibScreen 진입 버튼은 State의 진입 가능 여부만 본다', () => {
  it('오프라인 여부와 상관없이 비밀번호 변경 진입 가능 값을 따른다', () => {
    setup({ accountOffline: true, canStartChangePassword: false })
    expect(screen.getByRole('button', { name: '마스터 비밀번호 변경' })).toBeDisabled()
  })

  it('실제 변경 가능 값(canChangePassword)이 꺼져 있어도 진입 가능 값이 켜져 있으면 누를 수 있다', () => {
    setup({ canChangePassword: false, canStartChangePassword: true })
    expect(screen.getByRole('button', { name: '마스터 비밀번호 변경' })).toBeEnabled()
  })

  it('계정 삭제 진입 버튼도 진입 가능 값만 따른다', () => {
    const { update } = setup({ accountOffline: true, canStartDeleteAccount: false })
    expect(screen.getByRole('button', { name: '계정 삭제' })).toBeDisabled()

    update({ canDeleteAccount: false, canStartDeleteAccount: true })
    expect(screen.getByRole('button', { name: '계정 삭제' })).toBeEnabled()
  })
})

describe('SettingsRibScreen 안내 종류', () => {
  it('완료 안내(info)는 안내(status)로 보인다', () => {
    setup({ notice: '마스터 비밀번호를 바꿨습니다.', noticeKind: 'info' })

    expect(screen.getByRole('status')).toHaveTextContent('마스터 비밀번호를 바꿨습니다.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('오프라인 안내(error)는 오류(alert) 모양으로 보인다', () => {
    setup({ notice: '인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.', noticeKind: 'error' })

    expect(screen.getByRole('alert')).toHaveTextContent('인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('안내 종류가 바뀌면 모양도 따라 바뀐다', () => {
    const { update } = setup({ notice: '안내', noticeKind: 'error' })
    expect(screen.getByRole('alert')).toBeInTheDocument()

    update({ noticeKind: 'info' })

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('안내')
  })
})

describe('SettingsRibScreen 자동 잠금 선택지', () => {
  it('State의 선택지만 그리고 라벨은 분·시간 규칙을 따른다', () => {
    setup({ autoLockOptionsMs: [0, 60_000, 600_000, 7_200_000], autoLockTimeoutMs: 600_000 })

    const select = screen.getByLabelText('자동 잠금')
    expect(within(select).getAllByRole('option').map((option) => option.textContent)).toEqual(['즉시', '1분', '10분', '2시간'])
    expect(select).toHaveValue('600000')
  })

  it('기본 다섯 가지 선택지는 즉시·1분·5분·15분·1시간으로 읽힌다', () => {
    setup()

    expect(within(screen.getByLabelText('자동 잠금')).getAllByRole('option').map((option) => option.textContent)).toEqual([
      '즉시',
      '1분',
      '5분',
      '15분',
      '1시간',
    ])
  })
})

describe('SettingsRibScreen 정책 문구는 State 값을 그린다', () => {
  it('진입 버튼 이름과 내보내기 안내 글자를 State에서 받는다', () => {
    const { view } = setup({
      modal: 'exportConfirm',
      exportNotice: asState<WebConfirmText>({ title: '내보내기 제목', body: '내보내기 본문', confirm: '내보내기 확인', cancel: '내보내기 취소' }),
      changePasswordDialog: withFields(CHANGE_PASSWORD_DIALOG_TEXT, { title: '변경 진입' }),
      deleteAccountDialog: withFields(DELETE_ACCOUNT_DIALOG_TEXT, { title: '삭제 진입' }),
    })

    expect(screen.getByRole('button', { name: '변경 진입' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '삭제 진입' })).toBeInTheDocument()
    const prompt = screen.getByRole('alertdialog', { name: '내보내기 제목' })
    expect(prompt).toHaveTextContent('내보내기 본문')
    fireEvent.click(within(prompt).getByRole('button', { name: '내보내기 확인' }))
    fireEvent.click(within(prompt).getByRole('button', { name: '내보내기 취소' }))
    expect(view.confirmExport).toHaveBeenCalledTimes(1)
    expect(view.cancelModal).toHaveBeenCalledTimes(1)
  })

  it('복원 대화상자의 글자를 State에서 받는다', () => {
    setup({
      modal: 'restorePassword',
      inProgress: true,
      restoreDialog: asState<WebRestoreDialogText>({
        title: '복원 제목',
        body: '복원 본문',
        passwordLabel: '복원 비밀번호',
        submit: '복원 실행',
        cancel: '복원 취소',
        progress: '복원 진행 중',
      }),
    })

    const dialog = screen.getByRole('dialog', { name: '복원 제목' })
    expect(dialog).toHaveTextContent('복원 본문')
    expect(within(dialog).getByLabelText('복원 비밀번호')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '복원 취소' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '복원 실행' })).toBeInTheDocument()
    expect(within(dialog).getByRole('status')).toHaveTextContent('복원 진행 중')
  })

  it('비밀번호 변경 대화상자의 글자와 새 비밀번호 규칙 안내를 State에서 받는다', () => {
    setup({
      modal: 'changePassword',
      inProgress: true,
      changePasswordDialog: asState<WebChangePasswordDialogText>({
        title: '변경 제목',
        body: '변경 본문',
        currentLabel: '현재 칸',
        newLabel: '새 칸',
        confirmLabel: '확인 칸',
        newPasswordRule: '규칙 안내',
        submit: '변경 실행',
        cancel: '변경 취소',
        progress: '변경 진행 중',
      }),
    })

    const dialog = screen.getByRole('dialog', { name: '변경 제목' })
    expect(dialog).toHaveTextContent('변경 본문')
    expect(within(dialog).getByLabelText('현재 칸')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('새 칸')).toHaveAccessibleDescription('규칙 안내')
    expect(within(dialog).getByLabelText('확인 칸')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '변경 취소' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '변경 실행' })).toBeInTheDocument()
    expect(within(dialog).getByRole('status')).toHaveTextContent('변경 진행 중')
  })

  it('계정 삭제 대화상자의 글자를 State에서 받는다', () => {
    setup({
      modal: 'deleteAccount',
      inProgress: true,
      deleteAccountDialog: asState<WebDeleteAccountDialogText>({
        title: '삭제 제목',
        body: '삭제 본문',
        passwordLabel: '삭제 비밀번호 칸',
        phraseLabel: '삭제 확인 문구 칸',
        submit: '삭제 실행',
        cancel: '삭제 취소',
        progress: '삭제 진행 중',
      }),
    })

    const dialog = screen.getByRole('dialog', { name: '삭제 제목' })
    expect(dialog).toHaveTextContent('삭제 본문')
    expect(within(dialog).getByLabelText('삭제 비밀번호 칸')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('삭제 확인 문구 칸')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '삭제 취소' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '삭제 실행' })).toBeInTheDocument()
    expect(within(dialog).getByRole('status')).toHaveTextContent('삭제 진행 중')
  })
})
