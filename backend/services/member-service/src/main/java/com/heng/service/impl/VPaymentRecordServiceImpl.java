package com.heng.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heng.domain.VPaymentRecord;
import com.heng.service.VPaymentRecordService;
import com.heng.mapper.VPaymentRecordMapper;
import org.springframework.stereotype.Service;

/**
* @author heng
* @description 针对表【v_payment_record(支付流水和回调幂等记录)】的数据库操作Service实现
* @createDate 2026-10-09 13:31:17
*/
@Service
public class VPaymentRecordServiceImpl extends ServiceImpl<VPaymentRecordMapper, VPaymentRecord>
    implements VPaymentRecordService{

}




