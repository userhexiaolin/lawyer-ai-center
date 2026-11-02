package org.ruoyi.controller.chat;

import java.util.List;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.ruoyi.common.chat.domain.bo.chat.ChatMessageBo;
import org.ruoyi.common.chat.domain.vo.chat.ChatMessageVo;
import org.ruoyi.service.chat.IChatMessageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.ruoyi.common.idempotent.annotation.RepeatSubmit;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.excel.utils.ExcelUtil;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.core.exception.ServiceException;

/**
 * 聊天消息
 *
 * @author ageerle
 * @date 2025-12-14
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/message")
public class ChatMessageController extends BaseController {

    private final IChatMessageService chatMessageService;
    private final org.ruoyi.service.chat.ChatSessionOwnershipGuard ownershipGuard;

    private ChatMessageVo ownedMessage(Long id) {
        ChatMessageVo value = chatMessageService.queryById(id);
        if (value == null || !LoginHelper.getUserId().equals(value.getUserId())) throw new ServiceException("消息不可用");
        return value;
    }

    /**
     * 查询聊天消息列表
     */
    @SaCheckPermission("system:message:list")
    @GetMapping("/list")
    public TableDataInfo<ChatMessageVo> list(ChatMessageBo bo, PageQuery pageQuery) {
        bo.setUserId(LoginHelper.getUserId());
        if (bo.getSessionId() != null) ownershipGuard.requireOwned(LoginHelper.getUserId(), bo.getSessionId());
        return chatMessageService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出聊天消息列表
     */
    @SaCheckPermission("system:message:export")
    @Log(title = "聊天消息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ChatMessageBo bo, HttpServletResponse response) {
        bo.setUserId(LoginHelper.getUserId());
        if (bo.getSessionId() != null) ownershipGuard.requireOwned(LoginHelper.getUserId(), bo.getSessionId());
        List<ChatMessageVo> list = chatMessageService.queryList(bo);
        ExcelUtil.exportExcel(list, "聊天消息", ChatMessageVo.class, response);
    }

    /**
     * 获取聊天消息详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("system:message:query")
    @GetMapping("/{id}")
    public R<ChatMessageVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(ownedMessage(id));
    }

    /**
     * 新增聊天消息
     */
    @SaCheckPermission("system:message:add")
    @Log(title = "聊天消息", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ChatMessageBo bo) {
        ownershipGuard.requireOwned(LoginHelper.getUserId(), bo.getSessionId());
        bo.setUserId(LoginHelper.getUserId());
        return toAjax(chatMessageService.insertByBo(bo));
    }

    /**
     * 修改聊天消息
     */
    @SaCheckPermission("system:message:edit")
    @Log(title = "聊天消息", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ChatMessageBo bo) {
        ChatMessageVo previous = ownedMessage(bo.getId());
        bo.setUserId(LoginHelper.getUserId());
        bo.setSessionId(previous.getSessionId());
        return toAjax(chatMessageService.updateByBo(bo));
    }

    /**
     * 删除聊天消息
     *
     * @param ids 主键串
     */
    @SaCheckPermission("system:message:remove")
    @Log(title = "聊天消息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        for (Long id : ids) ownedMessage(id);
        return toAjax(chatMessageService.deleteWithValidByIds(List.of(ids), true));
    }
}
