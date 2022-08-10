package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author :珠代
 * @description :
 * @create :2022-05-24 21:17:00
 */
@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit){
        //设计逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透解决方案
     * @param keyPrefix 键前缀
     * @param id id
     * @param type 值的类型
     * @param dbFallback 函数操作
     * @param time 时间
     * @param unit 时间单位
     * @param <R> 泛型 value的类型
     * @param <ID> id的类型
     * @return r
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String redisKey = keyPrefix + id;
        // 1. 从redis里查询
        String shopJson = stringRedisTemplate.opsForValue().get(redisKey);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，返回
            return JSONUtil.toBean(shopJson, type);
        }
        //此时剩下空("")和null两种情况
        //判断是否是空值
        if (shopJson != null) {
            // 返回错误信息
            return null;
        }
        // 4. 不存在，从数据库查
        R r = dbFallback.apply(id);
        if (r == null) {
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(redisKey,"", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 5. 不存在返回错误
            return null;
        }
        // 6. 存在，写入缓存
        this.set(redisKey, r, time, unit);
        // 7. 返回
        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 缓存击穿解决方案：逻辑过期
     * @param keyPrefix 键前缀
     * @param id id
     * @param type 值的类型
     * @param dbFallback 函数操作
     * @param time 时间
     * @param unit 时间单位
     * @param <R> 泛型 value的类型
     * @param <ID> id的类型
     * @return r
     */
    public <R, ID> R queryWithLogicExpire( String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String redisKey = keyPrefix + id;
        // 1. 从redis里查询
        String shopJson = stringRedisTemplate.opsForValue().get(redisKey);
        // 2. 判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3. 不存在，返回
            return null;
        }
        // 4.命中 把JSON字符串反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //Shop shop = (Shop) redisData.getData();
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期返回店铺
            return r;
        }
        // 5.2 过期，重建缓存
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否成功
        if (isLock) {
            // 6.3 成功，开启独立线程，实现缓存重建
            // 再次检查是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 5.1 未过期返回店铺
                return r;
            }
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入Redis
                    this.setWithLogicExpire(redisKey, r1, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4 失败，返回过期的店铺信息
        return r;
    }

    /**
     * 缓存击穿解决方案：互斥锁
     * @param keyPrefix 键前缀
     * @param id id
     * @param type 值的类型
     * @param dbFallback 函数操作
     * @param time 时间
     * @param unit 时间单位
     * @param <R> 泛型 value的类型
     * @param <ID> id的类型
     * @return r
     */
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String redisKey = keyPrefix + id;
        // 1. 从redis里查询
        String shopJson = stringRedisTemplate.opsForValue().get(redisKey);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，返回
            return JSONUtil.toBean(shopJson, type);
        }
        //判断是否是空值
        if (shopJson != null) {
            // 返回错误信息
            return null;
        }
        // 4. 实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2 判断是否成功
            if (!isLock){
                // 4.3 失败，休眠并重试
                Thread.sleep(50);
                queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }

            // 4.4 成功，从数据库查
            r = dbFallback.apply(id);
            // 模拟重建延时
            Thread.sleep(200);
            if (r == null) {
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(redisKey,"", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 5. 不存在返回错误
                return null;
            }
            // 6. 存在，写入缓存
            this.set(redisKey, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 7. 释放互斥锁
            unlock(lockKey);
        }

        // 8. 返回
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete( key);
    }
}
