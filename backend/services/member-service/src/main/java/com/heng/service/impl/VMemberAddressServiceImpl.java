package com.heng.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heng.domain.VMemberAddress;
import com.heng.service.VMemberAddressService;
import com.heng.mapper.VMemberAddressMapper;
import org.springframework.stereotype.Service;

/**
* @author heng
* @description 针对表【v_member_address(会员收货地址)】的数据库操作Service实现
* @createDate 2026-10-09 13:31:17
*/
@Service
public class VMemberAddressServiceImpl extends ServiceImpl<VMemberAddressMapper, VMemberAddress>
    implements VMemberAddressService{

}




