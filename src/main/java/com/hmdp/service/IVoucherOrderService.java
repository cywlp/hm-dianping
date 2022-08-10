package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 珠代
 * @since 2022-6-20
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVocher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
