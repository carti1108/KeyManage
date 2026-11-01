# 웹 정적 파일 + 리버스 프록시 이미지 (ADR-0021). 빌드 컨텍스트는 레포 루트다
# 1단계: 웹을 빌드한다. 서버 주소를 비워 같은 출처(/api)로 요청하게 한다
FROM node:22 AS web
WORKDIR /src/Apps/Web
# 웹 코드가 레포 루트의 암호 테스트 벡터를 가져온다 (tsc가 함께 검사한다)
COPY docs/crypto-test-vectors.json /src/docs/crypto-test-vectors.json
COPY Apps/Web/package.json Apps/Web/package-lock.json ./
RUN npm ci
COPY Apps/Web/ ./
ENV VITE_API_BASE_URL=
RUN npm run build

# 2단계: Caddy가 빌드 결과를 내보내고 /api를 백엔드로 넘긴다
FROM caddy:2
COPY --from=web /src/Apps/Web/dist /srv
COPY Docker/caddy/Caddyfile /etc/caddy/Caddyfile
COPY Docker/caddy/https.caddy /etc/caddy/https.caddy
COPY Docker/caddy/entrypoint.sh /usr/local/bin/keymanage-caddy
# Windows에서 받은 레포는 스크립트가 CRLF일 수 있다. 셸이 읽을 수 있게 LF로 바꾼다
RUN sed -i 's/\r$//' /usr/local/bin/keymanage-caddy && chmod +x /usr/local/bin/keymanage-caddy
# 공식 이미지 기본값대로 root로 실행한다. Linux에서 mkcert 키는 호스트 사용자 소유 0600이라, 다른 uid로는 마운트한 키를 읽지 못한다
EXPOSE 8080 8443
ENTRYPOINT ["/usr/local/bin/keymanage-caddy"]
