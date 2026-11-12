// 브라우저 테스트의 테스트당 시간 한도(기본 2초). 60만 회 키 유도와 5MB 볼트 암호화 테스트가 있어 늘린다
config.set({ client: { mocha: { timeout: 120000 } }, browserNoActivityTimeout: 300000 })
