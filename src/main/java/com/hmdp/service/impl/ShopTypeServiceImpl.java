package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 珠代
 * @since 2022-6-20
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryCache() {
        String redisKey = CACHE_SHOP_TYPE;
        // 1. 从redis里查询
        String shopJson = stringRedisTemplate.opsForValue().get(redisKey);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，返回
            List<ShopType> shopTypeList = JSONUtil.toList(shopJson,ShopType.class);
            return Result.ok(shopTypeList);
        }
        // 4. 不存在，从数据库查
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        if (shopTypeList == null) {
            // 5. 不存在返回错误
            return Result.fail("店铺类型不存在!");
        }
        // 6. 存在，写入缓存
        stringRedisTemplate.opsForValue().set(redisKey,JSONUtil.toJsonStr(shopTypeList));
        // 7. 返回
        return Result.ok(shopTypeList);
    }
}
