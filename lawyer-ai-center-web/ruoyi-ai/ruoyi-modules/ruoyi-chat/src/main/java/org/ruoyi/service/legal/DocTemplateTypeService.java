package org.ruoyi.service.legal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.legal.*;
import org.ruoyi.mapper.legal.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class DocTemplateTypeService {
    private final LegalDocTemplateTypeMapper mapper;
    private final LegalDocTemplateMapper templates;
    public record Input(String name, Integer sortOrder, String status, String remark) {}
    public record Option(String value, String label, boolean disabled) {}

    private String tenant() {
        String tenant = LoginHelper.getTenantId();
        if (LoginHelper.getUserId() == null || tenant == null || tenant.isBlank()) throw new DraftingException("请先登录", 401);
        return tenant;
    }
    private LambdaQueryWrapper<LegalDocTemplateType> scope(String tenant) {
        return new LambdaQueryWrapper<LegalDocTemplateType>().eq(LegalDocTemplateType::getTenantId, tenant);
    }
    private void defaults(String tenant) {
        var seeds = DocumentType.options();
        if (mapper.selectCount(scope(tenant).eq(LegalDocTemplateType::getBuiltin, true)) >= seeds.size()) return;
        List<LegalDocTemplateType> rows = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            var row = new LegalDocTemplateType();
            row.setId(IdWorker.getId()); row.setTenantId(tenant); row.setName(seeds.get(i).value()); row.setSortOrder((i + 1) * 10);
            rows.add(row);
        }
        mapper.insertDefaults(rows);
    }
    public List<LegalDocTemplateType> list(String name) {
        String tenant = tenant(); defaults(tenant);
        var query = scope(tenant);
        if (name != null && !name.isBlank()) query.like(LegalDocTemplateType::getName, name.trim());
        return mapper.selectList(query.orderByAsc(LegalDocTemplateType::getSortOrder, LegalDocTemplateType::getId));
    }
    public List<Option> options(boolean includeDisabled) {
        return list(null).stream().filter(row -> includeDisabled || "0".equals(row.getStatus()))
            .map(row -> new Option(row.getName(), row.getName(), !"0".equals(row.getStatus()))).toList();
    }
    public String require(String name, boolean enabled, boolean lock) {
        if (name == null || name.isBlank() || name.length() > 50) throw new DraftingException("请选择有效的模板类型", 400);
        String tenant = tenant(); defaults(tenant);
        var query = scope(tenant).eq(LegalDocTemplateType::getName, name);
        if (lock) query.last("FOR UPDATE");
        var row = mapper.selectOne(query);
        if (row == null) throw new DraftingException("模板类型不存在，请刷新后重试", 400);
        if (enabled && !"0".equals(row.getStatus())) throw new DraftingException("该模板类型已停用，请选择启用的类型", 400);
        return row.getName();
    }
    private LegalDocTemplateType get(Long id) {
        if (id == null) throw new DraftingException("模板类型不存在或不可访问", 404);
        var row = mapper.selectOne(scope(tenant()).eq(LegalDocTemplateType::getId, id).last("FOR UPDATE"));
        if (row == null) throw new DraftingException("模板类型不存在或不可访问", 404);
        return row;
    }
    private void copy(LegalDocTemplateType row, Input input) {
        if (input == null || input.name() == null || input.name().isBlank() || input.name().trim().length() > 50) throw new DraftingException("类型名称不能为空且不超过50字", 400);
        String name = input.name().trim();
        if (Boolean.TRUE.equals(row.getBuiltin()) && !name.equals(row.getName())) throw new DraftingException("内置类型名称用于专用起草流程，不可修改", 400);
        if (input.sortOrder() == null || input.sortOrder() < 0 || input.sortOrder() > 999999) throw new DraftingException("排序号须为0至999999的整数", 400);
        if (!Set.of("0", "1").contains(Objects.toString(input.status(), ""))) throw new DraftingException("请选择启用或停用状态", 400);
        if (input.remark() != null && input.remark().length() > 500) throw new DraftingException("备注不超过500字", 400);
        if (mapper.selectCount(scope(row.getTenantId()).eq(LegalDocTemplateType::getName, name).ne(row.getId() != null, LegalDocTemplateType::getId, row.getId())) > 0) throw new DraftingException("该模板类型名称已存在", 400);
        row.setName(name); row.setSortOrder(input.sortOrder()); row.setStatus(input.status()); row.setRemark(input.remark());
    }
    @Transactional(rollbackFor = Exception.class)
    public String create(Input input) {
        String tenant = tenant(); defaults(tenant);
        var row = new LegalDocTemplateType(); row.setTenantId(tenant); row.setBuiltin(false); copy(row, input);
        try { mapper.insert(row); } catch (DuplicateKeyException e) { throw new DraftingException("该模板类型名称已存在", 400); }
        return row.getId().toString();
    }
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Input input) {
        var row = get(id); String previous = row.getName(); copy(row, input);
        try {
            if (mapper.update(row, scope(row.getTenantId()).eq(LegalDocTemplateType::getId, id)) != 1) throw new DraftingException("模板类型已变化，请刷新后重试", 409);
        } catch (DuplicateKeyException e) { throw new DraftingException("该模板类型名称已存在", 400); }
        if (!previous.equals(row.getName())) {
            var replacement = new LegalDocTemplate(); replacement.setDocumentType(row.getName());
            templates.update(replacement, new LambdaQueryWrapper<LegalDocTemplate>().eq(LegalDocTemplate::getTenantId, row.getTenantId()).eq(LegalDocTemplate::getDocumentType, previous));
        }
    }
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        var row = get(id);
        if (Boolean.TRUE.equals(row.getBuiltin())) throw new DraftingException("内置类型不可删除，可通过停用控制使用", 400);
        if (templates.selectCount(new LambdaQueryWrapper<LegalDocTemplate>().eq(LegalDocTemplate::getTenantId, row.getTenantId()).eq(LegalDocTemplate::getDocumentType, row.getName())) > 0) throw new DraftingException("该类型下仍有模板，请先删除模板或调整模板类型", 400);
        if (mapper.delete(scope(row.getTenantId()).eq(LegalDocTemplateType::getId, id)) != 1) throw new DraftingException("模板类型已变化，请刷新后重试", 409);
    }
}
