package org.ruoyi.service.legal;

import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import java.time.LocalDate;
import java.util.*;

/** Optional conjunctive filters; missing metadata never qualifies as a positive match. */
public record SearchCriteria(String keyword, String dateFrom, String dateTo, String region, String topic) {
    public SearchCriteria {
        keyword=clean(keyword,100);dateFrom=clean(dateFrom,10);dateTo=clean(dateTo,10);region=clean(region,80);topic=clean(topic,80);
        try { if(!dateFrom.isEmpty())LocalDate.parse(dateFrom);if(!dateTo.isEmpty())LocalDate.parse(dateTo);
            if(!dateFrom.isEmpty()&&!dateTo.isEmpty()&&dateFrom.compareTo(dateTo)>0)throw new IllegalArgumentException();
        } catch(RuntimeException e){throw new ServiceException("日期筛选无效，请检查起止日期",400);}
    }
    private static String clean(String value,int max){String v=value==null?"":value.strip();if(v.length()>max)throw new ServiceException("筛选条件过长",400);return v;}
    public static SearchCriteria empty(){return new SearchCriteria(null,null,null,null,null);}
    public boolean accepts(KnowledgeRetrievalVo hit){
        String text=Objects.toString(hit.getSourceName(),"")+"\n"+Objects.toString(hit.getContent(),"");
        for(String term:keyword.split("\\s+"))if(!term.isEmpty()&&!text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)))return false;
        if(!topic.isEmpty()&&!text.contains(topic))return false;
        var meta=hit.getLegalMetadata();
        if(!region.isEmpty()&&(meta==null||!region.equals(meta.region())))return false;
        if(!dateFrom.isEmpty()||!dateTo.isEmpty()){
            if(meta==null||meta.publishDate()==null)return false;
            String date=meta.publishDate().toString();
            if(!dateFrom.isEmpty()&&date.compareTo(dateFrom)<0||!dateTo.isEmpty()&&date.compareTo(dateTo)>0)return false;
        }
        return true;
    }
    public String sql(List<Object> params){
        var out=new StringBuilder();
        for(String term:keyword.split("\\s+"))if(!term.isEmpty()){out.append(" AND LOCATE(?,CONCAT(COALESCE(a.name,''),' ',f.content))>0");params.add(term);}
        if(!topic.isEmpty()){out.append(" AND LOCATE(?,CONCAT(COALESCE(a.name,''),' ',f.content))>0");params.add(topic);}
        if(!region.isEmpty()){out.append(" AND a.region=?");params.add(region);}
        if(!dateFrom.isEmpty()){out.append(" AND a.publish_date>=?");params.add(LocalDate.parse(dateFrom));}
        if(!dateTo.isEmpty()){out.append(" AND a.publish_date<=?");params.add(LocalDate.parse(dateTo));}
        return out.toString();
    }
}
