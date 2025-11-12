#!/bin/bash
# ============================================
# Docker Entrypoint - App Inventor Server
# ============================================

set -e

echo "============================================"
echo "App Inventor Server - Phase 4"
echo "Starting at: $(date)"
echo "============================================"

# Wait for PostgreSQL
if [ -n "$DB_HOST" ]; then
    echo "Waiting for PostgreSQL at ${DB_HOST}:${DB_PORT:-5432}..."
    timeout=60
    while ! pg_isready -h ${DB_HOST} -p ${DB_PORT:-5432} -U ${DB_USER:-appinventor_user} -t 1 > /dev/null 2>&1; do
        timeout=$((timeout - 1))
        if [ $timeout -le 0 ]; then
            echo "ERROR: PostgreSQL not ready after 60 seconds"
            exit 1
        fi
        echo "  Waiting... ($timeout seconds remaining)"
        sleep 1
    done
    echo "✅ PostgreSQL is ready!"
fi

# Wait for Redis
if [ -n "$REDIS_HOST" ]; then
    echo "Waiting for Redis at ${REDIS_HOST}:${REDIS_PORT:-6379}..."
    timeout=30
    while ! timeout 1 bash -c "cat < /dev/null > /dev/tcp/${REDIS_HOST}/${REDIS_PORT:-6379}" 2>/dev/null; do
        timeout=$((timeout - 1))
        if [ $timeout -le 0 ]; then
            echo "ERROR: Redis not ready after 30 seconds"
            exit 1
        fi
        echo "  Waiting... ($timeout seconds remaining)"
        sleep 1
    done
    echo "✅ Redis is ready!"
fi

# Configure database connection
if [ -n "$DB_PASSWORD" ]; then
    export JAVA_OPTS="$JAVA_OPTS -Ddb.host=${DB_HOST}"
    export JAVA_OPTS="$JAVA_OPTS -Ddb.port=${DB_PORT:-5432}"
    export JAVA_OPTS="$JAVA_OPTS -Ddb.name=${DB_NAME:-appinventor}"
    export JAVA_OPTS="$JAVA_OPTS -Ddb.user=${DB_USER:-appinventor_user}"
    export JAVA_OPTS="$JAVA_OPTS -Ddb.password=${DB_PASSWORD}"
    echo "✅ Database configuration set"
fi

# Configure Redis connection
if [ -n "$REDIS_PASSWORD" ]; then
    export JAVA_OPTS="$JAVA_OPTS -Dredis.host=${REDIS_HOST}"
    export JAVA_OPTS="$JAVA_OPTS -Dredis.port=${REDIS_PORT:-6379}"
    export JAVA_OPTS="$JAVA_OPTS -Dredis.password=${REDIS_PASSWORD}"
    echo "✅ Redis configuration set"
fi

# Configure MinIO connection
if [ -n "$MINIO_ACCESS_KEY" ]; then
    export JAVA_OPTS="$JAVA_OPTS -Dminio.endpoint=${MINIO_ENDPOINT}"
    export JAVA_OPTS="$JAVA_OPTS -Dminio.access.key=${MINIO_ACCESS_KEY}"
    export JAVA_OPTS="$JAVA_OPTS -Dminio.secret.key=${MINIO_SECRET_KEY}"
    export JAVA_OPTS="$JAVA_OPTS -Dminio.bucket=${MINIO_BUCKET:-appinventor-files}"
    echo "✅ MinIO configuration set"
fi

echo "============================================"
echo "Starting Tomcat..."
echo "JAVA_OPTS: $JAVA_OPTS"
echo "============================================"

# Execute the command passed to docker run
exec "$@"
