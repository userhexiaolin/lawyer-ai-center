package org.ruoyi.controller.lawyer;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;import org.ruoyi.service.lawyer.LawyerWorkTimeService;import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/lawyer/time")
public class LawyerWorkTimeController {
 private final LawyerWorkTimeService service;
 public record Start(@Positive Long caseId,@Positive Long sessionId,@Size(max=500,message="备注不能超过500字")String description){}
 @PostMapping("/start")public R<LawyerWorkTimeService.Entry> start(@Valid @RequestBody Start body){return R.ok(service.start(body.caseId(),body.sessionId(),body.description()));}
 @PostMapping("/{id}/stop")public R<LawyerWorkTimeService.Entry> stop(@PathVariable Long id){return R.ok(service.stop(id));}
 @GetMapping public R<LawyerWorkTimeService.Summary> summary(@RequestParam(required=false)Long caseId,@RequestParam(defaultValue="1")int pageNum){return R.ok(service.summary(caseId,pageNum));}
}
