package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 珠代
 * @since 2022-6-20
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryCache();
}
