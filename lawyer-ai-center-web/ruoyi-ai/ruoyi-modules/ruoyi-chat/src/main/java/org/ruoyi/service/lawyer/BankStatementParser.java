package org.ruoyi.service.lawyer;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic extraction from persisted page text. Never reads fixtures or confirms a transaction. */
public final class BankStatementParser {
    public static final String VERSION="bank-columns-v1";
    public record Row(int tableRow,int start,int end,String fragment,String timeRaw,String at,String amountRaw,
        BigDecimal amount,String direction,String currency,String balanceRaw,BigDecimal balance,String account,
        String counterAccount,String counterparty,String reference,String summary,List<String> reasons,List<String> anomalies){}
    public record Result(List<Row> rows,List<String> unparsed,List<String> warnings){}
    private static final Pattern DATE=Pattern.compile("(?:(?:19|20)[0-9]{2}[-/年][0-9]{1,2}[-/月][0-9]{1,2}日?|(?:19|20)[0-9]{6})(?![0-9])");
    private BankStatementParser(){}
    public static BigDecimal money(String raw){
        if(raw==null||raw.isBlank()||Set.of("-","—","--","未知","不明").contains(raw.strip()))return null;
        String value=raw.strip().replace("人民币","").replace("CNY","").replace("RMB","").replace("￥","").replace("¥","").replace("元","").strip();
        boolean bracket=value.startsWith("(")&&value.endsWith(")")||value.startsWith("（")&&value.endsWith("）");
        if(bracket)value="-"+value.substring(1,value.length()-1);
        if(!value.matches("[+-]?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?"))return null;
        try{var result=new BigDecimal(value.replace(",","")).setScale(2);return result.precision()<=20?result:null;}catch(Exception e){return null;}
    }
    public static String date(String raw){
        if(raw==null)return null;String value=raw.strip().replace('年','-').replace('月','-').replace("日","").replace('/','-');
        for(String pattern:List.of("uuuu-M-d HH:mm:ss","uuuu-M-d HH:mm","uuuu-M-d'T'HH:mm:ss","uuuu-M-d","uuuuMMdd"))try{
            var formatter=DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
            return pattern.contains("H")?LocalDateTime.parse(value,formatter).toString():LocalDate.parse(value,formatter).toString();
        }catch(DateTimeException ignored){}return null;
    }
    public static String direction(String raw){
        if(raw==null)return "unknown";String v=raw.strip().toLowerCase(Locale.ROOT);
        if(Set.of("收入","入账","收","贷","贷方","credit","income","in","cr").contains(v))return "income";
        if(Set.of("支出","出账","付","借","借方","debit","expense","out","dr").contains(v))return "expense";return "unknown";
    }
    static String field(String raw){
        String v=raw.toLowerCase(Locale.ROOT).replaceAll("[\\s()（）:：_/-]","");
        if(v.equals("原始记录行"))return "sourceRow";
        if(Set.of("日期","交易日期","交易时间","记账日期","date","time","datetime").contains(v))return "date";
        if(Set.of("收支","方向","借贷","借贷标志","direction","type").contains(v))return "direction";
        if(Set.of("金额","交易金额","发生额","amount").contains(v))return "amount";
        if(Set.of("收入","收入金额","贷方发生额","credit").contains(v))return "income";
        if(Set.of("支出","支出金额","借方发生额","debit").contains(v))return "expense";
        if(Set.of("余额","账户余额","balance").contains(v))return "balance";
        if(Set.of("币种","货币","currency").contains(v))return "currency";
        if(Set.of("账号","本方账号","账户","account").contains(v))return "account";
        if(Set.of("对方账号","对方账户","counteraccount").contains(v))return "counterAccount";
        if(Set.of("对方户名","对方","交易对手","counterparty","name").contains(v))return "counterparty";
        if(Set.of("流水号","交易号","交易参考号","参考号","reference","ref").contains(v))return "reference";
        if(Set.of("摘要","用途","备注","summary","memo","description").contains(v))return "summary";return "";
    }
    static List<String> cells(String line){
        if(line.indexOf('\t')>=0)return Arrays.asList(line.split("\\t",-1));
        if(line.indexOf('|')>=0)return Arrays.asList(line.replaceAll("^\\||\\|$","").split("\\|",-1));
        if(line.contains(",")&&line.contains("\""))return csv(line,',');
        if(line.indexOf(',')>=0&&line.matches(".*(?:date|日期|direction|收支|amount|金额),.*"))return csv(line,',');
        if(line.matches(".*\\s{2,}.*"))return Arrays.asList(line.strip().split("\\s{2,}"));
        return Arrays.asList(line.strip().split("\\s+"));
    }
    /** RFC4180 quoting, including embedded newlines; formulas remain inert text. */
    public static List<List<String>> csvDocument(String text){
        var rows=new ArrayList<List<String>>();var row=new ArrayList<String>();var value=new StringBuilder();boolean quoted=false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);
            if(c=='"'){if(quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){value.append('"');i++;}else if(quoted||value.length()==0)quoted=!quoted;else value.append(c);}
            else if(c==','&&!quoted){row.add(value.toString());value.setLength(0);}
            else if((c=='\n'||c=='\r')&&!quoted){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(value.toString());if(row.stream().anyMatch(s->!s.isBlank()))rows.add(List.copyOf(row));row.clear();value.setLength(0);}
            else value.append(c);
            if(rows.size()>10000||row.size()>50||value.length()>50000)throw new DossierReadException("TABLE_LIMIT","表格超过10000行、50列或单元格过长，请拆分");
        }
        if(quoted)throw new DossierReadException("CSV_INVALID","CSV引号不完整，请检查文件编码与分隔格式");
        if(value.length()>0||!row.isEmpty()){row.add(value.toString());rows.add(List.copyOf(row));}return rows;
    }
    static List<String> csv(String text,char ignored){var rows=csvDocument(text);return rows.isEmpty()?List.of():rows.get(0);}
    public static Result parse(String text){
        var result=new ArrayList<Row>();var unparsed=new ArrayList<String>();var warnings=new ArrayList<String>();
        List<String> header=List.of();int offset=0,number=0;boolean comma=false;
        for(String line:text.split("\n",-1)){
            int start=offset;offset+=line.length()+1;String stripped=line.strip();if(stripped.isEmpty())continue;
            var values=comma?csv(line,','):cells(line);var proposed=values.stream().map(String::strip).map(BankStatementParser::field).toList();
            if(proposed.contains("date")&&(proposed.contains("amount")||proposed.contains("income")||proposed.contains("expense"))){header=proposed;comma=line.contains(",")&&!line.contains("|")&&!line.contains("\t");continue;}
            if(stripped.matches("(?i)^(合计|总计|本页汇总|期末余额|subtotal|total)\\b.*")||stripped.matches("^(合计|总计|本页汇总|期末余额).*")){warnings.add("FOOTER_SKIPPED:"+start);continue;}
            if(!DATE.matcher(stripped).find()){if(!stripped.matches("(?i)^(SYNTHETIC|sheet:|工作表|系统分页|page |account:|账户[:：]).*"))unparsed.add(start+":"+line);continue;}
            var reasons=new ArrayList<String>();var anomalies=new ArrayList<String>();Map<String,String> fields=new HashMap<>();
            if(header.isEmpty())reasons.add("HEADER_UNRECOGNIZED");
            else{if(values.size()!=header.size()){reasons.add("COLUMN_MISALIGNMENT");anomalies.add("COLUMN_MISALIGNMENT");}
                if(values.size()==header.size())for(int col=0;col<header.size();col++)if(!header.get(col).isEmpty())fields.put(header.get(col),values.get(col).strip());}
            String rawDate=fields.get("date"),at=date(rawDate),rawAmount=fields.get("amount"),dir=direction(fields.get("direction"));
            if(rawAmount==null){var in=money(fields.get("income"));var out=money(fields.get("expense"));
                if(in!=null&&in.signum()!=0&&out!=null&&out.signum()!=0){reasons.add("BOTH_DEBIT_CREDIT");anomalies.add("DIRECTION_CONFLICT");}
                else if(in!=null&&in.signum()!=0){rawAmount=fields.get("income");dir="income";}else if(out!=null&&out.signum()!=0){rawAmount=fields.get("expense");dir="expense";}}
            var amount=money(rawAmount);var balance=money(fields.get("balance"));
            if(at==null)reasons.add("DATE_UNPARSED");else{var d=LocalDate.parse(at.substring(0,10));if(d.isBefore(LocalDate.of(1900,1,1))||d.isAfter(LocalDate.now(ZoneOffset.UTC).plusYears(1)))anomalies.add("DATE_OUT_OF_RANGE");}
            if(amount==null)reasons.add("AMOUNT_UNPARSED");else if(amount.signum()<0){if(dir.equals("income"))anomalies.add("DIRECTION_CONFLICT");if(dir.equals("unknown"))dir="expense";}
            if(balance==null)reasons.add("BALANCE_UNPARSED");if(dir.equals("unknown"))reasons.add("DIRECTION_UNKNOWN");
            String currency=fields.get("currency");if(currency!=null){currency=currency.toUpperCase(Locale.ROOT);if(Set.of("RMB","人民币","¥","￥").contains(currency))currency="CNY";if(!currency.matches("[A-Z]{3}")){currency=null;reasons.add("CURRENCY_UNPARSED");}}else reasons.add("CURRENCY_UNKNOWN");
            for(String value:values)if(value.stripLeading().matches("^[=+@].*"))anomalies.add("FORMULA_LIKE_TEXT");
            int tableRow=++number;try{if(fields.containsKey("sourceRow"))tableRow=Integer.parseInt(fields.get("sourceRow"));}catch(NumberFormatException ignored){reasons.add("SOURCE_ROW_UNPARSED");}
            result.add(new Row(tableRow,start,start+line.length(),line,rawDate,at,rawAmount,amount==null?null:amount.abs(),dir,currency,fields.get("balance"),balance,fields.get("account"),fields.get("counterAccount"),fields.get("counterparty"),fields.get("reference"),fields.get("summary"),List.copyOf(reasons),anomalies.stream().distinct().toList()));
        }
        return new Result(List.copyOf(result),List.copyOf(unparsed),List.copyOf(warnings));
    }
    public static String mask(String raw){if(raw==null)return null;String value=raw.strip();return value.length()<3?"*".repeat(value.length()):value.substring(0,1)+"***"+value.substring(value.length()-1);}
    public static String safeText(String raw){return raw==null?"":raw.replaceAll("(?<![0-9])([0-9]{3})[0-9]{4,}([0-9]{3})(?![0-9])","$1***$2");}
}
