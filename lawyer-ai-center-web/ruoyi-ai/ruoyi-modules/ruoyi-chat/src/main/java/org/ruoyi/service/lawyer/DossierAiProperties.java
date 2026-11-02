package org.ruoyi.service.lawyer;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;

/** Trusted deployment metadata, not a consent switch. Empty/unknown routes refuse model processing. */
@Data @Component @ConfigurationProperties(prefix="legal.dossier.ai")
public class DossierAiProperties {
    private long tokenBudget=1_000_000;
    private int pageBudget=500;
    private Map<Long,String> modelRegions=new HashMap<>();
}
