package com.heng.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heng.domain.VStock;
import com.heng.service.VStockService;
import com.heng.mapper.VStockMapper;
import org.springframework.stereotype.Service;

/**
* @author heng
* @description 针对表【v_stock(SKU 库存权威表)】的数据库操作Service实现
* @createDate 2026-10-09 13:31:17
*/
@Service
public class VStockServiceImpl extends ServiceImpl<VStockMapper, VStock>
    implements VStockService{

}




