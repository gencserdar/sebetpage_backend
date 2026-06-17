#!/bin/sh
# Retry Maven package when Maven Central drops large artifacts mid-download.
export MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.http.retryHandler.requestSentEnabled=true"
MODULES="$1"
if [ -z "$MODULES" ]; then
  echo "usage: mvn-package.sh <comma-separated-modules>" >&2
  exit 1
fi

attempt=1
while [ "$attempt" -le 3 ]; do
  echo "[$(date -Iseconds)] Maven package attempt $attempt/3: $MODULES" >&2
  if mvn -B -pl "$MODULES" -am -DskipTests package; then
    echo "[$(date -Iseconds)] Maven package succeeded: $MODULES" >&2
    exit 0
  fi
  echo "[$(date -Iseconds)] Maven package attempt $attempt/3 failed, clearing partial gRPC artifacts..." >&2
  rm -rf /root/.m2/repository/io/grpc 2>/dev/null || true
  if [ "$attempt" -eq 3 ]; then
    exit 1
  fi
  sleep 5
  attempt=$((attempt + 1))
done
