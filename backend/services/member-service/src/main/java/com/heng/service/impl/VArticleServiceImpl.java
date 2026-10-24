package com.heng.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heng.domain.VArticle;
import com.heng.service.VArticleService;
import com.heng.mapper.VArticleMapper;
import org.springframework.stereotype.Service;

/**
* @author heng
* @description 针对表【v_article(文章、公告和 FAQ)】的数据库操作Service实现
* @createDate 2026-10-09 13:31:16
*/
@Service
public class VArticleServiceImpl extends ServiceImpl<VArticleMapper, VArticle>
    implements VArticleService{

}




