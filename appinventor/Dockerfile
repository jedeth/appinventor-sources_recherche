# ============================================
# Dockerfile - App Inventor Server (Phase 4)
# ============================================
# Build: docker build -t appinventor-server:phase4-latest .
# Run: kubectl apply -f appinventor-deployment.yaml

FROM tomcat:9.0-jdk8

LABEL maintainer="DSI Paris <dsi@paris.fr>"
LABEL version="phase4-2.1"
LABEL description="App Inventor Server with PostgreSQL (Phase 4) - RGPD Compliant"

# Environment variables
ENV CATALINA_HOME=/usr/local/tomcat
ENV APP_HOME=/opt/appinventor
ENV JAVA_OPTS="-Xmx4G -Xms2G -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    vim \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# Create app directory
RUN mkdir -p ${APP_HOME}/war \
    && mkdir -p ${APP_HOME}/config \
    && mkdir -p ${APP_HOME}/logs

# Copy WAR file (built from ant build)
COPY build/war ${APP_HOME}/war

# Copy to Tomcat webapps
RUN rm -rf ${CATALINA_HOME}/webapps/* && \
    cp -r ${APP_HOME}/war ${CATALINA_HOME}/webapps/ROOT

# Copy configuration files
COPY appengine/src/com/google/appinventor/server/storage/database/*.properties ${APP_HOME}/config/ 2>/dev/null || true

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Expose ports
EXPOSE 8080 8443

# Volume for logs
VOLUME ["${APP_HOME}/logs"]

# Startup script
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["catalina.sh", "run"]
