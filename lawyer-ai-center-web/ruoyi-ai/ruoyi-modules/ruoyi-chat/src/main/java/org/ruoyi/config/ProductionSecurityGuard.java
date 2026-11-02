package org.ruoyi.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fail closed before production starts with example credentials. Values never enter errors. */
@Component @Profile("prod") @RequiredArgsConstructor
public class ProductionSecurityGuard {
    private final Environment environment;
    @PostConstruct public void validate(){
        require("sa-token.jwt-secret-key",32);
        require("spring.datasource.dynamic.datasource.master.password",12);
        require("spring.data.redis.password",12);
        require("spring.boot.admin.client.password",20);
    }
    private void require(String key,int minimum){
        String value=environment.getProperty(key);
        if(value==null||value.length()<minimum||value.contains("${")||value.equalsIgnoreCase("abcdefghijklmnopqrstuvwxyz")||value.toLowerCase(java.util.Locale.ROOT).contains("changeme"))
            throw new IllegalStateException("生产配置缺少安全凭据或仍为示例值: "+key);
    }
}
