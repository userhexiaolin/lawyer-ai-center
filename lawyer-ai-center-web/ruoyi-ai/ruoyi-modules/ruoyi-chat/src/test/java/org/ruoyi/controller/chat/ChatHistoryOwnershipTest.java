package org.ruoyi.controller.chat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.service.chat.*;
import org.ruoyi.common.chat.domain.bo.chat.ChatMessageBo;
import org.ruoyi.common.chat.domain.vo.chat.ChatMessageVo;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.domain.bo.chat.ChatSessionBo;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class ChatHistoryOwnershipTest {
    @Test void messageListOverridesSuppliedOwnerAndChecksSession() {
        var service = mock(IChatMessageService.class); var guard = mock(ChatSessionOwnershipGuard.class);
        var controller = new ChatMessageController(service, guard);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            var bo = new ChatMessageBo(); bo.setUserId(8L); bo.setSessionId(10L);
            controller.list(bo, new PageQuery(20, 1));
            assertEquals(7L, bo.getUserId()); verify(guard).requireOwned(7L, 10L);
        }
    }
    @Test void foreignMessageCannotBeReadOrEdited() {
        var service = mock(IChatMessageService.class); var guard = mock(ChatSessionOwnershipGuard.class);
        var controller = new ChatMessageController(service, guard);
        var value = new ChatMessageVo(); value.setUserId(8L); when(service.queryById(1L)).thenReturn(value);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThrows(RuntimeException.class, () -> controller.getInfo(1L));
            var bo = new ChatMessageBo(); bo.setId(1L);
            assertThrows(RuntimeException.class, () -> controller.edit(bo)); verify(service, never()).updateByBo(any());
        }
    }
    @Test void foreignSessionCannotBeReadUpdatedOrDeleted() {
        var service = mock(IChatSessionService.class); var guard = mock(ChatSessionOwnershipGuard.class);
        var controller = new ChatSessionController(service, guard);
        doThrow(new IllegalArgumentException("Unavailable")).when(guard).requireOwned(7L, 10L);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThrows(IllegalArgumentException.class, () -> controller.getInfo(10L));
            var bo = new ChatSessionBo(); bo.setId(10L);
            assertThrows(IllegalArgumentException.class, () -> controller.edit(bo));
            assertThrows(IllegalArgumentException.class, () -> controller.remove(new Long[]{10L}));
            verifyNoInteractions(service);
        }
    }
}
