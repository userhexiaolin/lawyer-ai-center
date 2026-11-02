package org.ruoyi.controller.legal;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.legal.FactSearchService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/legal/fact-search")
public class FactSearchController {
    private final FactSearchService service;
    @GetMapping("/libraries") public R<List<FactSearchService.Library>> libraries(){return R.ok(service.libraries());}
    @PostMapping public R<FactSearchService.Result> search(@RequestBody FactSearchService.Request request){return R.ok(service.search(request));}
}
