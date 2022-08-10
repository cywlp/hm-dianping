package com.hmdp.utils;

/**
 * @author :珠代
 * @description :
 * @create :2022-05-30 20:41:00
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期自动释放
     * @return true成功 false失败
     */
    boolean tryLock(Long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();
}
