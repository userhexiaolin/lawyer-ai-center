package org.ruoyi.service.legal;

import org.junit.jupiter.api.*;
import org.ruoyi.domain.entity.legal.LegalDocTemplate;
import org.ruoyi.mapper.legal.LegalDocTemplateMapper;
import org.ruoyi.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class TemplateTypeCompatibilityTest {
    private static final String LETTER="物业费催缴律师函（模板）\n致：{{收件人}}\n本所接受物业公司委托，请履行付款义务。\n{{律师事务所}}";
    private static final String LEDGER="物业服务费催收台账\n序号\t业主\t欠费期间\t应收\t实收\t欠费金额";
    private static final List<String> TYPES=List.of("律师函","台账","民事起诉状","申请书");
    private static final List<String> TEXTS=List.of(LETTER,LEDGER,"民事起诉状\n诉讼请求\n事实与理由","财产保全申请书\n申请事项\n事实与理由");
    @TestFactory Stream<DynamicTest> documentFormsMustMatchEvenForCustomTypes(){
        return java.util.stream.IntStream.range(0,TYPES.size()).boxed().flatMap(i->java.util.stream.IntStream.range(0,TYPES.size()).mapToObj(j->DynamicTest.dynamicTest(TYPES.get(i)+"正文→"+TYPES.get(j),()->{
            if(i==j)assertDoesNotThrow(()->TemplateTypeCompatibility.require(TYPES.get(j),"任意名称",TEXTS.get(i)));
            else assertEquals(422,assertThrows(DraftingException.class,()->TemplateTypeCompatibility.require(TYPES.get(j),"任意名称",TEXTS.get(i))).getCode());
        })));
    }
    private DocTemplateService service(LegalDocTemplateMapper mapper){
        var types=mock(DocTemplateTypeService.class);
        when(types.require(anyString(),anyBoolean(),anyBoolean())).thenAnswer(call->call.getArgument(0));
        return new DocTemplateService(mapper,types);
    }
    private LegalDocTemplate row(String status){
        var row=new LegalDocTemplate();row.setId(9L);row.setTenantId("tenant-a");row.setName("物业费催缴律师函");row.setDocumentType("律师函");row.setContent(LETTER);row.setStatus(status);return row;
    }
    private org.mockito.MockedStatic<LoginHelper> login(){
        var login=mockStatic(LoginHelper.class);login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");return login;
    }
    @BeforeEach void metadata(){com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"test"),LegalDocTemplate.class);}

    @Test void manualMetadataAndTypeOnlySavesAcceptAnyValidTypeForEnabledAndDisabledLetters(){
        for(String status:List.of("0","1")){
            var mapper=mock(LegalDocTemplateMapper.class);var row=row(status);when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenReturn(1);var service=service(mapper);
            try(var login=login()){
                service.updateMetadata(9L,new DocTemplateService.Metadata("重命名为台账","台账",0,status,""));
                var revision=service.typeSnapshot(9L).revision();
                service.updateType(9L,new DocTemplateService.TypeInput("台账",revision));
                verify(mapper,times(2)).update(argThat((LegalDocTemplate changed)->"台账".equals(changed.getDocumentType())&&changed.getContent()==null),any(LambdaQueryWrapper.class));
                assertEquals("律师函",row.getDocumentType());
            }
        }
    }
    @Test void createAndFullEditHonorManualTypeRegardlessOfBodyStatusOrDraftFlag(){
        for(String status:List.of("0","1"))for(boolean draft:List.of(true,false)){
            var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row("1"));
            when(mapper.insert(any(LegalDocTemplate.class))).thenAnswer(call->{LegalDocTemplate saved=call.getArgument(0);saved.setId(19L);return 1;});when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenReturn(1);
            var input=new DocTemplateService.Input("改成台账","台账",LETTER,0,status,"",null,null,false,null,false,draft);
            try(var login=login()){
                assertEquals("19",service.create(input));
                service.update(9L,input);
                verify(mapper).insert(argThat((LegalDocTemplate saved)->"台账".equals(saved.getDocumentType())&&LETTER.equals(saved.getContent())));verify(mapper).update(argThat((LegalDocTemplate saved)->"台账".equals(saved.getDocumentType())&&LETTER.equals(saved.getContent())),any(LambdaQueryWrapper.class));
            }
        }
    }
    @Test void sameFamilyCustomTypesAreAllowedAndSavedMiscalssificationsCanBeCorrected(){
        var mapper=mock(LegalDocTemplateMapper.class);var row=row("0");row.setDocumentType("台账");when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenReturn(1);var service=service(mapper);
        try(var login=login()){
            service.updateMetadata(9L,new DocTemplateService.Metadata("物业费催缴律师函","律师函",0,"0",""));
            assertDoesNotThrow(()->TemplateTypeCompatibility.require("物业催缴律师函",null,LETTER));
            verify(mapper).update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class));
        }
    }
    @Test void manuallyClassifiedTemplateRemainsSelectableAndDraftableWithAdvisoryOnly(){
        var mapper=mock(LegalDocTemplateMapper.class);var row=row("0");row.setName("物业服务费催款函");row.setDocumentType("催款函");row.setContent("物业服务费催款函\n请履行付款义务。");when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row));when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenAnswer(call->{LegalDocTemplate changes=call.getArgument(0);assertNull(changes.getContent());row.setDocumentType(changes.getDocumentType());return 1;});var service=service(mapper);
        var files=mock(TemplateFiles.class);org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
        byte[] docx=new DocumentWord().generateTemplate(row.getContent());var blocks=TemplateLayout.inspect(docx);
        when(files.current(9L)).thenReturn(new TemplateFiles.Version("confirmed",9L,"物业服务费催款函.docx","docx",docx,docx,blocks));
        when(files.currentSummaries(List.of(9L))).thenReturn(Map.of(9L,new TemplateFiles.SourceSummary("confirmed","物业服务费催款函.docx","docx","confirmed","",true)));
        try(var login=login()){
            service.updateMetadata(9L,new DocTemplateService.Metadata(row.getName(),"律师函",0,"0",""));assertEquals("律师函",row.getDocumentType());
            var option=service.enabled("律师函").get(0);assertTrue(option.compatible());assertTrue(option.preservesLayout());assertTrue(option.warning().contains("辅助识别为“催款函”"));assertTrue(option.warning().contains("不影响保存和起草"));assertFalse(option.warning().contains("未保存"));
            var prompt=service.prompt("律师函",9L,"本次起草事实");assertEquals("9",prompt.templateId());assertEquals(row.getName(),prompt.templateName());assertTrue(prompt.content().startsWith("本次起草事实"));
        }
    }
    @Test void uploadAndParsedSourceRespectManualTypeEvenWhenHeadingIsAnotherForm(){
        var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);var files=new TemplateFiles(jdbc,mock(DocumentConversion.class));
        var file=new org.springframework.mock.web.MockMultipartFile("file","物业服务催款函.txt","text/plain","物业服务费催款函\n请履行付款义务。".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try(var login=login()){
            var preview=service.upload(file,"律师函");assertEquals("律师函",preview.documentType());assertTrue(preview.content().startsWith("物业服务费催款函"));
            var source=files.stage(file,"律师函",true);assertEquals("律师函",source.documentType());assertEquals("rules",source.parseStatus());assertTrue(source.content().contains("物业服务费催款函"));verifyNoInteractions(mapper);
        }
    }
    @Test void bodyTitleWinsOverMisleadingNameAndBodyReferencesDoNotBecomeDocumentTitles(){
        assertDoesNotThrow(()->TemplateTypeCompatibility.require("台账","律师函",LEDGER));
        assertDoesNotThrow(()->TemplateTypeCompatibility.require("律师函",null,"律 师 函（模板）\n根据借款合同履行义务。"));
        assertDoesNotThrow(()->TemplateTypeCompatibility.require("民事起诉状",null,"民事起诉状\n证据包括律师函和催收台账。"));
        assertThrows(DraftingException.class,()->TemplateTypeCompatibility.require("台账","03_律师函模板.docx","请履行义务。"));
        assertDoesNotThrow(()->TemplateTypeCompatibility.require("自由描述文书",null,LETTER));
        assertThrows(DraftingException.class,()->TemplateTypeCompatibility.require("民事起诉状",null,"行政起诉状\n诉讼请求"));
        assertThrows(DraftingException.class,()->TemplateTypeCompatibility.require("台账","普通模板","致业主：\n本所接受物业公司的委托，指派承办律师处理。\n请收到本函后履行付款义务。"));
    }
    @Test void aiAnswerWithRealButIrrelevantEvidenceCannotReclassifyLetterAsLedger(){
        assertFalse(TemplateTypePolicy.valid(new TemplateTypeDecisionStore.Decision("台账","AI","本所接受物业公司委托"),List.of("律师函","台账"),LETTER));
    }
    @Test void savingRawUploadUsesManualTypeWithoutReclassifyingItsBody()throws Exception{
        byte[] docx;
        try(var document=new org.apache.poi.xwpf.usermodel.XWPFDocument();var out=new java.io.ByteArrayOutputStream()){
            document.createParagraph().createRun().setText("物业服务费催收台账");document.createParagraph().createRun().setText("序号 业主 欠费期间 欠费金额");document.write(out);docx=out.toByteArray();
        }
        var mapper=mock(LegalDocTemplateMapper.class);when(mapper.insert(any(LegalDocTemplate.class))).thenAnswer(call->{LegalDocTemplate row=call.getArgument(0);row.setId(19L);return 1;});var service=service(mapper);var files=mock(TemplateFiles.class);
        byte[] raw=TemplateLayout.storeState(docx,new TemplateLayout.State(true,false,"",""));
        when(files.load("raw")).thenReturn(new TemplateFiles.Version("raw",null,"误命名律师函.docx","docx",docx,raw,List.of()));org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
        try(var login=login()){
            assertEquals("19",service.create(new DocTemplateService.Input("误命名律师函","台账","",0,"0","","raw",null,false,null,false,true)));
            assertEquals("19",service.create(new DocTemplateService.Input("律师函","律师函","",0,"0","","raw",null,false,null,false,true)));
            verify(mapper,times(2)).insert(any(LegalDocTemplate.class));
        }
    }
    @Test void rawTypeSaveDoesNotReadOrModifyOriginalEvenWhenInferenceDisagrees()throws Exception{
        var root=java.nio.file.Path.of(System.getProperty("template.fixtureRoot","."));
        var original=root.resolve("artifacts/20261022/物业服务合同20261022/03_物业费催缴律师函模板.docx");
        var ledger=root.resolve("artifacts/20261022/物业服务合同20261022/01_物业服务费催收台账.docx");
        Assumptions.assumeTrue(java.nio.file.Files.exists(original)&&java.nio.file.Files.exists(ledger),"Original local fixtures required");
        byte[] before=java.nio.file.Files.readAllBytes(original),ledgerBytes=java.nio.file.Files.readAllBytes(ledger);
        String source=DocTemplateFile.extract(original.getFileName().toString(),before).content();
        String ledgerSource=DocTemplateFile.extract(ledger.getFileName().toString(),ledgerBytes).content();
        assertThrows(DraftingException.class,()->TemplateTypeCompatibility.require("台账",null,source));
        assertThrows(DraftingException.class,()->TemplateTypeCompatibility.require("律师函",null,ledgerSource));
        var mapper=mock(LegalDocTemplateMapper.class);var row=row("1");row.setContent("");row.setDocumentType("自由描述文书");when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenReturn(1);var service=service(mapper);var files=mock(TemplateFiles.class);
        when(files.current(9L)).thenReturn(new TemplateFiles.Version("raw",9L,original.getFileName().toString(),"docx",before,before,List.of()));org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
        try(var login=login()){
            assertDoesNotThrow(()->service.updateMetadata(9L,new DocTemplateService.Metadata("台账","台账",0,"1","")));
            assertDoesNotThrow(()->service.updateMetadata(9L,new DocTemplateService.Metadata("物业律师函","律师函",0,"1","")));
            verify(mapper,times(2)).update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class));verifyNoInteractions(files);
        }
        assertArrayEquals(before,java.nio.file.Files.readAllBytes(original));assertArrayEquals(ledgerBytes,java.nio.file.Files.readAllBytes(ledger));
    }
    @Test void collectionLetterCanBeSavedAsLawyerLetterWithoutChangingNameBodyOrSource(){
        var mapper=mock(LegalDocTemplateMapper.class);var row=row("0");row.setName("物业服务催款函");row.setDocumentType("催款函");row.setContent("物业服务费催款函\n请履行付款义务。");
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenReturn(1);var service=service(mapper);var files=mock(TemplateFiles.class);org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
        try(var login=login()){
            service.updateMetadata(9L,new DocTemplateService.Metadata("物业服务催款函","律师函",0,"0",""));
            var revision=service.typeSnapshot(9L).revision();service.updateType(9L,new DocTemplateService.TypeInput("律师函",revision));
            verify(mapper,times(2)).update(argThat((LegalDocTemplate changed)->"律师函".equals(changed.getDocumentType())&&changed.getContent()==null&&changed.getSourceVersion()==null),any(LambdaQueryWrapper.class));
            assertEquals("催款函",row.getDocumentType());assertEquals("物业服务费催款函\n请履行付款义务。",row.getContent());verify(files,never()).current(anyLong());
        }
    }
    @Test void typeOnlySaveAcceptsUnparsedBodyButStillRejectsStaleRevision(){
        var mapper=mock(LegalDocTemplateMapper.class);var row=row("1");row.setContent("");when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenReturn(1);var service=service(mapper);
        try(var login=login()){
            var revision=service.typeSnapshot(9L).revision();service.updateType(9L,new DocTemplateService.TypeInput("台账",revision));
            assertEquals(409,assertThrows(DraftingException.class,()->service.updateType(9L,new DocTemplateService.TypeInput("台账","stale"))).getCode());
            verify(mapper).update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class));
        }
    }
}
