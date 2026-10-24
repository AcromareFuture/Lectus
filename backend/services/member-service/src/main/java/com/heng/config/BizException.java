package com.heng.config;

/** 业务预期内的失败，返回 400 和可直接展示给用户的文案。 */
public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
