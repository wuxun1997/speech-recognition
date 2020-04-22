package com.wlf.translateprovider.utils;

import com.wlf.translateprovider.javabean.CacheNode;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 带过期时间的缓存
 *
 * @author wulf
 * @since 2020/04/21
 */
public class ExpiredCache {

    // 缓存key=接口名，value=接口调用量、过期时间
    private Map<String, CacheNode> cache = new ConcurrentHashMap<>();

    // 重入锁
    private ReentrantLock lock = new ReentrantLock();

    // 失效队列
    private PriorityQueue<CacheNode> queue = new PriorityQueue<>();

    // 启动定时任务，每秒清理一次过期缓存
    private final static ScheduledExecutorService scheduleExe = new ScheduledThreadPoolExecutor(10);

    // 构造函数中启动定时任务，执行对已过期缓存的清理工作，每秒执行一次
    public ExpiredCache() {
        scheduleExe.scheduleAtFixedRate(new CleanExpireCacheTask(), 1L, 1L, TimeUnit.SECONDS);
    }

    /**
     * 内部类，清理过期缓存对象，仅清理过期时间<当前时间的那些过期缓存对象
     */
    private class CleanExpireCacheTask implements Runnable {

        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            // 取出队列中的队头元素，对已过期的元素执行清除计划，剩下没有过期则退出
            while (true) {
                lock.lock();
                try {
                    CacheNode cacheNode = queue.peek();
                    // 已经把队列清空了，或者所有过期元素已清空了，退出
                    if (cacheNode == null || cacheNode.getExpireTime() > currentTime) {
                        return;
                    }

                    // 开始大清理了
                    cache.remove(cacheNode.getKey());
                    queue.poll();
                } finally {
                    lock.unlock();
                }

            }
        }
    }

    /**
     * 加入缓存，设置存活时间
     *
     * @param cacheKey
     * @param tts      缓存存活时间，单位秒
     */
    public AtomicLong set(String cacheKey, long tts) {

        CacheNode oldNode = cache.get(cacheKey);
        if (oldNode == null) {
            // 新创建缓存节点，失效时间=当前时间+缓存存活时间
            AtomicLong qps = new AtomicLong(1);
            CacheNode newNode = new CacheNode(cacheKey, qps, System.currentTimeMillis() + tts * 1000);
            CacheNode tmp = cache.putIfAbsent(cacheKey, newNode);

            // 若缓存中已存在缓存节点，不需要更新过期时间，自增qps
            if (tmp != null) {
                qps = tmp.getCallQuantity();
                qps.incrementAndGet();
                cache.put(cacheKey, tmp);
            } else {
                // 放入缓存，加入过期队列
                queue.add(newNode);
            }

        } else {
            // 已存在缓存节点，自增qps
            AtomicLong oldQps = oldNode.getCallQuantity();
            oldQps.incrementAndGet();
            cache.put(cacheKey, oldNode);
        }
        return cache.get(cacheKey).getCallQuantity();
    }
}
