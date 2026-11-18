package com.aermini.report.database;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import com.aermini.report.AerReport;
import com.aermini.report.utils.RedisKeys;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

public class RedisManager {
    private static final int MAX_FAILS = 3;
    private static final long BREAK_MILLIS = 30000L;

    private final AerReport plugin;
    private volatile JedisPool pool;
    private volatile boolean available;
    private final AtomicInteger failCount = new AtomicInteger();
    private final AtomicBoolean probing = new AtomicBoolean();
    private volatile long breakUntil;

    private volatile String host;
    private volatile int port;
    private volatile int timeout;
    private volatile String password;
    private volatile int database;

    public RedisManager(AerReport plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (pool != null) return;
        if (!plugin.getConfig().getBoolean("redis.enabled", false)) return;

        host = plugin.getConfig().getString("redis.host", "127.0.0.1");
        port = plugin.getConfig().getInt("redis.port", 6379);
        password = plugin.getConfig().getString("redis.password", "");
        database = plugin.getConfig().getInt("redis.database", 0);
        timeout = plugin.getConfig().getInt("redis.timeout", 2000);
        String keyPrefix = plugin.getConfig().getString("redis.key-prefix", "aerreport:");
        int maxTotal = plugin.getConfig().getInt("redis.pool.max-total", 8);
        int maxIdle = plugin.getConfig().getInt("redis.pool.max-idle", 8);
        int minIdle = plugin.getConfig().getInt("redis.pool.min-idle", 1);
        int maxWait = plugin.getConfig().getInt("redis.pool.max-wait", 2000);

        if (host == null || host.trim().isEmpty() || port <= 0 || port > 65535 || timeout <= 0) {
            plugin.getLogger().warning("Redis 配置不完整, 已跳过初始化");
            return;
        }

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal > 0 ? maxTotal : 8);
        poolConfig.setMaxIdle(maxIdle >= 0 ? maxIdle : 8);
        poolConfig.setMinIdle(minIdle >= 0 ? minIdle : 1);
        poolConfig.setMaxWait(Duration.ofMillis(maxWait > 0 ? maxWait : 2000));

        String auth = (password == null || password.isEmpty()) ? null : password;
        JedisPool newPool = null;
        try {
            newPool = new JedisPool(poolConfig, host, port, timeout, auth, database);
            try (Jedis jedis = newPool.getResource()) {
                String pong = jedis.ping();
                if (!"PONG".equalsIgnoreCase(pong)) {
                    throw new IllegalStateException("PING 返回 " + pong);
                }
            }
            pool = newPool;
            available = true;
            failCount.set(0);
            probing.set(false);
            breakUntil = 0L;
            RedisKeys.setPrefix(keyPrefix);
            plugin.getLogger().info("Redis 连接成功 -> " + host + ":" + port + ", 数据库 " + database);
        } catch (Exception e) {
            available = false;
            pool = null;
            if (newPool != null) {
                try {
                    newPool.close();
                } catch (Exception ignored) {
                }
            }
            plugin.getLogger().warning("Redis 连接失败, 已回退到本地模式 -> " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        JedisPool current = pool;
        if (current == null || !available) return false;
        if (failCount.get() < MAX_FAILS) return true;
        return System.currentTimeMillis() >= breakUntil;
    }

    public <T> T call(Function<Jedis, T> action, T fallback) {
        JedisPool current = pool;
        if (current == null || !isAvailable()) return fallback;
        if (failCount.get() >= MAX_FAILS && !probing.compareAndSet(false, true)) return fallback;

        for (int i = 0; i < 2; i++) {
            try (Jedis jedis = current.getResource()) {
                T result = action.apply(jedis);
                failCount.set(0);
                probing.set(false);
                breakUntil = 0L;
                return result;
            } catch (JedisException e) {
                probing.set(false);
                int fails = failCount.incrementAndGet();
                if (fails >= MAX_FAILS) {
                    breakUntil = System.currentTimeMillis() + BREAK_MILLIS;
                }
                plugin.getLogger().warning("Redis 操作失败(" + fails + ")" +
                        (fails >= MAX_FAILS ? ", 熔断 30 秒" : "") + " -> " + e.getMessage());
            } catch (Exception e) {
                probing.set(false);
                plugin.getLogger().warning("Redis 调用异常, 不重试 -> " + e.getMessage());
                return fallback;
            }
        }
        return fallback;
    }

    public void run(Consumer<Jedis> action) {
        this.<Void>call(jedis -> {
            action.accept(jedis);
            return null;
        }, null);
    }

    public long publish(String channel, String message) {
        Long result = call(jedis -> jedis.publish(channel, message), 0L);
        return result == null ? 0L : result;
    }

    public Jedis newConnection() {
        String currentHost = host;
        if (currentHost == null || currentHost.trim().isEmpty() || port <= 0 || timeout <= 0) return null;

        Jedis jedis = null;
        try {
            jedis = new Jedis(currentHost, port, timeout);
            if (password != null && !password.isEmpty()) {
                jedis.auth(password);
            }
            if (database > 0) {
                jedis.select(database);
            }
            return jedis;
        } catch (Exception e) {
            plugin.getLogger().warning("Redis 订阅连接创建失败 -> " + e.getMessage());
            if (jedis != null) {
                try {
                    jedis.close();
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    public void close() {
        available = false;
        probing.set(false);
        JedisPool current = pool;
        pool = null;
        if (current != null) {
            try {
                current.close();
                plugin.getLogger().info("Redis 连接池已关闭");
            } catch (Exception e) {
                plugin.getLogger().warning("Redis 连接池关闭失败 -> " + e.getMessage());
            }
        }
    }
}
