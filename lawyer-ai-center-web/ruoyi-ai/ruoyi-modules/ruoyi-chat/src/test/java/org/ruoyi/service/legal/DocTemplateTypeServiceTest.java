package org.ruoyi.service.legal;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.legal.*;
import org.ruoyi.mapper.legal.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class DocTemplateTypeServiceTest {
    private LegalDocTemplateTypeMapper mapper;
    private LegalDocTemplateMapper templates;
    private DocTemplateTypeService service;
    private MockedStatic<LoginHelper> login;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), LegalDocTemplateType.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), LegalDocTemplate.class);
        mapper = mock(LegalDocTemplateTypeMapper.class); templates = mock(LegalDocTemplateMapper.class);
        service = new DocTemplateTypeService(mapper, templates);
        login = mockStatic(LoginHelper.class); login.when(LoginHelper::getUserId).thenReturn(7L); login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<LegalDocTemplateType> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("tenant_id"));
            assertTrue(query.getParamNameValuePairs().containsValue("tenant-a"));
            return query.getSqlSegment().contains("builtin") ? 6L : 0L;
        });
    }
    @AfterEach void cleanup() { login.close(); }
    private LegalDocTemplateType row(String name, String status, boolean builtin) {
        var row = new LegalDocTemplateType(); row.setId(12L); row.setTenantId("tenant-a"); row.setName(name); row.setStatus(status); row.setSortOrder(10); row.setBuiltin(builtin); return row;
    }
    private DocTemplateTypeService.Input input(String name) { return new DocTemplateTypeService.Input(name, 20, "0", "备注"); }

    @Test void optionsUseStoredCustomTypesAndHideDisabledOnlyForUsers() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row("申请书", "0", false), row("律师函", "1", true)));
        assertEquals(List.of(new DocTemplateTypeService.Option("申请书", "申请书", false)), service.options(false));
        assertEquals(2, service.options(true).size()); assertTrue(service.options(true).get(1).disabled());
    }
    @Test void customTypeIsValidatedAgainstCurrentTenantAndLockedForWrites() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<LegalDocTemplateType> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("FOR UPDATE")); assertTrue(query.getParamNameValuePairs().containsValue("tenant-a")); assertTrue(query.getParamNameValuePairs().containsValue("申请书"));
            return row("申请书", "0", false);
        });
        assertEquals("申请书", service.require("申请书", true, true));
    }
    @Test void unknownAndDisabledTypesCannotBeUsedForDrafting() {
        assertThrows(DraftingException.class, () -> service.require("未知类型", true, false));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row("申请书", "1", false));
        assertThrows(DraftingException.class, () -> service.require("申请书", true, false));
        assertEquals("申请书", service.require("申请书", false, false));
    }
    @Test void newTenantGetsAllSixBuiltinsWithoutOverwritingLaterEdits() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(mapper.insertDefaults(anyList())).thenAnswer(call -> {
            List<LegalDocTemplateType> rows = call.getArgument(0); assertEquals(6, rows.size());
            assertEquals(DocumentType.options().stream().map(DocumentType.Option::value).toList(), rows.stream().map(LegalDocTemplateType::getName).toList());
            assertTrue(rows.stream().allMatch(value -> "tenant-a".equals(value.getTenantId()) && value.getId() != null)); return 6;
        });
        service.list(null); verify(mapper).insertDefaults(anyList());
    }
    @Test void creationStoresTrimmedNameAndCurrentTenant() {
        when(mapper.insert(any(LegalDocTemplateType.class))).thenAnswer(call -> {
            LegalDocTemplateType row = call.getArgument(0); assertEquals("申请书", row.getName()); assertEquals("tenant-a", row.getTenantId()); assertFalse(row.getBuiltin()); row.setId(99L); return 1;
        });
        assertEquals("99", service.create(input(" 申请书 ")));
    }
    @Test void duplicateNamesAreRejected() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(6L);
        assertThrows(DraftingException.class, () -> service.create(input("民事起诉状")));
        verify(mapper, never()).insert(any(LegalDocTemplateType.class));
    }
    @Test void builtinsKeepTheirNamesAndCannotBeDeleted() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row("民事起诉状", "0", true));
        assertThrows(DraftingException.class, () -> service.update(12L, input("其他名称")));
        assertThrows(DraftingException.class, () -> service.remove(12L));
        verifyNoInteractions(templates); verify(mapper, never()).delete(any(LambdaQueryWrapper.class));
    }
    @Test void renamingCustomTypeMovesOnlyTemplatesInItsTenant() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row("申请书", "0", false));
        when(mapper.update(any(LegalDocTemplateType.class), any(LambdaQueryWrapper.class))).thenReturn(1);
        when(templates.update(any(LegalDocTemplate.class), any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            LegalDocTemplate value = call.getArgument(0); LambdaQueryWrapper<LegalDocTemplate> query = call.getArgument(1);
            assertEquals("执行申请书", value.getDocumentType()); query.getSqlSegment();
            assertTrue(query.getParamNameValuePairs().containsValue("tenant-a")); assertTrue(query.getParamNameValuePairs().containsValue("申请书")); return 2;
        });
        service.update(12L, input("执行申请书")); verify(templates).update(any(LegalDocTemplate.class), any(LambdaQueryWrapper.class));
    }
    @Test void customTypeRenameUsesManualNameWithoutInspectingTemplateBodies(){
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row("物业催缴律师函","0",false));
        when(mapper.update(any(LegalDocTemplateType.class),any(LambdaQueryWrapper.class))).thenReturn(1);
        service.update(12L,input("台账"));
        verify(mapper).update(argThat((LegalDocTemplateType changed)->"台账".equals(changed.getName())),any(LambdaQueryWrapper.class));
        verify(templates).update(argThat((LegalDocTemplate changed)->"台账".equals(changed.getDocumentType())&&changed.getContent()==null),argThat((LambdaQueryWrapper<LegalDocTemplate> query)->{query.getSqlSegment();return query.getParamNameValuePairs().containsValue("tenant-a")&&query.getParamNameValuePairs().containsValue("物业催缴律师函");}));
        verify(templates,never()).selectList(any(LambdaQueryWrapper.class));
    }
    @Test void referencedTypesCannotBeDeletedEvenIfTheirTemplatesAreDisabled() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row("申请书", "1", false));
        when(templates.selectCount(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<LegalDocTemplate> query = call.getArgument(0); String sql = query.getSqlSegment();
            assertTrue(sql.contains("tenant_id")); assertFalse(sql.contains("status")); assertTrue(query.getParamNameValuePairs().containsValue("申请书")); return 1L;
        });
        assertThrows(DraftingException.class, () -> service.remove(12L)); verify(mapper, never()).delete(any(LambdaQueryWrapper.class));
    }
    @Test void unreferencedCustomTypeCanBeDeleted() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row("申请书", "0", false));
        when(templates.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(mapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        service.remove(12L); verify(mapper).delete(any(LambdaQueryWrapper.class));
    }
    @Test void anotherTenantsTypeCannotBeChangedOrDeleted() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<LegalDocTemplateType> query = call.getArgument(0); assertTrue(query.getSqlSegment().contains("tenant_id")); assertTrue(query.getParamNameValuePairs().containsValue("tenant-a")); return null;
        });
        assertEquals(404, assertThrows(DraftingException.class, () -> service.update(12L, input("申请书"))).getCode());
        assertEquals(404, assertThrows(DraftingException.class, () -> service.remove(12L)).getCode()); verifyNoInteractions(templates);
    }
}
