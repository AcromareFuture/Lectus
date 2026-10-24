package com.heng.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heng.domain.VOrder;
import com.heng.service.VOrderService;
import com.heng.mapper.VOrderMapper;
import org.springframework.stereotype.Service;

/**
* @author heng
* @description 针对表【v_order(订单主表)】的数据库操作Service实现
* @createDate 2026-10-09 13:31:17
*/
@Service
public class VOrderServiceImpl extends ServiceImpl<VOrderMapper, VOrder>
    implements VOrderService{

}




