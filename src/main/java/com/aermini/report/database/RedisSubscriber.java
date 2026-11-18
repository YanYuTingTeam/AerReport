package com.aermini.report.database;

import java.util.function.Consumer;

import org.bukkit.Bukkit;

import com.aermini.report.AerReport;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class RedisSubscriber {
    private final AerReport plugin;
    private final RedisManager redis;
    private volatile boolean running;
    private volatile Thread thread;
    private volatile Jedis currentJedis;
    private volatile JedisPubSub currentPubSub;

    public RedisSubscriber(AerReport plugin, RedisManager redis) {
        this.plugin = plugin;
        this.redis = redis;
    }

    public void start(final String channel, final Consumer<String> handler) {
        if (running || channel == null || redis == null || !redis.isAvailable()) return;
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                listen(channel, handler);
            }
        }, "AerReport-RedisSub");
        thread.setDaemon(true);
        thread.start();
    }

    private void listen(String channel, Consumer<String> handler) {
        while (running) {
            Jedis jedis = null;
            try {
                jedis = redis.newConnection();
                if (jedis != null) {
                    if (!running) break;
                    currentJedis = jedis;
                    JedisPubSub pubSub = new JedisPubSub() {
                        @Override
                        public void onMessage(String ch, String message) {
                            if (!channel.equals(ch)) return;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (plugin.isEnabled()) handler.accept(message);
                            });
                        }
                    };
                    currentPubSub = pubSub;
                    jedis.subscribe(pubSub, channel);
                }
            } catch (Exception e) {
                if (running) {
                    plugin.getLogger().warning("Redis 订阅连接异常 -> " + e.getMessage());
                }
            } finally {
                currentPubSub = null;
                currentJedis = null;
                if (jedis != null) {
                    try {
                        jedis.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            if (!running) break;
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void close() {
        running = false;
        JedisPubSub pubSub = currentPubSub;
        if (pubSub != null) {
            try {
                pubSub.unsubscribe();
            } catch (Exception ignored) {
            }
        }
        Jedis jedis = currentJedis;
        if (jedis != null) {
            try {
                jedis.close();
            } catch (Exception ignored) {
            }
        }
        Thread currentThread = thread;
        if (currentThread != null) {
            try {
                currentThread.join(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
