package org.ruoyi.controller.chat;

import cn.dev33.satoken.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.service.billing.LawyerBillingService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor @SaCheckLogin
@RequestMapping("/lawyer/billing")
public class LawyerBillingController {
    private final LawyerBillingService service;
    public record Create(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{1,40}") String planCode,
                         @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{20,64}") String idempotencyKey) {}
    public record Adjustment(@NotNull Long userId,int chatDelta,int reviewDelta,
                             @Min(1) @Max(3650) int validDays,@NotBlank @Size(max=200) String reason) {}
    @GetMapping("/plans") public R<LawyerBillingService.Catalog> plans(){return R.ok(service.plans());}
    @GetMapping("/account") public R<LawyerBillingService.Account> account(){return R.ok(service.account());}
    @GetMapping("/orders") public R<List<LawyerBillingService.OrderView>> orders(@RequestParam(defaultValue="1") int page){return R.ok(service.orders(page));}
    @PostMapping("/orders") public R<LawyerBillingService.OrderView> create(@Valid @RequestBody Create input){return R.ok(service.create(input.planCode,input.idempotencyKey));}
    @GetMapping("/orders/{no}") public R<LawyerBillingService.OrderView> order(@PathVariable String no,@RequestParam(defaultValue="false") boolean refresh){return R.ok(service.order(no,refresh));}
    @PostMapping("/orders/{no}/close") public R<LawyerBillingService.OrderView> close(@PathVariable String no){return R.ok(service.close(no));}
    @PostMapping("/admin/adjust") @SaCheckPermission("lawyer:billing:adjust")
    @Log(title="个人额度应急调整",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public R<Void> adjust(@Valid @RequestBody Adjustment input){service.adjust(input.userId,input.chatDelta,input.reviewDelta,input.validDays,input.reason);return R.ok();}
    @SaIgnore @PostMapping(value="/notify/alipay",consumes="application/x-www-form-urlencoded",produces="text/plain;charset=UTF-8")
    public String notifyAlipay(HttpServletRequest request){
        try {
            var raw=request.getParameterMap();if(raw.size()>80)return "failure";
            Map<String,String> parameters=new TreeMap<>();
            for(var field:raw.entrySet()){
                if(field.getKey().length()>100||field.getValue().length!=1||field.getValue()[0].length()>10000)return "failure";
                parameters.put(field.getKey(),field.getValue()[0]);
            }
            return service.notifyAlipay(parameters)?"success":"failure";
        }catch(Exception ignored){return "failure";}
    }
}
