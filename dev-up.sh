#!/usr/bin/env bash
set -e

echo "1) MySQL 기동..."
docker compose up -d

echo "2) Mock Supplier 기동 (백그라운드)..."
./gradlew :mock:bootRun > mock.log 2>&1 &
echo "   로그: mock.log"

echo "3) Mock이 응답할 때까지 대기..."
until curl -s http://localhost:9090/a/v1/hotels > /dev/null 2>&1; do
  sleep 1
  echo "   대기 중..."
done
echo "   Mock 준비 완료."

echo "4) 애플리케이션 기동 (포그라운드 — Ctrl+C로 종료)..."
# Gradle은 백그라운드 Daemon에 작업을 위임하므로 $!(gradlew 클라이언트 PID)는
# 실제 Mock Supplier JVM과 다르다 — 이름으로 찾아서 종료해야 한다.
trap "echo '종료 중...'; pkill -f MockSupplierApplicationKt 2>/dev/null" EXIT
./gradlew bootRun
