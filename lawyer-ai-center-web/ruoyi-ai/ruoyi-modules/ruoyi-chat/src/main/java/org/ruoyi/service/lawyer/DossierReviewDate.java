package org.ruoyi.service.lawyer;

import org.ruoyi.common.core.exception.ServiceException;
import java.time.LocalDate;
import java.util.*;

/** Human-reviewed uncertainty is preserved; an unknown/range is never coerced into a precise day. */
public final class DossierReviewDate {
    private DossierReviewDate(){}
    static boolean blank(String value){return value==null||value.isBlank();}
    public static void validate(Map<String,String> values,boolean required){String raw=values.get("dateRaw"),precision=values.get("datePrecision"),from=values.get("dateStart"),to=values.get("dateEnd");if(blank(precision))precision="exact";
        if(!Set.of("exact","range","unknown").contains(precision))throw new ServiceException("日期确定性无效",400);
        if("unknown".equals(precision)){if(!blank(from)||!blank(to))throw new ServiceException("无法确定日期时不能附造一个日期范围",422);return;}
        if("range".equals(precision)){try{if(blank(raw)||blank(from)||blank(to)||LocalDate.parse(from).isAfter(LocalDate.parse(to)))throw new IllegalArgumentException();}catch(Exception e){throw new ServiceException("请保留日期原文并填写有效的起止范围，不强填某个确切日",422);}return;}
        if(!blank(from)||!blank(to))throw new ServiceException("确切日期不能同时附日期范围",422);
        if((required||!blank(raw))&&BankStatementParser.date(raw)==null)throw new ServiceException("日期尚未明确；可选择无法确定或有效范围后人工核对",422);
    }
}
