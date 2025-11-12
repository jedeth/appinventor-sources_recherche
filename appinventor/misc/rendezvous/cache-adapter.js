// Copyright 2024 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

/**
 * Cache Adapter - Abstraction layer for cache backends
 *
 * This adapter allows switching between Google App Engine Memcache
 * and Redis standalone, enabling deployment on non-Google infrastructure
 * while maintaining compatibility with the original MIT App Inventor codebase.
 *
 * Configuration via environment variables:
 * - CACHE_BACKEND: 'memcache' (default) or 'redis'
 * - REDIS_HOST: Redis server hostname (default: 'localhost')
 * - REDIS_PORT: Redis server port (default: 6379)
 * - REDIS_PASSWORD: Redis password (optional)
 * - REDIS_DB: Redis database number (default: 0)
 */

const CACHE_BACKEND = process.env.CACHE_BACKEND || 'memcache';

class CacheAdapter {
    constructor() {
        this.backend = CACHE_BACKEND;
        this.client = null;
        this.connected = false;
    }

    /**
     * Initialize and connect to the cache backend
     */
    connect() {
        if (this.backend === 'redis') {
            console.log('Initializing Redis cache backend...');
            this._connectRedis();
        } else {
            console.log('Initializing Memcache backend (Google App Engine)...');
            this._connectMemcache();
        }
    }

    /**
     * Connect to Redis (Google-free deployment)
     */
    _connectRedis() {
        const redis = require('redis');

        const redisConfig = {
            host: process.env.REDIS_HOST || 'localhost',
            port: parseInt(process.env.REDIS_PORT || '6379'),
            db: parseInt(process.env.REDIS_DB || '0'),
            retry_strategy: function(options) {
                if (options.error && options.error.code === 'ECONNREFUSED') {
                    console.error('Redis connection refused');
                    return new Error('Redis server connection refused');
                }
                if (options.total_retry_time > 1000 * 60 * 60) {
                    return new Error('Redis retry time exhausted');
                }
                if (options.attempt > 10) {
                    return undefined;
                }
                return Math.min(options.attempt * 100, 3000);
            }
        };

        if (process.env.REDIS_PASSWORD) {
            redisConfig.password = process.env.REDIS_PASSWORD;
        }

        this.client = redis.createClient(redisConfig);

        this.client.on('connect', () => {
            console.log('✓ Redis connected successfully');
            this.connected = true;
        });

        this.client.on('error', (err) => {
            console.error('Redis error:', err);
            this.connected = false;
        });

        this.client.on('end', () => {
            console.log('Redis connection closed');
            this.connected = false;
        });
    }

    /**
     * Connect to Memcache (Google App Engine deployment)
     */
    _connectMemcache() {
        const memcache = require('memcache');
        this.client = new memcache.Client();
        this.client.connect();
        this.connected = true;
        console.log('✓ Memcache connected');
    }

    /**
     * Get a value from cache
     * @param {string} key - Cache key
     * @param {function} callback - Callback function(error, result)
     */
    get(key, callback) {
        if (!this.connected) {
            return callback(new Error('Cache not connected'), null);
        }

        if (this.backend === 'redis') {
            this.client.get(key, (err, result) => {
                if (err) {
                    console.error(`Redis GET error for key ${key}:`, err);
                    return callback(err, null);
                }
                // Redis returns null for non-existent keys, Memcache returns undefined/false
                callback(null, result);
            });
        } else {
            // Memcache (original behavior)
            this.client.get(key, callback);
        }
    }

    /**
     * Set a value in cache with TTL
     * @param {string} key - Cache key
     * @param {string} value - Value to store (must be string)
     * @param {number} ttl - Time to live in seconds
     * @param {function} callback - Optional callback function(error, result)
     */
    set(key, value, ttl, callback) {
        if (!this.connected) {
            const error = new Error('Cache not connected');
            if (callback) return callback(error);
            console.error(error.message);
            return;
        }

        if (this.backend === 'redis') {
            // Redis SETEX: set with expiration
            this.client.setex(key, ttl, value, (err, result) => {
                if (err) {
                    console.error(`Redis SET error for key ${key}:`, err);
                    if (callback) callback(err);
                } else {
                    if (callback) callback(null, result);
                }
            });
        } else {
            // Memcache (original behavior)
            // Note: Memcache set() doesn't typically use a callback, but we support it
            this.client.set(key, value, ttl);
            if (callback) callback(null, true);
        }
    }

    /**
     * Delete a value from cache
     * @param {string} key - Cache key
     * @param {function} callback - Optional callback function(error, result)
     */
    delete(key, callback) {
        if (!this.connected) {
            const error = new Error('Cache not connected');
            if (callback) return callback(error);
            console.error(error.message);
            return;
        }

        if (this.backend === 'redis') {
            this.client.del(key, (err, result) => {
                if (err) {
                    console.error(`Redis DEL error for key ${key}:`, err);
                    if (callback) callback(err);
                } else {
                    if (callback) callback(null, result);
                }
            });
        } else {
            // Memcache
            this.client.delete(key);
            if (callback) callback(null, true);
        }
    }

    /**
     * Close the cache connection
     */
    close() {
        if (this.backend === 'redis' && this.client) {
            this.client.quit();
            console.log('Redis connection closed');
        }
        // Memcache doesn't need explicit closing
        this.connected = false;
    }

    /**
     * Get backend information
     */
    getInfo() {
        return {
            backend: this.backend,
            connected: this.connected,
            config: this.backend === 'redis' ? {
                host: process.env.REDIS_HOST || 'localhost',
                port: process.env.REDIS_PORT || 6379,
                db: process.env.REDIS_DB || 0
            } : {
                type: 'Google App Engine Memcache'
            }
        };
    }
}

module.exports = CacheAdapter;
