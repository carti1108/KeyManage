// 볼트가 열린 뒤의 데스크톱 3분할 / 모바일 반응형 틀 (와이어프레임 03~06 플랫폼 틀)
import { Lock, LogOut, Settings, Shield, Sparkles, WifiOff } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { cn } from '../cn'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { OFFLINE_NOTICE, READ_ONLY_NOTICE } from '../messages/vaultNotices'
import { PasswordGenerator } from '../vault/PasswordGenerator'
import { SettingsView } from './SettingsView'

export type VaultTab = 'vault' | 'generator' | 'settings'

export interface VaultHomeScreenProps {
  email: string
  readOnly: boolean
  offline: boolean
  onLock: () => void
  onLogout: () => void
  onCopy?: (text: string) => void
  sidebarFooter?: ReactNode
  children: ReactNode
}

export function VaultHomeScreen({
  email,
  readOnly,
  offline,
  onLock,
  onLogout,
  onCopy,
  sidebarFooter,
  children,
}: VaultHomeScreenProps) {
  const [activeTab, setActiveTab] = useState<VaultTab>('vault')

  return (
    <div className="flex min-h-screen flex-col lg:flex-row bg-background text-foreground">
      {/* 1. 사이드바 (와이어프레임 Web 240px: w-60) */}
      <aside aria-label="사이드바" className="w-full lg:w-60 lg:shrink-0 border-b lg:border-b-0 lg:border-r border-border bg-card/60 flex flex-col justify-between p-4 gap-4">
        <div className="flex flex-col gap-4">
          {/* 로고 및 사용자 정보 */}
          <div className="min-w-0">
            <h1 className="text-lg font-bold tracking-tight">KeyManage</h1>
            <p className="truncate text-xs text-muted-foreground">{email}</p>
          </div>

          {/* 사이드바 메뉴 탭 (와이어프레임 03~06: 볼트, 생성기, 설정) */}
          <nav aria-label="메인 메뉴" className="flex lg:flex-col gap-1">
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'vault'}
              onClick={() => setActiveTab('vault')}
              className={cn(
                'flex flex-1 lg:flex-initial items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                activeTab === 'vault'
                  ? 'bg-accent text-accent-foreground font-semibold'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground',
              )}
            >
              <Shield aria-hidden className="size-4" />
              <span>볼트</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'generator'}
              onClick={() => setActiveTab('generator')}
              className={cn(
                'flex flex-1 lg:flex-initial items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                activeTab === 'generator'
                  ? 'bg-accent text-accent-foreground font-semibold'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground',
              )}
            >
              <Sparkles aria-hidden className="size-4" />
              <span>생성기</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'settings'}
              onClick={() => setActiveTab('settings')}
              className={cn(
                'flex flex-1 lg:flex-initial items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                activeTab === 'settings'
                  ? 'bg-accent text-accent-foreground font-semibold'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground',
              )}
            >
              <Settings aria-hidden className="size-4" />
              <span>설정</span>
            </button>
          </nav>
        </div>

        {/* 사이드바 하단 (자동 잠금, 백업 및 잠금/로그아웃) */}
        <div className="flex flex-col gap-3 border-t border-border/80 pt-3">
          {sidebarFooter && (
            <div className="rounded-lg bg-muted/30 p-3 flex flex-col gap-3 border border-border/50">
              {sidebarFooter}
            </div>
          )}
          <div className="grid grid-cols-2 gap-2">
            <Button
              variant="outline"
              size="sm"
              className="w-full justify-center text-xs px-2 whitespace-nowrap h-8"
              icon={<Lock aria-hidden className="size-3.5 shrink-0" />}
              onClick={onLock}
            >
              잠그기
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="w-full justify-center text-xs px-2 whitespace-nowrap h-8 text-muted-foreground hover:text-destructive"
              icon={<LogOut aria-hidden className="size-3.5 shrink-0" />}
              onClick={onLogout}
            >
              로그아웃
            </Button>
          </div>
        </div>
      </aside>

      {/* 2. 메인 콘텐츠 영역 */}
      <main className="flex-1 flex flex-col min-w-0 p-4 md:p-6 gap-4 overflow-y-auto">
        {offline && (
          <Alert variant="info">
            <WifiOff aria-hidden className="mr-1 inline size-4" />
            {OFFLINE_NOTICE}
          </Alert>
        )}
        {readOnly && <Alert variant="info">{READ_ONLY_NOTICE}</Alert>}

        {/* 탭별 뷰 전환 */}
        {activeTab === 'vault' && children}

        {activeTab === 'generator' && (
          <div className="mx-auto w-full max-w-xl">
            <Card className="p-6">
              <h2 className="mb-4 text-xl font-bold">비밀번호 생성기</h2>
              <PasswordGenerator onCopy={onCopy} />
            </Card>
          </div>
        )}

        {activeTab === 'settings' && (
          <div className="mx-auto w-full max-w-xl">
            <SettingsView email={email} offline={offline} onLock={onLock} onLogout={onLogout} />
          </div>
        )}
      </main>
    </div>
  )
}
