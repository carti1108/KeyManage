#!/bin/sh
# 인증서가 있으면 8443 https 사이트를 켠다. 없으면 8080 http만 띄운다 (ADR-0021)
set -eu

CERT=/certs/localhost.pem
KEY=/certs/localhost-key.pem
ENABLED=/etc/caddy/enabled

mkdir -p "$ENABLED"
rm -f "$ENABLED"/*.caddy

if [ -f "$CERT" ] && [ -f "$KEY" ]; then
    cp /etc/caddy/https.caddy "$ENABLED"/https.caddy
    echo "keymanage: 인증서를 찾았다. http://localhost:8080 과 https://localhost:8443 을 연다"
elif [ -f "$CERT" ] || [ -f "$KEY" ]; then
    echo "keymanage: 오류: certs 폴더에 localhost.pem 과 localhost-key.pem 이 둘 다 있어야 한다" >&2
    exit 1
else
    echo "keymanage: certs 폴더에 인증서가 없다. http://localhost:8080 만 연다 (https://localhost:8443 은 꺼짐)"
fi

exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
