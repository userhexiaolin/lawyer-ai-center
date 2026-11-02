package org.ruoyi.controller.legal;

import cn.dev33.satoken.annotation.*;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.entity.legal.LegalDocTemplateType;
import org.ruoyi.service.legal.DocTemplateTypeService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @SaCheckLogin
@RequestMapping("/legal/doc-template-type")
public class DocTemplateTypeController {
    private final DocTemplateTypeService service;
    @GetMapping("/options")
    public R<List<DocTemplateTypeService.Option>> options() { return R.ok(service.options(true)); }
    @SaCheckPermission("legal:doc-template-type:list") @GetMapping("/list")
    public R<List<LegalDocTemplateType>> list(@RequestParam(value="name", required=false) String name) { return R.ok(service.list(name)); }
    @SaCheckPermission("legal:doc-template-type:add") @PostMapping
    public R<String> create(@RequestBody DocTemplateTypeService.Input input) { return R.ok("操作成功", service.create(input)); }
    @SaCheckPermission("legal:doc-template-type:edit") @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody DocTemplateTypeService.Input input) { service.update(id, input); return R.ok(); }
    @SaCheckPermission("legal:doc-template-type:remove") @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable("id") Long id) { service.remove(id); return R.ok(); }
}
