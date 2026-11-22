import { useState } from 'react'
import { exampleFeatures, launchExample, type WebExampleFeature, type WebRunningExample } from 'keymanage-web-example'
import { AuthRibScreen } from '../ui/rib/AuthRibScreen'
import { GeneratorRibScreen } from '../ui/rib/GeneratorRibScreen'
import { MainRibScreen } from '../ui/rib/MainRibScreen'
import type { ScreenRenderers } from '../ui/rib/ribsReact'
import { SettingsRibScreen } from '../ui/rib/SettingsRibScreen'
import { SettingsSidebarBox } from '../ui/rib/SettingsSidebarBox'
import { UnlockRibScreen } from '../ui/rib/UnlockRibScreen'
import { VaultListRibScreen } from '../ui/rib/VaultListRibScreen'

// 피처 Example 페이지 (modularization-plan E단계). Android의 feature/<이름>/example 앱과 같은 일을 한다:
// 시나리오를 고르면 공유 RIB 하나를 가짜 세션으로 만들어 실제 화면에 붙인다. 서버와 실제 세션은 쓰지 않는다.
// 사람이 브라우저에서 화면을 확인하는 용도라 배포 빌드에는 들어가지 않는다

/** 웹 뷰의 screenKey마다 그릴 화면 */
const SCREENS: ScreenRenderers = {
  auth: (view) => <AuthRibScreen view={view} />,
  unlock: (view) => <UnlockRibScreen view={view} />,
  main: (view) => <MainRibScreen view={view} />,
  vaultList: (view) => <VaultListRibScreen view={view} />,
  passwordGenerator: (view) => <GeneratorRibScreen view={view} />,
  // 앱에서는 사이드바 상자와 설정 탭으로 나뉜다. 설정 RIB만 띄우는 Example은 둘을 함께 보인다
  settings: (view) => (
    <div className="mx-auto flex max-w-xl flex-col gap-6 p-4">
      <SettingsSidebarBox view={view} />
      <SettingsRibScreen view={view} />
    </div>
  ),
}

interface Running {
  title: string
  example: WebRunningExample
}

export function ExampleApp() {
  const [features] = useState<WebExampleFeature[]>(() => exampleFeatures())
  const [running, setRunning] = useState<Running | null>(null)
  const [events, setEvents] = useState<string[]>([])

  // RIB은 누를 때 한 번만 만든다. 그리는 중에 만들면 StrictMode가 두 번 붙인다
  const open = (feature: WebExampleFeature, index: number) => {
    setEvents([])
    const example = launchExample(feature.key, index, (event) => setEvents((previous) => [...previous, event]))
    setRunning({ title: `${feature.title} · ${feature.scenarios[index]}`, example })
  }

  const close = () => {
    running?.example.stop()
    setRunning(null)
  }

  if (running === null) return <ScenarioList features={features} onOpen={open} />

  const render = SCREENS[running.example.screenKey]
  if (render === undefined) throw new Error(`No screen for '${running.example.screenKey}'`)

  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex items-center gap-3 border-b border-border px-4 py-2">
        <button type="button" className="rounded-md border border-border px-3 py-1 text-label" onClick={close}>
          목록으로
        </button>
        <h1 className="text-label text-muted-foreground">{running.title}</h1>
      </header>
      <div className="flex-1">{render(running.example.view)}</div>
      <footer className="border-t border-border px-4 py-2 text-caption text-muted-foreground" aria-live="polite">
        결과: {events.length === 0 ? '없음' : events.join(' → ')}
      </footer>
    </div>
  )
}

function ScenarioList({ features, onOpen }: { features: WebExampleFeature[]; onOpen: (feature: WebExampleFeature, index: number) => void }) {
  return (
    <main className="mx-auto flex max-w-xl flex-col gap-6 px-4 py-6">
      <h1 className="text-title">피처 Example</h1>
      {features.map((feature) => (
        <section key={feature.key} className="flex flex-col gap-2">
          <h2 className="text-heading">{feature.title}</h2>
          <ul className="flex flex-col gap-1">
            {feature.scenarios.map((scenario, index) => (
              <li key={scenario}>
                <button
                  type="button"
                  className="w-full rounded-md border border-border px-3 py-2 text-left text-body hover:bg-muted"
                  onClick={() => onOpen(feature, index)}
                >
                  {scenario}
                </button>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </main>
  )
}
