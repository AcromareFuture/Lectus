package com.heng.logistics;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component @ConfigurationProperties(prefix="viridac.amap") @Getter @Setter
public class AmapProperties {
    private boolean enabled=false;
    private String webKey="";
    private String jsKey="";
    private boolean jsProxyEnabled=false;
}
