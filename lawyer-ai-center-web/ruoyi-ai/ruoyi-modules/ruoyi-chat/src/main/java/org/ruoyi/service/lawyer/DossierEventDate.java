package org.ruoyi.service.lawyer;

import org.ruoyi.common.core.exception.ServiceException;
import java.time.*;
import java.util.*;

/** Persist the given precision; sorting bounds are derived and never replace raw dates. */
public final class DossierEventDate {
    private DossierEventDate(){}
    public record Bounds(LocalDate start,LocalDate end,String precision){}
    public static Bounds partial(String value){
        if(value==null||value.isBlank())return null;
        if(value.startsWith("0000"))throw new ServiceException("年份必须为0001至9999",400);
        try{
            if(value.matches("[0-9]{4}")){var y=Year.parse(value);return new Bounds(y.atDay(1),y.atMonth(12).atEndOfMonth(),"year");}
            if(value.matches("[0-9]{4}-[0-9]{2}")){var m=YearMonth.parse(value);return new Bounds(m.atDay(1),m.atEndOfMonth(),"month");}
            if(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")){var d=LocalDate.parse(value);return new Bounds(d,d,"day");}
        }catch(Exception ignored){}
        throw new ServiceException("日期格式或日历日期无效，请保留原始表达并选择未知/相对日期",400);
    }
    static boolean empty(String s){return s==null||s.isBlank();}
    public static void validate(String kind,String raw,String start,String end,String timezone){
        if(!Set.of("exact","range","approximate","relative","unknown").contains(Objects.toString(kind,""))||raw==null||raw.length()>500)throw new ServiceException("事件日期确定性无效",400);
        if(!empty(timezone))try{ZoneId.of(timezone);}catch(Exception e){throw new ServiceException("时区无效",400);}
        if(Set.of("unknown","relative").contains(kind)){if(!empty(start)||!empty(end))throw new ServiceException("未知/相对日期不得填入推测的绝对日期",400);if(kind.equals("relative")&&raw.isBlank())throw new ServiceException("相对日期必须保留原始表达",400);return;}
        if(raw.isBlank())throw new ServiceException("需保留日期原始表达",400);
        var a=partial(start);var b=partial(end);
        if(kind.equals("exact")&&(a==null||!a.precision().equals("day")||b!=null&&!start.equals(end)))throw new ServiceException("确切日必须为原件可核对的日，不接受月份替代",400);
        if(kind.equals("range")&&(a==null||b==null||!a.precision().equals("day")||!b.precision().equals("day")))throw new ServiceException("日期区间需完整起止日",400);
        if(a==null&&b!=null||a!=null&&b!=null&&a.start().isAfter(b.end()))throw new ServiceException("日期区间起止顺序无效",400);
    }
    public static String inferredKind(String raw){return raw!=null&&raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")?"exact":raw!=null&&(raw.matches("[0-9]{4}(?:-[0-9]{2})?")||raw.matches(".*(?:前后|大约|约|季度|月份).*"))?"approximate":raw!=null&&raw.matches(".*(?:次日|翌日|同年|此后|之前|之后|当天).*" )?"relative":"unknown";}
}
