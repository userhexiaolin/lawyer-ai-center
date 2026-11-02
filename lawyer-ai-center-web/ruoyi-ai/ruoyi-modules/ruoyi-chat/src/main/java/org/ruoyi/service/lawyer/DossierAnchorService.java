package org.ruoyi.service.lawyer;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.entity.lawyer.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;

/** Immutable location evidence. Location validity never asserts that a statement is true. */
@Service @RequiredArgsConstructor
public class DossierAnchorService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DossierMaterialService materials;
    private final DossierFileReader reader;
    private final DossierOcr ocr;
    public record Principal(String tenantId,Long caseId,Long userId){}
    public record Region(double x,double y,double width,double height){}
    public record Create(Long fileVersionId,Integer pageIndex,String kind,Integer start,Integer end,
                         String fragment,Region region,Integer tableRow,Integer tableColumn,boolean pinHistory){}
    public record Resolved(CaseSourceAnchor anchor,String fileName,String pageLabel,boolean current,boolean verified,String error,String pageText){}
    Principal principal(Long caseId){materials.access(caseId);return new Principal(DossierMaterialService.tenant(),caseId,DossierMaterialService.actor());}
    void owned(Principal p){if(p==null||p.userId()==null||jdbc.queryForList("SELECT id FROM lawyer_case WHERE id=? AND tenant_id=? AND user_id=? AND deleted=0",p.caseId(),p.tenantId(),p.userId()).isEmpty())throw new ServiceException("案件不可用",404);}
    CaseFileVersion version(Principal p,Long id){owned(p);var rows=jdbc.query("SELECT * FROM lawyer_case_file_version WHERE id=? AND case_id=? AND tenant_id=? AND user_id=?",DossierMaterialService.mapping(CaseFileVersion.class),id,p.caseId(),p.tenantId(),p.userId());if(rows.isEmpty())throw new ServiceException("来源不可用",404);return rows.get(0);}
    CaseFilePage page(Principal p,Long version,Integer page){if(page==null||page<1)throw new ServiceException("必须指定有效物理页，片段不能代替页码",422);var rows=jdbc.query("SELECT * FROM lawyer_case_file_page WHERE file_version_id=? AND tenant_id=? AND case_id=? AND page_index=?",DossierMaterialService.mapping(CaseFilePage.class),version,p.tenantId(),p.caseId(),page);if(rows.isEmpty())throw new ServiceException("来源物理页不存在",422);return rows.get(0);}
    boolean current(Principal p,CaseFileVersion v){return !jdbc.queryForList("SELECT id FROM lawyer_case_file WHERE id=? AND case_id=? AND tenant_id=? AND user_id=? AND current_version_id=?",v.getMaterialId(),p.caseId(),p.tenantId(),p.userId(),v.getId()).isEmpty();}
    static String hash(String value){return DossierFiles.hash(value.getBytes(StandardCharsets.UTF_8));}
    static String text(CaseFilePage p){return Objects.toString(p.getTextContent(),"");}
    static void fragment(CaseFilePage p,Integer start,Integer end,String fragment){String text=text(p);if(!"success".equals(p.getExtractStatus())||!Objects.equals(p.getTextHash(),hash(text)))throw new ServiceException("来源页未读成功或文本指纹变化",422);if(start==null||end==null||start<0||end<=start||end>text.length()||end-start>6000||fragment==null||fragment.isBlank()||!text.substring(start,end).equals(fragment))throw new ServiceException("来源片段与指定字符范围不一致",422);}
    public CaseSourceAnchor create(Long caseId,Create input){return create(principal(caseId),input);}
    CaseSourceAnchor create(Principal p,Create input){
        if(input==null||input.kind()==null||!Set.of("text_offset","table_cell","ocr_bbox","image_region").contains(input.kind()))throw new ServiceException("来源定位类型无效",400);
        var v=version(p,input.fileVersionId());var page=page(p,v.getId(),input.pageIndex());
        if(!current(p,v)&&!input.pinHistory())throw new ServiceException("历史版本需要明确固定后才能引用",409);
        String imageHash=null,regionHash=null,geometry=null;
        if(!input.kind().equals("image_region"))fragment(page,input.start(),input.end(),input.fragment());
        else if(input.start()!=null||input.end()!=null||(input.fragment()!=null&&!input.fragment().isBlank()))throw new ServiceException("人工图像区域不能冒充机器读取的文字；请在事实编辑中填写人工判断",422);
        if(input.kind().equals("table_cell"))table(page,input);
        if(Set.of("ocr_bbox","image_region").contains(input.kind())){
            byte[] png=preview(p,v,page.getPageIndex());var image=image(png);var crop=crop(image,input.region());
            imageHash=DossierFiles.hash(png);regionHash=pngHash(crop);geometry=BankStatementService.json(input.region());
            if(input.kind().equals("ocr_bbox")&&!ocr.recognize(crop).replaceAll("\\s+","").equals(input.fragment().replaceAll("\\s+","")))throw new ServiceException("本地区域OCR无法核对该片段，请改用文字范围或明确的人工图像区域",422);
        }else if(input.region()!=null)throw new ServiceException("文字/表格定位不接受伪造图像坐标",400);
        String fingerprint=hash(BankStatementService.json(Arrays.asList(v.getId().toString(),v.getSha256(),page.getId().toString(),hash(text(page)),input.kind(),input.start(),input.end(),Objects.toString(input.fragment(),""),imageHash,regionHash,geometry,input.tableRow(),input.tableColumn(),input.pinHistory())));
        final String ih=imageHash,rh=regionHash,geo=geometry;
        return tx.execute(s->{owned(p);var prior=jdbc.query("SELECT * FROM lawyer_case_source_anchor WHERE tenant_id=? AND case_id=? AND fingerprint=?",DossierMaterialService.mapping(CaseSourceAnchor.class),p.tenantId(),p.caseId(),fingerprint);if(!prior.isEmpty())return prior.get(0);long id=DossierMaterialService.id();jdbc.update("INSERT INTO lawyer_case_source_anchor(id,tenant_id,case_id,user_id,file_version_id,page_id,page_index,kind,fragment_start,fragment_end,fragment_text,page_hash,file_hash,image_hash,region_hash,geometry_json,table_row,table_column,pinned_history,fingerprint,create_by,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())",id,p.tenantId(),p.caseId(),p.userId(),v.getId(),page.getId(),page.getPageIndex(),input.kind(),input.start(),input.end(),Objects.toString(input.fragment(),""),hash(text(page)),v.getSha256(),ih,rh,geo,input.tableRow(),input.tableColumn(),input.pinHistory(),fingerprint,p.userId());return get(p,id);});
    }
    private static void table(CaseFilePage page,Create input){
        if(input.tableRow()==null||input.tableColumn()==null||input.tableRow()<1||input.tableColumn()<1)throw new ServiceException("需提供原始表格行列",422);
        String text=text(page);int begin=text.lastIndexOf('\n',Math.max(0,input.start()-1))+1,end=text.indexOf('\n',input.start());if(end<0)end=text.length();String line=text.substring(begin,end);String[] cells=line.split("\t",-1);
        if(cells.length<2||!cells[0].strip().equals(input.tableRow().toString())||input.tableColumn()>=cells.length)throw new ServiceException("页文本无法核对该原始表格行列",422);
        int cellStart=begin;for(int i=0;i<input.tableColumn();i++)cellStart+=cells[i].length()+1;int cellEnd=cellStart+cells[input.tableColumn()].length();
        if(input.start()<cellStart||input.end()>cellEnd)throw new ServiceException("字符范围不属于指定表格单元格",422);
    }
    CaseSourceAnchor get(Principal p,Long id){owned(p);var rows=jdbc.query("SELECT * FROM lawyer_case_source_anchor WHERE id=? AND tenant_id=? AND case_id=? AND user_id=?",DossierMaterialService.mapping(CaseSourceAnchor.class),id,p.tenantId(),p.caseId(),p.userId());if(rows.isEmpty())throw new ServiceException("来源不可用",404);return rows.get(0);}
    public Resolved resolve(Long caseId,Long id){return resolve(principal(caseId),id,true);}
    Resolved resolve(Principal p,Long id,boolean checkOriginal){
        var a=get(p,id);var v=version(p,a.getFileVersionId());var page=page(p,v.getId(),a.getPageIndex());boolean current=current(p,v);String error=null;
        try{
            if(!Objects.equals(v.getSha256(),a.getFileHash())||!Objects.equals(page.getId(),a.getPageId())||!Objects.equals(hash(text(page)),a.getPageHash())||(page.getTextHash()!=null&&!Objects.equals(page.getTextHash(),hash(text(page)))))throw new ServiceException("SOURCE_HASH_CHANGED",409);
            if(!current&&!Boolean.TRUE.equals(a.getPinnedHistory()))throw new ServiceException("SOURCE_VERSION_REPLACED",409);
            if(!"image_region".equals(a.getKind()))fragment(page,a.getFragmentStart(),a.getFragmentEnd(),a.getFragmentText());
            if(a.getGeometryJson()!=null){byte[] png=preview(p,v,page.getPageIndex());Region region=new com.fasterxml.jackson.databind.ObjectMapper().readValue(a.getGeometryJson(),Region.class);if(!DossierFiles.hash(png).equals(a.getImageHash())||!pngHash(crop(image(png),region)).equals(a.getRegionHash()))throw new ServiceException("SOURCE_IMAGE_CHANGED",409);}
            else if(checkOriginal)materials.checkedBytes(v);
        }catch(Exception e){error=e instanceof ServiceException se&&se.getMessage().startsWith("SOURCE_")?se.getMessage():"SOURCE_UNVERIFIED";}
        return new Resolved(a,v.getFileName(),page.getPageLabel(),current,error==null,error,text(page));
    }
    byte[] preview(Principal p,CaseFileVersion v,int page){owned(p);try(var source=reader.open(materials.checkedBytes(v),v.getMime())){return source.preview(page);}catch(Exception e){throw new ServiceException("原页暂不可预览，可下载原件人工定位",503);}}
    static BufferedImage image(byte[] bytes){try{var image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null)throw new IOException();return image;}catch(Exception e){throw new ServiceException("原页图像不可读取",422);}}
    static BufferedImage crop(BufferedImage image,Region r){if(r==null||!Double.isFinite(r.x())||!Double.isFinite(r.y())||!Double.isFinite(r.width())||!Double.isFinite(r.height())||r.x()<0||r.y()<0||r.width()<=0||r.height()<=0||r.x()+r.width()>1||r.y()+r.height()>1)throw new ServiceException("图像区域超出真实页范围",422);int x=(int)Math.floor(r.x()*image.getWidth()),y=(int)Math.floor(r.y()*image.getHeight()),w=(int)Math.ceil(r.width()*image.getWidth()),h=(int)Math.ceil(r.height()*image.getHeight());return image.getSubimage(x,y,Math.min(w,image.getWidth()-x),Math.min(h,image.getHeight()-y));}
    static String pngHash(BufferedImage image){try(var out=new ByteArrayOutputStream()){ImageIO.write(image,"png",out);return DossierFiles.hash(out.toByteArray());}catch(Exception e){throw new ServiceException("来源图像指纹不可计算",422);}}
}
