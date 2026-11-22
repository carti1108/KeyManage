// 설정 탭 화면 (Kotlin SettingsRib의 WebSettingsView). 계정 정보·고정 보안 정책·서버 주소·세션 제어를 그린다.
// 자동 잠금·백업·비밀번호 변경·계정 삭제는 사이드바 아래 상자(SettingsSidebarBox)가 같은 뷰로 그린다
import { Globe, Lock, LogOut, Shield, User } from 'lucide-react'
import type { ReactNode } from 'react'
import type { WebSettingsView } from 'keymanage-web-session'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { useViewStore } from './ribsReact'

function SettingsSection({ title, icon, children }: { title: string; icon: ReactNode; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2 text-label font-semibold text-foreground">
        {icon}
        <h3>{title}</h3>
      </div>
      <div className="rounded-lg border border-border bg-card divide-y divide-border text-label">{children}</div>
    </div>
  )
}

function SettingsRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between p-4 gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium text-foreground truncate">{value}</span>
    </div>
  )
}

export function SettingsRibScreen({ view }: { view: WebSettingsView }) {
  const state = useViewStore(view.store)
  const serverAddress = state.serverAddress === '' ? window.location.origin : state.serverAddress

  return (
    <section aria-labelledby="settings-heading" className="flex flex-col gap-6">
      <div>
        <h2 id="settings-heading" className="text-title font-bold">
          설정
        </h2>
        <p className="text-label text-muted-foreground">계정 및 보안 정책 상태를 확인합니다.</p>
      </div>

      <div className="flex flex-col gap-4">
        <SettingsSection title="계정" icon={<User aria-hidden className="size-4 text-primary" />}>
          <SettingsRow label="이메일" value={state.email} />
          <SettingsRow label="동기화 상태" value={state.syncStatus} />
        </SettingsSection>

        <SettingsSection title="보안 정책" icon={<Shield aria-hidden className="size-4 text-primary" />}>
          <SettingsRow label={state.securityPolicy.clipboardLabel} value={state.securityPolicy.clipboardValue} />
          <SettingsRow label={state.securityPolicy.encryptionLabel} value={state.securityPolicy.encryptionValue} />
        </SettingsSection>

        <SettingsSection title="서버" icon={<Globe aria-hidden className="size-4 text-primary" />}>
          <SettingsRow label="서버 주소" value={serverAddress} />
        </SettingsSection>

        <Card className="p-4 flex flex-col sm:flex-row items-center justify-between gap-3 bg-muted/20">
          <div>
            <h4 className="text-label font-medium">세션 제어</h4>
            <p className="text-caption text-muted-foreground">볼트를 잠그거나 계정에서 로그아웃합니다.</p>
          </div>
          <div className="flex gap-2 w-full sm:w-auto">
            <Button variant="outline" size="sm" className="flex-1 sm:flex-initial" icon={<Lock aria-hidden className="size-4" />} onClick={() => view.requestLock()}>
              잠그기
            </Button>
            <Button variant="destructive" size="sm" className="flex-1 sm:flex-initial" icon={<LogOut aria-hidden className="size-4" />} onClick={() => view.requestLogout()}>
              로그아웃
            </Button>
          </div>
        </Card>
      </div>
    </section>
  )
}
