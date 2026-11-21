// 공유 Kotlin의 web-example 모듈(Apps/Shared/web-example)이 내보내는 것 중 Example 페이지가 쓰는 부분.
// 생성된 .d.mts를 직접 가리키지 않는 이유: Example 번들은 `npm run example` 때만 빌드하므로, 없을 때도 `npm run check`가 통과해야 한다.
// Kotlin 쪽(WebExamples.kt)을 고치면 여기도 맞춘다
declare module 'keymanage-web-example' {
  export interface WebExampleFeature {
    readonly key: string
    readonly title: string
    readonly scenarios: readonly string[]
  }

  export interface WebRunningExample {
    readonly screenKey: string
    readonly view: unknown
    stop(): void
  }

  export function exampleFeatures(): WebExampleFeature[]
  export function launchExample(featureKey: string, scenarioIndex: number, onEvent: (event: string) => void): WebRunningExample
}
