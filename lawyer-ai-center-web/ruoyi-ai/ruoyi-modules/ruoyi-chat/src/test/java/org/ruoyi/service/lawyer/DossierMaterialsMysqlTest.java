package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.lawyer.*;
import org.ruoyi.service.legal.DocumentConversion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real MySQL, dedicated synthetic-only database, production service/controller/worker.
 * The disk-backed OssService fixture tests the storage contract, not the live S3 deployment.
 */
@Tag("dev") @EnabledIfSystemProperty(named="dossier.mysql",matches="true")
class DossierMaterialsMysqlTest {
    static Path workspace, evidence, objectRoot;static String url,user,password,database;
    JdbcTemplate jdbc;TransactionTemplate tx;CaseAccess access;OssService oss;
    DossierMaterialService materials;DossierParseWorker worker;MockedStatic<LoginHelper> login;
    long actor=101;String tenant="000000";
    static String setting(String config,String field){var m=Pattern.compile("(?m)^          "+field+": (.+)$").matcher(config);if(!m.find())throw new IllegalStateException("Datasource setting missing");return m.group(1).strip();}
    @BeforeAll static void schema()throws Exception{
        workspace=Path.of(System.getProperty("dossier.workspace")).toAbsolutePath();
        evidence=workspace.resolve(System.getProperty("dossier.evidence","docs/product/o001/evidence/task-85"));Files.createDirectories(evidence);
        objectRoot=Files.createTempDirectory("dossier85-synthetic-objects-");
        String config=Files.readString(workspace.resolve("ruoyi-ai/ruoyi-admin/src/main/resources/application-dev.yml"));
        url=setting(config,"url");user=setting(config,"username");password=setting(config,"password");
        if(!url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost)(?::[0-9]+)?/.*"))throw new IllegalStateException("Only a local MySQL test instance is allowed");
        database="codex_dossier85_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4");}
        int query=url.indexOf('?');if(query<0)query=url.length();int slash=url.lastIndexOf('/',query);
        url=url.substring(0,slash+1)+database+url.substring(query);
        var db=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        db.execute("CREATE TABLE lawyer_case(id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),user_id BIGINT,deleted INT DEFAULT 0,title VARCHAR(200))");
        db.execute("CREATE TABLE lawyer_case_file(tenant_id VARCHAR(20),user_id BIGINT,case_id BIGINT,oss_id BIGINT,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id,user_id,case_id,oss_id))");
        db.execute("CREATE TABLE sys_oss(oss_id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),create_by BIGINT,original_name VARCHAR(255),file_name VARCHAR(255),file_suffix VARCHAR(20),create_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
        String migration=Files.readString(workspace.resolve("ruoyi-ai/docs/script/sql/update/2026-10-21-dossier-materials.sql")).replaceAll("(?m)--.*$","");
        for(String sql:migration.split(";"))if(!sql.isBlank())db.execute(sql);
        Files.writeString(evidence.resolve("mysql-rehearsal.json"),"{\"database\":\""+database+"\",\"syntheticOnly\":true,\"liveBusinessDatabaseModified\":false,\"objectFixture\":\"local disk via OssService test adapter\"}\n");
    }
    DossierMaterialService service(DossierFileReader reader){var result=new DossierMaterialService(jdbc,tx,access,oss,reader);result.enabled(true);return result;}
    @BeforeEach void setup()throws Exception{
        var source=new DriverManagerDataSource(url,user,password);jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        for(String table:List.of("lawyer_case_parse_log","lawyer_case_parse_job","lawyer_case_file_page","lawyer_case_upload","lawyer_case_file_version","lawyer_case_file","sys_oss","lawyer_case"))jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO lawyer_case VALUES(11,'000000',101,0,'SYNTHETIC CASE'),(22,'000000',202,0,'OTHER CASE'),(33,'111111',101,0,'OTHER TENANT')");
        login=mockStatic(LoginHelper.class);login.when(LoginHelper::getTenantId).thenAnswer(c->tenant);login.when(LoginHelper::getUserId).thenAnswer(c->actor);
        access=mock(CaseAccess.class);
        org.mockito.stubbing.Answer<LawyerCase> owned=c->{Long id=c.getArgument(0);var rows=jdbc.queryForList("SELECT * FROM lawyer_case WHERE id=? AND tenant_id=? AND user_id=? AND deleted=0"+(c.getArguments().length>1&&Boolean.TRUE.equals(c.getArgument(1))?" FOR UPDATE":""),id,tenant,actor);if(rows.isEmpty())throw new ServiceException("案件不可用",404);var value=new LawyerCase();value.setId(id);value.setTenantId(tenant);value.setUserId(actor);value.setTitle("SYNTHETIC CASE");return value;};
        when(access.owned(anyLong())).thenAnswer(owned);when(access.owned(anyLong(),anyBoolean())).thenAnswer(owned);
        oss=mock(OssService.class);
        when(oss.uploadFile(any(org.springframework.web.multipart.MultipartFile.class))).thenAnswer(c->{var file=(org.springframework.web.multipart.MultipartFile)c.getArgument(0);long id=DossierMaterialService.id();Files.write(objectRoot.resolve(id+".bin"),file.getBytes());jdbc.update("INSERT INTO sys_oss(oss_id,tenant_id,create_by,original_name,file_name,file_suffix) VALUES(?,?,?,?,?,?)",id,tenant,actor,file.getOriginalFilename(),id+".bin",".pdf");var result=new OssDTO();result.setOssId(id);return result;});
        when(oss.readFileBytes(anyLong(),anyInt())).thenAnswer(c->Files.readAllBytes(objectRoot.resolve(c.getArgument(0)+".bin")));
        var reader=new DossierFileReader(new LocalDossierOcr(),mock(DocumentConversion.class));materials=service(reader);worker=new DossierParseWorker(jdbc,tx,materials,reader);
    }
    @AfterEach void close(){if(worker!=null)worker.close();if(login!=null)login.close();}
    CaseFileVersion upload(String name,byte[] bytes,Long material,boolean keep)throws Exception{
        var ticket=materials.init(11L,new DossierMaterialService.Init(name,bytes.length,DossierFiles.hash(bytes),UUID.randomUUID().toString(),material));
        materials.upload(11L,ticket.upload().getId(),new MockMultipartFile("file",name,"application/pdf",bytes));return materials.complete(11L,ticket.upload().getId(),keep);
    }
    void execute(CaseFileVersion version){var j=materials.job(11L,version.getLastJobId());String lease=UUID.randomUUID().toString();assertTrue(worker.claim(j.getId(),lease));worker.run(j.getId(),lease);}
    @Test void uploadCompletionIsIdempotentAndThreePagePdfPersistsAcrossServiceRestart()throws Exception{
        byte[] bytes=DossierFileReaderTest.pdf(3,false);String key=UUID.randomUUID().toString();var input=new DossierMaterialService.Init("normal.pdf",bytes.length,DossierFiles.hash(bytes),key,null);
        var ticket=materials.init(11L,input);assertEquals(ticket.upload().getId(),materials.init(11L,input).upload().getId());
        materials.upload(11L,ticket.upload().getId(),new MockMultipartFile("file","normal.pdf","application/pdf",bytes));
        var version=materials.complete(11L,ticket.upload().getId(),false);assertEquals(version.getId(),materials.complete(11L,ticket.upload().getId(),false).getId());
        assertEquals("queued",version.getParseStatus());assertNull(version.getPageCount());execute(version);
        var reader=new DossierFileReader(new LocalDossierOcr(),mock(DocumentConversion.class));var restarted=service(reader);
        var saved=restarted.version(11L,version.getId());assertEquals("success",saved.getParseStatus());assertEquals(3,saved.getPageCount());assertEquals(3,saved.getParsedPages());assertEquals(3,restarted.pages(11L,version.getId()).size());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_file_version",Integer.class));assertArrayEquals(bytes,restarted.checkedBytes(saved));
        assertEquals(137,restarted.preview(11L,saved.getId(),2)[0]&255);
    }
    @Test void completedNewUploadResumesAfterLostResponseAndKeepsItsOriginalTarget()throws Exception{
        byte[] bytes=DossierFileReaderTest.pdf(1,false);String key=UUID.randomUUID().toString();
        var input=new DossierMaterialService.Init("recover.pdf",bytes.length,DossierFiles.hash(bytes),key,null);
        var ticket=materials.init(11L,input).upload();
        materials.upload(11L,ticket.getId(),new MockMultipartFile("file","recover.pdf","application/pdf",bytes));
        var first=materials.complete(11L,ticket.getId(),false);
        // The completion response was lost: a refreshed browser repeats initialization with the original payload.
        var resumed=materials.init(11L,input).upload();
        assertEquals(ticket.getId(),resumed.getId());assertEquals("completed",resumed.getState());
        assertEquals(first.getId(),materials.complete(11L,resumed.getId(),false).getId());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_file_version",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_parse_job",Integer.class));
        var changedTarget=new DossierMaterialService.Init(input.fileName(),input.fileBytes(),input.sha256(),key,first.getMaterialId());
        assertEquals(409,assertThrows(ServiceException.class,()->materials.init(11L,changedTarget)).getCode());
        // The original request still resolves to V1 after a later replacement becomes current.
        upload("replacement.pdf",DossierFileReaderTest.pdf(2,false),first.getMaterialId(),false);
        assertEquals(first.getId(),materials.init(11L,input).upload().getFileVersionId());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_file_version",Integer.class));
    }
    @Test void completedReplacementRetainsItsRequestedMaterialAndRejectsNewMaterialReplay()throws Exception{
        var first=upload("first.pdf",DossierFileReaderTest.pdf(1,false),null,false);
        byte[] bytes=DossierFileReaderTest.pdf(2,false);String key=UUID.randomUUID().toString();
        var input=new DossierMaterialService.Init("replacement.pdf",bytes.length,DossierFiles.hash(bytes),key,first.getMaterialId());
        var ticket=materials.init(11L,input).upload();
        materials.upload(11L,ticket.getId(),new MockMultipartFile("file",input.fileName(),"application/pdf",bytes));
        var second=materials.complete(11L,ticket.getId(),false);
        assertEquals(2,second.getVersionNo());assertEquals(second.getId(),materials.init(11L,input).upload().getFileVersionId());
        var changedTarget=new DossierMaterialService.Init(input.fileName(),input.fileBytes(),input.sha256(),key,null);
        assertEquals(409,assertThrows(ServiceException.class,()->materials.init(11L,changedTarget)).getCode());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_file_version",Integer.class));
    }
    @Test void firstVersionOfLegacyMaterialIsStillAReplacementRequest()throws Exception{
        var original=oss.uploadFile(new MockMultipartFile("file","legacy.pdf","application/pdf",DossierFileReaderTest.pdf(1,false)));
        long materialId=DossierMaterialService.id();
        jdbc.update("INSERT INTO lawyer_case_file(tenant_id,user_id,case_id,oss_id,id,display_name) VALUES('000000',101,11,?,?,?)",original.getOssId(),materialId,"legacy.pdf");
        byte[] bytes=DossierFileReaderTest.pdf(2,false);String key=UUID.randomUUID().toString();
        var input=new DossierMaterialService.Init("replacement.pdf",bytes.length,DossierFiles.hash(bytes),key,materialId);
        var ticket=materials.init(11L,input).upload();
        materials.upload(11L,ticket.getId(),new MockMultipartFile("file",input.fileName(),"application/pdf",bytes));
        var first=materials.complete(11L,ticket.getId(),false);
        assertEquals(1,first.getVersionNo());assertNotEquals(original.getOssId(),first.getOssId());
        assertEquals(first.getId(),materials.init(11L,input).upload().getFileVersionId());
        var changedTarget=new DossierMaterialService.Init(input.fileName(),input.fileBytes(),input.sha256(),key,null);
        assertEquals(409,assertThrows(ServiceException.class,()->materials.init(11L,changedTarget)).getCode());
    }
    @Test void sameNameDifferentBytesAndDuplicateContentAreNeverSilentlyDiscarded()throws Exception{
        byte[] bytes=DossierFileReaderTest.pdf(1,false);var first=upload("same.pdf",bytes,null,false);
        var second=upload("same.pdf",DossierFileReaderTest.pdf(2,false),null,false);assertNotEquals(first.getMaterialId(),second.getMaterialId());
        var t=materials.init(11L,new DossierMaterialService.Init("copy.pdf",bytes.length,DossierFiles.hash(bytes),UUID.randomUUID().toString(),null));assertFalse(t.duplicateNames().isEmpty());
        materials.upload(11L,t.upload().getId(),new MockMultipartFile("file","copy.pdf","application/pdf",bytes));
        assertEquals(409,assertThrows(ServiceException.class,()->materials.complete(11L,t.upload().getId(),false)).getCode());
        var retained=materials.complete(11L,t.upload().getId(),true);assertNotEquals(first.getMaterialId(),retained.getMaterialId());assertEquals(3,materials.overview(11L).files());
    }
    @Test void leaseRecoveryReusesVersionAndPagesAndOldWorkerCannotOverwrite()throws Exception{
        var version=upload("recover.pdf",DossierFileReaderTest.pdf(3,false),null,false);long jobId=version.getLastJobId();
        assertTrue(worker.claim(jobId,"old-worker"));jdbc.update("UPDATE lawyer_case_parse_job SET lease_until=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE id=?",jobId);
        worker.close();var reader=new DossierFileReader(new LocalDossierOcr(),mock(DocumentConversion.class));worker=new DossierParseWorker(jdbc,tx,materials,reader);
        assertTrue(worker.claim(jobId,"recovered-worker"));worker.run(jobId,"old-worker");assertEquals(0,materials.pages(11L,version.getId()).size());
        worker.run(jobId,"recovered-worker");assertEquals("success",materials.version(11L,version.getId()).getParseStatus());assertEquals(2,materials.job(11L,jobId).getAttempts());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_file_page",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_file_version",Integer.class));
    }
    @Test void replacingFilePreservesOldOriginalAndVersionHistory()throws Exception{
        byte[] old=DossierFileReaderTest.pdf(1,false);var a=upload("version.pdf",old,null,false);execute(a);
        var b=upload("version.pdf",DossierFileReaderTest.pdf(2,false),a.getMaterialId(),false);
        assertEquals(2,b.getVersionNo());assertEquals(a.getMaterialId(),b.getMaterialId());assertEquals(2,materials.versions(11L,a.getMaterialId()).size());assertArrayEquals(old,materials.checkedBytes(materials.version(11L,a.getId())));assertEquals(1,materials.overview(11L).files());
    }
    @Test void pageOnlyRetryDoesNotReparseSuccessesOrCreateAnotherVersion()throws Exception{
        var calls=new AtomicInteger();DossierOcr ocr=image->{calls.incrementAndGet();throw new DossierReadException("OCR_TIMEOUT","合成故障注入：第3页超时");};
        var reader=new DossierFileReader(ocr,mock(DocumentConversion.class));materials=service(reader);worker.close();worker=new DossierParseWorker(jdbc,tx,materials,reader);
        var v=upload("ten-pages.pdf",DossierFileReaderTest.pdf(10,true),null,false);execute(v);
        var partial=materials.version(11L,v.getId());assertEquals("partial",partial.getParseStatus());assertEquals(9,partial.getParsedPages());assertEquals(1,partial.getFailedPages());
        var ids=materials.pages(11L,v.getId()).stream().map(CaseFilePage::getId).toList();assertEquals(1,calls.get());
        String key=UUID.randomUUID().toString();var retry=materials.retry(11L,v.getId(),key,List.of(3));assertEquals(retry.getId(),materials.retry(11L,v.getId(),key,List.of(3)).getId());
        String lease="only-third-page";assertTrue(worker.claim(retry.getId(),lease));worker.run(retry.getId(),lease);
        assertEquals(2,calls.get());assertEquals(ids,materials.pages(11L,v.getId()).stream().map(CaseFilePage::getId).toList());assertEquals(1,materials.job(11L,retry.getId()).getProgressTotal());
        var sources=materials.readable(11L,v.getOssId());assertEquals(9,sources.readablePages());assertEquals("partial",sources.parseStatus());assertEquals("partial",materials.version(11L,v.getId()).getParseStatus());
        var caseService=mock(LawyerCaseService.class);var rawReader=mock(org.ruoyi.controller.chat.ContractReviewController.class);
        var c=new LawyerCase();c.setTitle("SYNTHETIC CASE");var file=new LawyerCaseService.Material(v.getOssId(),"ten-pages.pdf","ten-pages.pdf",".pdf",null,null,null,"protected-download");
        when(caseService.detail(11L)).thenReturn(new LawyerCaseService.CaseDetail(c,List.of(),List.of(file)));when(caseService.file(11L,v.getOssId())).thenReturn(file);
        var downstream=new DossierService(caseService,rawReader,oss);org.springframework.test.util.ReflectionTestUtils.setField(downstream,"pageMaterials",materials);
        var document=downstream.parse(11L,List.of(v.getOssId())).documents().get(0);assertEquals("partial",document.status());assertTrue(document.text().contains("9/10"));assertFalse(document.text().contains("第3物理页"));verifyNoInteractions(rawReader);
        assertEquals(403,assertThrows(ServiceException.class,()->downstream.question(11L,List.of(v.getOssId()),"合成问题")).getCode());assertEquals("partial",materials.version(11L,v.getId()).getParseStatus());
        assertThrows(ServiceException.class,()->materials.retry(11L,v.getId(),UUID.randomUUID().toString(),List.of(1)));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_file_version",Integer.class));
    }
    @Test void foreignCaseTenantPreviewDownloadAndJobRequestsAreDeniedBeforeStorage()throws Exception{
        var v=upload("private.pdf",DossierFileReaderTest.pdf(1,false),null,false);int before=mockingDetails(oss).getInvocations().size();
        actor=202;
        assertThrows(ServiceException.class,()->materials.overview(11L));assertThrows(ServiceException.class,()->materials.preview(11L,v.getId(),1));assertThrows(ServiceException.class,()->materials.job(11L,v.getLastJobId()));
        assertThrows(ServiceException.class,()->materials.download(11L,v.getId(),new org.springframework.mock.web.MockHttpServletResponse()));
        actor=101;tenant="111111";assertThrows(ServiceException.class,()->materials.pages(11L,v.getId()));assertEquals(before,mockingDetails(oss).getInvocations().size());
    }
    @Test void blankRequiresExplicitManualActionAndChangeIsAudited()throws Exception{
        var reader=new DossierFileReader(image->"",mock(DocumentConversion.class));materials=service(reader);worker.close();worker=new DossierParseWorker(jdbc,tx,materials,reader);
        var v=upload("blank.pdf",DossierFileReaderTest.pdf(3,true),null,false);execute(v);var p=materials.pages(11L,v.getId()).get(2);assertEquals("EMPTY_UNCONFIRMED",p.getErrorCode());assertEquals("partial",materials.version(11L,v.getId()).getParseStatus());
        materials.confirmBlank(11L,v.getId(),3,p.getVersion());assertTrue(materials.pages(11L,v.getId()).get(2).getEmptyPageConfirmed());assertEquals("success",materials.version(11L,v.getId()).getParseStatus());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_parse_log WHERE action='manual_blank' AND operator_id=101",Integer.class));assertThrows(ServiceException.class,()->materials.confirmBlank(11L,v.getId(),3,p.getVersion()));
    }
    @Test void damagedFileKeepsOriginalAndUnknownCoverageWithoutFakePage()throws Exception{
        byte[] broken="%PDF-corrupt-synthetic".getBytes();var v=upload("broken.pdf",broken,null,false);execute(v);var saved=materials.version(11L,v.getId());
        assertEquals("failed",saved.getParseStatus());assertEquals("MALFORMED_DOCUMENT",saved.getErrorCode());assertNotNull(saved.getErrorMessage());assertNull(saved.getPageCount());assertTrue(materials.pages(11L,v.getId()).isEmpty());assertArrayEquals(broken,materials.checkedBytes(saved));
        assertEquals(1,materials.list(11L,"","issues",1,20).total());
    }
    @Test void interruptedParseMarksRemainingPagesFailedAndKeepsSuccessfulText()throws Exception{
        var v=upload("interrupted.pdf",DossierFileReaderTest.pdf(2,false),null,false);
        jdbc.update("UPDATE lawyer_case_file_version SET page_count=2 WHERE id=?",v.getId());
        for(int index=1;index<=2;index++)jdbc.update("INSERT INTO lawyer_case_file_page(id,tenant_id,case_id,file_version_id,page_index,extract_status,ocr_status,preview_status,extraction_method,text_content,parser_version,last_job_id,update_time) VALUES(?,'000000',11,?,?,?,'not_needed','success','text',?,?,?,UTC_TIMESTAMP())",DossierMaterialService.id(),v.getId(),index,index==1?"success":"queued",index==1?"SYNTHETIC RETAINED TEXT":null,DossierFileReader.VERSION,v.getLastJobId());
        var failedReader=mock(DossierFileReader.class);when(failedReader.open(any(),anyString())).thenThrow(new IllegalStateException("SYNTHETIC PRIVATE CONTENT"));
        worker.close();worker=new DossierParseWorker(jdbc,tx,materials,failedReader);execute(v);
        var rows=materials.pages(11L,v.getId());assertEquals("success",rows.get(0).getExtractStatus());assertEquals("SYNTHETIC RETAINED TEXT",rows.get(0).getTextContent());assertEquals("failed",rows.get(1).getExtractStatus());assertEquals("PARSE_UNAVAILABLE",rows.get(1).getErrorCode());assertFalse(rows.get(1).getErrorMessage().contains("PRIVATE"));
        assertEquals("partial",materials.version(11L,v.getId()).getParseStatus());assertEquals(1,materials.list(11L,"","issues",1,20).total());
    }
    @Test void revokedCaseCancelsQueuedWorkWithoutReadingStorageOrLeavingFileQueued()throws Exception{
        var v=upload("revoked.pdf",DossierFileReaderTest.pdf(1,false),null,false);
        int before=mockingDetails(oss).getInvocations().size();jdbc.update("UPDATE lawyer_case SET user_id=202 WHERE id=11");
        assertFalse(worker.claim(v.getLastJobId(),"revoked-claim"));assertEquals(before,mockingDetails(oss).getInvocations().size());
        assertEquals("cancelled",jdbc.queryForObject("SELECT state FROM lawyer_case_parse_job WHERE id=?",String.class,v.getLastJobId()));
        assertEquals("cancelled",jdbc.queryForObject("SELECT parse_status FROM lawyer_case_file_version WHERE id=?",String.class,v.getId()));
        assertEquals("ACCESS_REVOKED",jdbc.queryForObject("SELECT error_code FROM lawyer_case_file_version WHERE id=?",String.class,v.getId()));
    }
    @Test void legacyArchiveIsExtendedInPlaceAndAdoptionDoesNotDuplicateIt()throws Exception{
        byte[] bytes=DossierFileReaderTest.pdf(1,false);var asset=oss.uploadFile(new MockMultipartFile("file","legacy.pdf","application/pdf",bytes));
        jdbc.update("INSERT INTO lawyer_case_file(tenant_id,user_id,case_id,oss_id) VALUES('000000',101,11,?)",asset.getOssId());
        var row=materials.list(11L,"","",1,20).rows().get(0);assertNull(row.current());assertNotNull(row.material().getId());String key=UUID.randomUUID().toString();
        var first=materials.adopt(11L,row.material().getId(),key);assertEquals(first.getId(),materials.adopt(11L,row.material().getId(),key).getId());execute(first);
        assertEquals(1,materials.overview(11L).files());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_file",Integer.class));
    }
    @Test void encryptedOriginalIsStoredWithAnExplicitPasswordFailure()throws Exception{
        byte[] encrypted;
        try(var document=org.apache.pdfbox.Loader.loadPDF(DossierFileReaderTest.pdf(1,false));var out=new java.io.ByteArrayOutputStream()){
            var policy=new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner","secret",new org.apache.pdfbox.pdmodel.encryption.AccessPermission());policy.setEncryptionKeyLength(128);document.protect(policy);document.save(out);encrypted=out.toByteArray();
        }
        var v=upload("encrypted.pdf",encrypted,null,false);execute(v);var result=materials.version(11L,v.getId());assertEquals("failed",result.getParseStatus());assertEquals("ENCRYPTED_PDF",result.getErrorCode());assertNull(result.getPageCount());assertArrayEquals(encrypted,materials.checkedBytes(result));
    }
    @Test void realLocalOcrReadsNineScanPagesAndExplicitTestFaultKeepsThirdUnread()throws Exception{
        var nativeOcr=new LocalDossierOcr();org.springframework.test.util.ReflectionTestUtils.setField(nativeOcr,"language","en-US");
        var calls=new AtomicInteger();DossierOcr withFault=image->{calls.incrementAndGet();String text=nativeOcr.recognize(image);if(text.matches("(?s).*PAGE\\s*3\\b.*"))throw new DossierReadException("OCR_TIMEOUT","测试适配器明确注入第3物理页OCR超时");return text;};
        var reader=new DossierFileReader(withFault,mock(DocumentConversion.class));materials=service(reader);worker.close();worker=new DossierParseWorker(jdbc,tx,materials,reader);
        byte[] scan;
        try(var document=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()){
            for(int page=1;page<=10;page++){
                var image=new java.awt.image.BufferedImage(1200,1000,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,1200,1000);g.setColor(java.awt.Color.BLACK);g.setFont(new java.awt.Font("Arial",java.awt.Font.PLAIN,48));g.drawString("SYNTHETIC PAGE "+page,80,180);g.drawString("PAYMENT 50000.00",80,280);g.dispose();
                var pdfPage=new org.apache.pdfbox.pdmodel.PDPage();document.addPage(pdfPage);try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,pdfPage)){stream.drawImage(org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document,image),0,200,600,500);}
            }
            document.save(out);scan=out.toByteArray();
        }
        Files.write(evidence.resolve("F02-scan-ten-pages.pdf"),scan);
        var v=upload("F02-scan-ten-pages.pdf",scan,null,false);execute(v);var result=materials.version(11L,v.getId());
        assertEquals("partial",result.getParseStatus());assertEquals(10,result.getPageCount());assertEquals(9,result.getParsedPages());assertEquals(1,result.getFailedPages());assertEquals(10,calls.get());
        assertEquals("OCR_TIMEOUT",materials.pages(11L,v.getId()).get(2).getErrorCode());assertTrue(materials.pages(11L,v.getId()).get(0).getTextContent().contains("50000"));
        Files.writeString(evidence.resolve("local-ocr-result.json"),"{\"realLocalOcr\":true,\"scanPages\":10,\"readablePages\":9,\"failedPage\":3,\"faultHook\":\"test adapter only\",\"cloudCalls\":0,\"parseStatus\":\"partial\"}\n");
    }
    @Test void controllerReturnsActualStatusAndCannotExposeForeignMetadata()throws Exception{
        var v=upload("private.pdf",DossierFileReaderTest.pdf(1,false),null,false);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new org.ruoyi.controller.lawyer.DossierMaterialController(materials)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/lawyer/case/11/dossier/materials")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.total").value(1));
        actor=202;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/lawyer/case/11/dossier/files/"+v.getId()+"/pages")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private.pdf"))));
    }
}
