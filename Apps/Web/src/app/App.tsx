// 조립 지점(Composition Root): 이후 어댑터를 만들어 포트로 화면에 넘겨준다 (ADR-0004)
function App() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-900 text-slate-50">
      <h1 className="text-3xl font-bold">KeyManage</h1>
    </main>
  )
}

export default App
