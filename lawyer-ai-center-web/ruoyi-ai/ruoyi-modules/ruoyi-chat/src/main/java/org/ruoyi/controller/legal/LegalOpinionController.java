package org.ruoyi.controller.legal;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.chat.domain.bo.chat.ChatMessageBo;
import org.ruoyi.service.chat.ChatSessionOwnershipGuard;
import org.ruoyi.service.chat.IChatMessageService;
import org.ruoyi.service.chat.IChatSessionService;
import org.ruoyi.service.legal.LegalOpinionDocument;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

/** Opinion history and export share the existing tenant-scoped, personal session ownership. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/legal/opinion")
public class LegalOpinionController {
    private final ChatSessionOwnershipGuard ownership;
    private final IChatSessionService sessions;
    private final IChatMessageService messages;
    public record History(Long sessionId,String sessionTitle,String input,String content) {}
    public record ExportRequest(Long sessionId,String content,String format) {}
    private Long owned(Long id) {
        Long user=LoginHelper.getUserId();
        try { ownership.requireOwned(user,id); }
        catch(IllegalArgumentException e){throw new ServiceException("意见书不可用",404);}
        var session=sessions.queryById(id);
        if(session==null||session.getSessionTitle()==null||!session.getSessionTitle().startsWith("法律意见书 - "))throw new ServiceException("意见书不可用",404);
        return user;
    }
    @GetMapping("/{id}")
    public R<History> history(@PathVariable Long id) {
        Long user=owned(id);var query=new ChatMessageBo();query.setUserId(user);query.setSessionId(id);
        String input="",content="";
        for(var message:messages.queryList(query)) {
            if("user".equals(message.getRole())&&message.getContent()!=null&&message.getContent().startsWith(LegalOpinionDocument.INPUT_MARKER)) { input=message.getContent();content=""; }
            if("assistant".equals(message.getRole()))content=message.getContent();
        }
        return R.ok(new History(id,sessions.queryById(id).getSessionTitle(),input,content));
    }
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody ExportRequest request) {
        owned(request.sessionId());
        var document=LegalOpinionDocument.parse(request.content());
        boolean markdown="md".equals(request.format());
        if(!markdown&&!"docx".equals(request.format()))throw new ServiceException("仅支持 Word 或 Markdown 格式",400);
        byte[] bytes=markdown?LegalOpinionDocument.markdown(document).getBytes(StandardCharsets.UTF_8):LegalOpinionDocument.word(document);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(markdown?"text/markdown;charset=UTF-8":"application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=legal-opinion."+(markdown?"md":"docx"))
            .header(HttpHeaders.CACHE_CONTROL,"no-store").body(bytes);
    }
}
