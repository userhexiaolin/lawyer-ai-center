package org.ruoyi.controller.legal;

import org.junit.jupiter.api.*;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.vo.chat.ChatSessionVo;
import org.ruoyi.service.chat.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.ruoyi.common.chat.domain.bo.chat.ChatMessageBo;
import org.ruoyi.common.chat.domain.vo.chat.ChatMessageVo;
import org.ruoyi.service.legal.LegalOpinionDocument;
import java.util.*;

@Tag("dev")
class LegalOpinionControllerTest {
    private static ChatMessageVo message(String role,String content) {var value=new ChatMessageVo();value.setRole(role);value.setContent(content);return value;}
    private static String complete() {return "{\"sections\":["+String.join(",",LegalOpinionDocument.KEYS.stream().map(key->"{\"key\":\""+key+"\",\"content\":\"正文\",\"citations\":[]}").toList())+"]}";}
    @Test void personalHistoryReturnsLatestAnswerAndOriginalInputRatherThanRepairInstruction() {
        var sessions=mock(IChatSessionService.class);var session=new ChatSessionVo();session.setSessionTitle("法律意见书 - 测试");when(sessions.queryById(2L)).thenReturn(session);
        var messages=mock(IChatMessageService.class);
        String input=LegalOpinionDocument.INPUT_MARKER+"\n{}";
        when(messages.queryList(any())).thenReturn(List.of(message("user",input),message("assistant","{}"),message("user","补全六章"),message("assistant",complete())));
        var controller=new LegalOpinionController(mock(ChatSessionOwnershipGuard.class),sessions,messages);
        try(var login=mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1L);
            var history=controller.history(2L).getData();assertEquals(input,history.input());assertEquals(complete(),history.content());
            var query=ArgumentCaptor.forClass(ChatMessageBo.class);verify(messages).queryList(query.capture());assertEquals(1L,query.getValue().getUserId());assertEquals(2L,query.getValue().getSessionId());
            var word=controller.export(new LegalOpinionController.ExportRequest(2L,complete(),"docx"));assertEquals((byte)'P',word.getBody()[0]);assertEquals((byte)'K',word.getBody()[1]);
            var md=controller.export(new LegalOpinionController.ExportRequest(2L,complete(),"md"));assertTrue(new String(md.getBody(),java.nio.charset.StandardCharsets.UTF_8).contains("6. 引用清单"));
            assertThrows(ServiceException.class,()->controller.export(new LegalOpinionController.ExportRequest(2L,"{}","docx")));
        }
    }
    @Test void rejectsForeignHistoryAndExportBeforeReadingMessagesOrGeneratingFiles() {
        var guard=mock(ChatSessionOwnershipGuard.class);var sessions=mock(IChatSessionService.class);var messages=mock(IChatMessageService.class);
        var controller=new LegalOpinionController(guard,sessions,messages);
        try(var login=mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1L);
            doThrow(new IllegalArgumentException("unavailable")).when(guard).requireOwned(1L,2L);
            assertThrows(ServiceException.class,()->controller.history(2L));
            assertThrows(ServiceException.class,()->controller.export(new LegalOpinionController.ExportRequest(2L,"{}","docx")));
            verifyNoInteractions(sessions,messages);
        }
    }
    @Test void ordinarySessionCannotBeExportedAsOpinion() {
        var sessions=mock(IChatSessionService.class);var session=new ChatSessionVo();session.setSessionTitle("普通聊天");when(sessions.queryById(2L)).thenReturn(session);
        var controller=new LegalOpinionController(mock(ChatSessionOwnershipGuard.class),sessions,mock(IChatMessageService.class));
        try(var login=mockStatic(LoginHelper.class)) {login.when(LoginHelper::getUserId).thenReturn(1L);assertThrows(ServiceException.class,()->controller.export(new LegalOpinionController.ExportRequest(2L,"{}","docx")));}
    }
}
