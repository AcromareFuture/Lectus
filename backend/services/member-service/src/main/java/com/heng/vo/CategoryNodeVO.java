package com.heng.vo;

import java.util.List;

public record CategoryNodeVO(Long categoryId, String name, String iconUrl, String imageUrl,
                            Integer sort, List<CategoryNodeVO> children) {
}
