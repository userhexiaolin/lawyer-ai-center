package org.ruoyi.service.billing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties(prefix="lawyer.billing")
public class BillingProperties {
    /** Local deployments bypass all usage checks/debits as well as purchasing. */
    private boolean enabled=false;
    private boolean sandbox=false;
    private String appId="";
    private String sellerId="";
    private String privateKeyFile="";
    private String alipayPublicKeyFile="";
    private String notifyUrl="";
}
