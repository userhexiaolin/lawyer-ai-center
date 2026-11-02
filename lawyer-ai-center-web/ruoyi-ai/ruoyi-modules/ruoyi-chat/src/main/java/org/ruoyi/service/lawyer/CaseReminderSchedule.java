package org.ruoyi.service.lawyer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import java.time.*;

@Slf4j @Configuration(proxyBeanMethods=false) @EnableScheduling @RequiredArgsConstructor
@ConditionalOnProperty(prefix="lawyer.reminders",name="enabled",havingValue="true",matchIfMissing=true)
public class CaseReminderSchedule {
    private final CaseReminderDelivery delivery;
    @Scheduled(fixedDelayString="${lawyer.reminders.delay-ms:300000}",initialDelayString="${lawyer.reminders.initial-delay-ms:30000}")
    public void scan(){
        try {var result=delivery.scan(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));if(result.created()>0)log.info("personal_reminders candidates={} created={}",result.candidates(),result.created());}
        catch(RuntimeException error){log.error("personal_reminders scan failed; next scheduled pass will retry, errorType={}",error.getClass().getSimpleName());}
    }
}
