package org.ruoyi.controller.lawyer;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.service.lawyer.DossierEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @SaCheckLogin @RequiredArgsConstructor
@RequestMapping("/lawyer/case/{caseId}/dossier")
public class DossierEventController {
    private final DossierEventService service;
    @ExceptionHandler(ServiceException.class) public ResponseEntity<R<Void>> error(ServiceException e){int code=e.getCode()==null?500:e.getCode();return ResponseEntity.status(code>=400&&code<=599?code:500).body(R.fail(code,e.getMessage()));}
    @ExceptionHandler(Exception.class) public ResponseEntity<R<Void>> unavailable(){return ResponseEntity.status(500).body(R.fail(500,"事件操作未完成，输入和已保存历史保留，请重试"));}
    @GetMapping("/events") public R<?> list(@PathVariable Long caseId,@ModelAttribute DossierEventService.Filter filter){return R.ok(service.list(caseId,filter));}
    @GetMapping("/events/{id}") public R<?> detail(@PathVariable Long caseId,@PathVariable Long id){return R.ok(service.detail(caseId,id));}
    @PostMapping("/events") public R<?> create(@PathVariable Long caseId,@RequestBody DossierEventService.Edit value){return R.ok(service.create(caseId,value));}
    @PatchMapping("/events/{id}") public R<?> edit(@PathVariable Long caseId,@PathVariable Long id,@RequestBody DossierEventService.Edit value){return R.ok(service.edit(caseId,id,value));}
    @PostMapping("/events/{id}/split") public R<?> split(@PathVariable Long caseId,@PathVariable Long id,@RequestBody DossierEventService.Split value){return R.ok(service.split(caseId,id,value));}
    @PostMapping("/events/merge") public R<?> merge(@PathVariable Long caseId,@RequestBody DossierEventService.Merge value){return R.ok(service.merge(caseId,value));}
    @PostMapping("/events/group") public R<?> group(@PathVariable Long caseId,@RequestBody DossierEventService.Group value){return R.ok(service.group(caseId,value));}
    @PostMapping("/events/from-claims") public R<?> fromClaims(@PathVariable Long caseId,@RequestBody DossierEventService.Import value){return R.ok(service.fromClaims(caseId,value));}
    @GetMapping("/timeline/graph-data") public R<?> graph(@PathVariable Long caseId,@ModelAttribute DossierEventService.Filter filter){return R.ok(service.graph(caseId,filter));}
    @PostMapping("/events/precedence") public R<?> order(@PathVariable Long caseId,@RequestBody DossierEventService.Order value){return R.ok(service.order(caseId,value));}
    @PostMapping("/events/precedence/{edgeId}/exclude") public R<?> excludeOrder(@PathVariable Long caseId,@PathVariable Long edgeId,@RequestBody DossierEventService.OrderDecision value){service.excludeOrder(caseId,edgeId,value);return R.ok();}
}
