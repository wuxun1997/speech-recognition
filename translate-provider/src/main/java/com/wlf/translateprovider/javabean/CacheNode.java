package com.wlf.translateprovider.javabean;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 内部类，缓存对象，按失效时间排序，越早失效越前
 * @author wulf
 * @since 20200421
 */
@Getter
@Setter
@ToString
public class CacheNode implements Comparable<CacheNode> {
    private String key;
    private AtomicLong callQuantity;
    private long expireTime;

    public CacheNode(String key, AtomicLong callQuantity, long expireTime) {
        this.key = key;
        this.callQuantity = callQuantity;
        this.expireTime = expireTime;
    }


    @Override
    public int compareTo(CacheNode o) {
        long dif = this.expireTime - o.expireTime;
        if (dif > 0) {
            return 1;
        } else if (dif < 0) {
            return -1;
        }
        return 0;
    }
}
