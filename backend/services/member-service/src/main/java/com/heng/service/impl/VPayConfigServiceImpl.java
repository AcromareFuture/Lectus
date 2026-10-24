package com.heng.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heng.domain.VPayConfig;
import com.heng.service.VPayConfigService;
import com.heng.mapper.VPayConfigMapper;
import org.springframework.stereotype.Service;

/**
* @author heng
* @description 针对表【v_pay_config(支付方式配置（敏感密钥不落库）)】的数据库操作Service实现
* @createDate 2026-10-09 13:31:17
*/
@Service
public class VPayConfigServiceImpl extends ServiceImpl<VPayConfigMapper, VPayConfig>
    implements VPayConfigService{

}




