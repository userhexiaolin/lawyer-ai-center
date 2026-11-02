package org.ruoyi.controller.chat;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.chat.SessionBindingService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@RequestMapping("/legal/session")
public class SessionBindingController {
 private final SessionBindingService service;
 @GetMapping("/{id}/binding") public R<JsonNode> get(@PathVariable Long id){return R.ok(service.load(id));}
 @PutMapping("/{id}/binding") public R<Void> put(@PathVariable Long id,@RequestBody JsonNode value){service.save(id,value);return R.ok();}
}
