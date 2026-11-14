// 설정 화면 (와이어프레임 06 설정)
import { Globe, Lock, LogOut, Shield, User } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '../components/Button'
import { Card } from '../components/Card'

export interface SettingsViewProps {
  email: string
  offline: boolean
  onLock: () => void
  onLogout: () => void
}

function SettingsSection({ title, icon, children }: { title: string; icon: ReactNode; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2 text-label font-semibold text-foreground">
        {icon}
        <h3>{title}</h3>
      </div>
      <div className="rounded-lg border border-border bg-card divide-y divide-border text-label">
        {children}
      </div>
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

export function SettingsView({ email, offline, onLock, onLogout }: SettingsViewProps) {
  const serverOrigin = typeof window !== 'undefined' ? window.location.origin : 'https://keymanage.example.com'

  return (
    <section aria-labelledby="settings-heading" className="flex flex-col gap-6">
      <div>
        <h2 id="settings-heading" className="text-title font-bold">설정</h2>
        <p className="text-label text-muted-foreground">계정 및 보안 정책 상태를 확인합니다.</p>
      </div>

      <div className="flex flex-col gap-4">
        {/* 계정 섹션 */}
        <SettingsSection title="계정" icon={<User aria-hidden className="size-4 text-primary" />}>
          <SettingsRow label="이메일" value={email} />
          <SettingsRow label="동기화 상태" value={offline ? '오프라인' : '최신 상태 (동기화 완료)'} />
        </SettingsSection>

        {/* 보안 섹션 */}
        <SettingsSection title="보안 정책" icon={<Shield aria-hidden className="size-4 text-primary" />}>
          <SettingsRow label="클립보드 자동 삭제" value="30초 (고정, COM-001)" />
          <SettingsRow label="암호화 구조" value="클라이언트 영지식 (AES-256-GCM)" />
        </SettingsSection>

        {/* 서버 섹션 */}
        <SettingsSection title="서버" icon={<Globe aria-hidden className="size-4 text-primary" />}>
          <SettingsRow label="서버 주소" value={serverOrigin} />
        </SettingsSection>

        {/* 계정 작업 */}
        <Card className="p-4 flex flex-col sm:flex-row items-center justify-between gap-3 bg-muted/20">
          <div>
            <h4 className="text-label font-medium">세션 제어</h4>
            <p className="text-caption text-muted-foreground">볼트를 잠그거나 계정에서 로그아웃합니다.</p>
          </div>
          <div className="flex gap-2 w-full sm:w-auto">
            <Button variant="outline" size="sm" className="flex-1 sm:flex-initial" icon={<Lock aria-hidden className="size-4" />} onClick={onLock}>
              잠그기
            </Button>
            <Button variant="destructive" size="sm" className="flex-1 sm:flex-initial" icon={<LogOut aria-hidden className="size-4" />} onClick={onLogout}>
              로그아웃
            </Button>
          </div>
        </Card>
      </div>
    </section>
  )
}
