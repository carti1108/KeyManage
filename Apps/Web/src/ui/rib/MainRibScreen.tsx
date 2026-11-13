// 볼트 껍데기 화면 (Kotlin MainRib의 WebMainView). 탭·배너·로그아웃 확인·다시 연결 입력을 그리고,
// 고른 탭의 화면은 view.slot에 들어온 자식 RIB 화면이 채운다
import { Lock, LogOut, RefreshCw, Settings, Shield, Sparkles, WifiOff, type LucideIcon } from 'lucide-react'
import type { FormEvent } from 'react'
import type { WebMainState, WebMainView } from 'keymanage-web-session'
import { cn } from '../cn'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ConfirmPrompt } from '../components/ConfirmPrompt'
import { PasswordField } from '../components/PasswordField'
import { GeneratorRibScreen } from './GeneratorRibScreen'
import { RibSlot, useViewStore } from './ribsReact'
import { SettingsRibScreen } from './SettingsRibScreen'
import { VaultListRibScreen } from './VaultListRibScreen'

const TABS: readonly { readonly key: string; readonly label: string; readonly Icon: LucideIcon }[] = [
  { key: 'vault', label: '볼트', Icon: Shield },
  { key: 'generator', label: '생성기', Icon: Sparkles },
  { key: 'settings', label: '설정', Icon: Settings },
]

export function MainRibScreen({ view }: { view: WebMainView }) {
  const state = useViewStore(view.store)

  return (
    <div className="flex min-h-screen flex-col lg:flex-row bg-background text-foreground">
      <aside aria-label="사이드바" className="w-full lg:w-60 lg:shrink-0 border-b lg:border-b-0 lg:border-r border-border bg-card/60 flex flex-col justify-between p-4 gap-4">
        <div className="flex flex-col gap-4">
          <div className="min-w-0">
            <h1 className="text-lg font-bold tracking-tight">KeyManage</h1>
            <p className="truncate text-xs text-muted-foreground">{state.email}</p>
          </div>
          <nav aria-label="메인 메뉴" className="flex lg:flex-col gap-1">
            {TABS.map(({ key, label, Icon }) => {
              const selected = state.selectedTab === key
              return (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={selected}
                  onClick={() => view.selectTab(key)}
                  className={cn(
                    'flex flex-1 lg:flex-initial items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                    selected ? 'bg-accent text-accent-foreground font-semibold' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )}
                >
                  <Icon aria-hidden className="size-4" />
                  <span>{label}</span>
                </button>
              )
            })}
          </nav>
        </div>

        <div className="flex flex-col gap-3 border-t border-border/80 pt-3">
          <div className="grid grid-cols-2 gap-2">
            <Button
              variant="outline"
              size="sm"
              className="w-full justify-center text-xs px-2 whitespace-nowrap h-8"
              icon={<Lock aria-hidden className="size-3.5 shrink-0" />}
              onClick={() => view.requestLock()}
            >
              잠그기
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="w-full justify-center text-xs px-2 whitespace-nowrap h-8 text-muted-foreground hover:text-destructive"
              icon={<LogOut aria-hidden className="size-3.5 shrink-0" />}
              onClick={() => view.requestLogout()}
            >
              로그아웃
            </Button>
          </div>
        </div>
      </aside>

      <main className="flex-1 flex flex-col min-w-0 p-4 md:p-6 lg:p-8 gap-4 overflow-y-auto">
        {state.offline && state.offlineNotice !== null && (
          <Alert variant="info">
            <WifiOff aria-hidden className="mr-1 inline size-4" />
            {state.offlineNotice}
          </Alert>
        )}
        {state.readOnly && state.readOnlyNotice !== null && <Alert variant="info">{state.readOnlyNotice}</Alert>}
        {state.isConfirmingLogout && (
          <ConfirmPrompt
            title={state.pendingLogoutConfirm.title}
            body={state.pendingLogoutConfirm.body}
            confirmLabel={state.pendingLogoutConfirm.confirm}
            cancelLabel={state.pendingLogoutConfirm.cancel}
            pending={state.isLoggingOut}
            onConfirm={() => view.confirmLogout()}
            onCancel={() => view.cancelLogout()}
          />
        )}
        {state.syncError && <Alert title={state.syncError.title ?? undefined}>{state.syncError.body}</Alert>}
        {state.showsDeviceStorageWarning && state.deviceStorageWarning !== null && <Alert>{state.deviceStorageWarning}</Alert>}
        {state.needsReconnect && <ReconnectForm view={view} state={state} />}

        <div className={cn(state.selectedTab !== 'vault' && 'mx-auto w-full max-w-xl')}>
          <RibSlot
            slot={view.slot}
            screens={{
              vaultList: (v) => <VaultListRibScreen view={v} />,
              passwordGenerator: (v) => <GeneratorRibScreen view={v} />,
              settings: (v) => <SettingsRibScreen view={v} />,
            }}
          />
        </div>
      </main>
    </div>
  )
}

/** 세션 없이 연 볼트에서 마스터 비밀번호를 다시 받는 칸. 입력값과 결과는 모두 Kotlin 상태에 있다 */
function ReconnectForm({ view, state }: { view: WebMainView; state: WebMainState }) {
  function submit(event: FormEvent) {
    event.preventDefault()
    view.reconnect()
  }

  return (
    <form className="flex flex-col gap-3 rounded-md border border-border p-3" onSubmit={submit} noValidate>
      <p className="text-sm">{state.reconnectPrompt.body}</p>
      {/* 비밀번호 관리자가 계정을 알아보도록 숨은 사용자 이름을 둔다 */}
      <input type="email" autoComplete="username" value={state.email} readOnly hidden />
      <PasswordField
        label="마스터 비밀번호"
        autoComplete="current-password"
        value={state.reconnectPassword}
        onTextChange={(value) => view.updateReconnectPassword(value)}
        disabled={state.isReconnecting}
      />
      {state.isConfirmingReconnectRollback ? (
        <ConfirmPrompt
          title={state.rollbackWarning.title}
          body={state.rollbackWarning.body}
          confirmLabel={state.rollbackWarning.confirm}
          cancelLabel={state.rollbackWarning.cancel}
          onConfirm={() => view.acceptReconnectRollback()}
          onCancel={() => view.cancelReconnectRollback()}
        />
      ) : (
        state.reconnectError && <Alert title={state.reconnectError.title ?? undefined}>{state.reconnectError.body}</Alert>
      )}
      <Button type="submit" size="sm" loading={state.isReconnecting} disabled={state.isReconnecting} icon={<RefreshCw aria-hidden className="size-4" />} className="self-start">
        {state.reconnectPrompt.action}
      </Button>
    </form>
  )
}
