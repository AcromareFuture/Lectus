package com.heng.auth;

public interface SmsGateway {
    boolean available();
    void send(String phone, String scheme, String outId);
    boolean verify(String phone, String scheme, String outId, String code);
}
