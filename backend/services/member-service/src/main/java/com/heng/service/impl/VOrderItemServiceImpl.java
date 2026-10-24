package com.heng.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heng.domain.VOrderItem;
import com.heng.service.VOrderItemService;
import com.heng.mapper.VOrderItemMapper;
import org.springframework.stereotype.Service;

/**
* @author heng
* @description 针对表【v_order_item(订单商品快照)】的数据库操作Service实现
* @createDate 2026-10-09 13:31:17
*/
@Service
public class VOrderItemServiceImpl extends ServiceImpl<VOrderItemMapper, VOrderItem>
    implements VOrderItemService{

}




