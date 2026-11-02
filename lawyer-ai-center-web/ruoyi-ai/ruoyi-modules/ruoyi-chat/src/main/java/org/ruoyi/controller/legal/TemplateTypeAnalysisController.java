package org.ruoyi.controller.legal;

import cn.dev33.satoken.annotation.*;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.legal.TemplateTypeAnalysis;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@SaCheckLogin
@RequestMapping("/legal/doc-template")
public class TemplateTypeAnalysisController {
    private final TemplateTypeAnalysis analysis;

    @SaCheckPermission(value = {"legal:doc-template:add", "legal:doc-template:edit"}, mode = SaMode.OR)
    @PostMapping(value = "/classify", consumes = "multipart/form-data")
    public R<TemplateTypeAnalysis.Analysis> classify(@RequestPart("file") MultipartFile file) {
        return R.ok(analysis.analyze(file));
    }
    @SaCheckPermission("legal:doc-template:edit")
    @PostMapping("/{id}/classify")
    public R<TemplateTypeAnalysis.ExistingAnalysis> classifyExisting(@PathVariable Long id) {
        return R.ok(analysis.analyzeExisting(id));
    }
}
