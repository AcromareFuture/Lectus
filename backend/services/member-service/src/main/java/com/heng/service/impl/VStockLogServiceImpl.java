package com.heng.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heng.domain.VStockLog;
import com.heng.service.VStockLogService;
import com.heng.mapper.VStockLogMapper;
import org.springframework.stereotype.Service;

/**
* @author heng
* @description 针对表【v_stock_log(库存变动日志)】的数据库操作Service实现
* @createDate 2026-10-09 13:31:17
*/
@Service
public class VStockLogServiceImpl extends ServiceImpl<VStockLogMapper, VStockLog>
    implements VStockLogService{

}




