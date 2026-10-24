package com.heng.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heng.domain.VMember;
import com.heng.service.VMemberService;
import com.heng.mapper.VMemberMapper;
import org.springframework.stereotype.Service;

/**
* @author heng
* @description 针对表【v_member(Viridac C端会员)】的数据库操作Service实现
* @createDate 2026-10-09 13:31:17
*/
@Service
public class VMemberServiceImpl extends ServiceImpl<VMemberMapper, VMember>
    implements VMemberService{

}




