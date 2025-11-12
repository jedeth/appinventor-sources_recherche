# MIT App Inventor Rendezvous Server

This directory contains the code needed to run the MIT App Inventor
Rendezvous Server version 2.

Version 2 supports both our "legacy" httpd connection to the MIT AI2
Companion and the newer WebRTC based system.

## 🆕 Google-Free Deployment Support

**Version 2.1.0** adds support for **Redis standalone**, enabling deployment
on infrastructure independent of Google App Engine, while maintaining full
compatibility with the original MIT implementation.

### Cache Backend Options

This server now supports **two cache backends**:

1. **Memcache** (Google App Engine) - Original MIT implementation
2. **Redis** (Standalone) - For Google-free deployment

Choose your backend via the `CACHE_BACKEND` environment variable:

```bash
# Google App Engine (default)
CACHE_BACKEND=memcache npm start

# Independent infrastructure
CACHE_BACKEND=redis npm start
```

See [MIGRATION.md](MIGRATION.md) for detailed migration guide and compatibility information.

## Quick Start

### Option 1: Docker Deployment (Original)

This code is designed to run in a docker container. You can build the
needed image simply with:

    docker build -t <imagename> .

Run the resulting image on your server as:

    docker run --restart=always -d -p 80:3000 --name=rendezvous <imagename>

<imagename> is a name you pick for your image. The image expects (and
should create) a docker volume which it mounts in "/data". An sqlite3
database will be created here which will gather statistics. Keep an
eye on this database as it will grow without bound. You may need to
trim it periodically.

Logs for the running server are in /var/log/supervisor within the
container. These logs are automatically managed (trimmed as needed) so
you need not worry about them. They mostly contain debugging output
which isn't of much value (and which a newer version may flush
completely).

### Option 2: Docker with Redis (Google-Free)

Create a `docker-compose.yml`:

```yaml
version: '3.8'
services:
  rendezvous:
    build: .
    ports:
      - "80:3000"
    environment:
      - CACHE_BACKEND=redis
      - REDIS_HOST=redis
    depends_on:
      - redis
  redis:
    image: redis:7-alpine
    volumes:
      - redis-data:/data
volumes:
  redis-data:
```

Then run:

    docker-compose up -d

### Option 3: Standalone Node.js

For development or simple deployment:

```bash
# Install dependencies
npm install

# Configure (copy and edit .env file)
cp .env.example .env

# Start with Redis
CACHE_BACKEND=redis npm start

# Or start with Memcache (Google App Engine)
CACHE_BACKEND=memcache npm start
```

## Configuration

Configuration is done via environment variables. See `.env.example` for all options:

```bash
# Cache backend: 'memcache' or 'redis'
CACHE_BACKEND=redis

# Redis configuration (when using CACHE_BACKEND=redis)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_DB=0
REDIS_PASSWORD=  # optional
```

## Testing

Test that the server is running:

```bash
# Basic connectivity test
curl http://localhost:3000/

# Rendezvous endpoint test
curl http://localhost:3000/rendezvous/test

# Rendezvous2 (WebRTC) endpoint test
curl http://localhost:3000/rendezvous2/test
```

All tests should return "Connection OK".

## Architecture

The rendezvous server acts as a **WebRTC signaling server** that:

1. Allows the MIT AI2 Companion app to register with a unique key
2. Allows the web-based App Inventor blocks editor to retrieve connection info
3. Facilitates WebRTC peer connection establishment
4. Stores connection data temporarily (120-second TTL)

**Endpoints:**
- `/rendezvous/` - Legacy HTTP connection mode
- `/rendezvous2/` - Modern WebRTC mode (recommended)

**Storage:**
- **Cache** (Memcache or Redis): Temporary connection data
- **SQLite**: Statistics logging

## Files

- `rendezvous.js` - Main server code
- `cache-adapter.js` - Cache abstraction layer (NEW in v2.1.0)
- `logworker.js` - Worker for async statistics logging
- `package.json` - Node.js dependencies
- `.env.example` - Configuration template
- `MIGRATION.md` - Detailed migration and compatibility guide

## Compatibility with MIT App Inventor

These modifications are designed to maintain **full compatibility** with the
original MIT App Inventor codebase:

- Original code is preserved in comments
- Changes are minimal and localized
- Easy to merge updates from MIT repository
- Can switch between Google and independent backends instantly

See [MIGRATION.md](MIGRATION.md) for versioning strategy.

## Dependencies

```json
{
  "memcache": "^0.3.0",      // Google App Engine Memcache
  "redis": "^3.1.2",         // Redis client (NEW)
  "workerpool": "^2.3.1",    // Worker pool for async tasks
  "sqlite3": "^3.1.13",      // Statistics database
  "async-lock": "^1.1.3"     // Locking for concurrent access
}
```

## Performance

**Expected throughput:**
- Legacy mode: ~1,000 concurrent connections
- WebRTC mode: ~10,000 concurrent connections

**Cache performance:**
- Memcache (Google): ~5-10ms latency
- Redis (local): ~1ms latency
- Redis (network): ~2-5ms latency

## Troubleshooting

### Redis connection refused
```bash
# Check Redis is running
sudo systemctl status redis
redis-cli ping  # Should return "PONG"
```

### Module not found
```bash
# Reinstall dependencies
rm -rf node_modules
npm install
```

### Port 3000 already in use
```bash
# Change port (not recommended, update clients too)
PORT=8080 npm start
```

## Security

**Production checklist:**
- ✅ Use strong Redis password (`REDIS_PASSWORD`)
- ✅ Restrict Redis network access (firewall)
- ✅ Never commit `.env` file to Git
- ✅ Use TLS/SSL for Redis in production
- ✅ Monitor and trim SQLite database regularly
- ✅ Set up Redis authentication

## License

Apache License 2.0 (same as MIT App Inventor)

## Authors

- **Original implementation:** Jeffrey I. Schiller <jis@mit.edu>
- **Redis support:** 2024

## Support

- Full documentation: [MIGRATION.md](MIGRATION.md)
- MIT App Inventor: https://github.com/mit-cml/appinventor-sources
- Redis documentation: https://redis.io/documentation

