# 백엔드 이미지 (ADR-0021). 빌드 컨텍스트는 레포 루트다
# 1단계: JDK 21로 실행 jar를 만든다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY Back/ ./
# Windows에서 받은 레포는 gradlew가 CRLF일 수 있다. 셸이 읽을 수 있게 LF로 바꾼다
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :app:bootJar --no-daemon \
    && find app/build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

# 2단계: JRE 21로 jar만 실행한다
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 keymanage \
    && useradd --system --uid 10001 --gid keymanage --no-create-home keymanage \
    && mkdir -p /data/vaults /data/auth \
    && chown -R keymanage:keymanage /data
COPY --from=build /app.jar /opt/keymanage/app.jar
# 볼트·계정·서버 비밀키는 볼륨(/data)에 둔다. 컨테이너를 다시 만들어도 남는다
ENV KEYMANAGE_VAULT_STORAGE_DIR=/data/vaults \
    KEYMANAGE_AUTH_STORAGE_DIR=/data/auth
VOLUME /data
USER keymanage
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/keymanage/app.jar"]
