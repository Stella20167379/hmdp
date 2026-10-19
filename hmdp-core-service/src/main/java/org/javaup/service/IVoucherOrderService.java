package org.javaup.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.javaup.dto.CancelVoucherOrderDto;
import org.javaup.dto.GetVoucherOrderByVoucherIdDto;
import org.javaup.dto.GetVoucherOrderDto;
import org.javaup.dto.Result;
import org.javaup.entity.VoucherOrder;
import org.javaup.kafka.message.SeckillVoucherMessage;
import org.javaup.message.MessageExtend;

/**
 * @description: 优惠券订单 接口
 **/
public interface IVoucherOrderService extends IService<VoucherOrder> {

//    这是下单接口，但只是扣减库存尚未真正写入订单
    Result<Long> seckillVoucher(Long voucherId);

    void createVoucherOrderV1(VoucherOrder voucherOrder);

//    这个才是kafka消息被消费时，真正创建订单的入口
    boolean createVoucherOrderV2(MessageExtend<SeckillVoucherMessage> message);
    
    Long getSeckillVoucherOrder(GetVoucherOrderDto getVoucherOrderDto);
    
    Boolean cancel(CancelVoucherOrderDto cancelVoucherOrderDto);
    
    boolean autoIssueVoucherToEarliestSubscriber(final Long voucherId, final Long excludeUserId);
    
    Long getSeckillVoucherOrderIdByVoucherId(GetVoucherOrderByVoucherIdDto getVoucherOrderByVoucherIdDto);
}
