import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/** Explicit-target additive migrations. No isolated-business-data import and no credential output. */
class Stage4Migration {
    static final List<String> FILES=List.of("2026-10-17-legal-citation-source.sql","2026-10-17-legal-search-report.sql","2026-10-17-case-tasks.sql","2026-10-17-legal-opinion.sql","2026-10-18-contract-review-issues.sql","2026-10-18-personal-reminders.sql");
    static final List<String> TABLES=List.of("knowledge_attach","legal_search_report","legal_search_report_item","lawyer_case_task","lawyer_case_checklist_item","lawyer_case_task_time","sys_notice");
    static Path root=Path.of(".").toAbsolutePath().normalize(), sqlRoot=root.resolve("docs/script/sql/update"), output;
    static String env(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing environment variable: "+key);return value;}
    static String id(String value){if(!value.matches("[a-zA-Z0-9_-]+"))throw new IllegalArgumentException("Unsafe database identifier");return "`"+value+"`";}
    static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static String hashFile(Path file)throws Exception{var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(file)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)digest.update(b,0,n);}return HexFormat.of().formatHex(digest.digest());}
    static String json(Object v){if(v==null)return "null";if(v instanceof Number||v instanceof Boolean)return v.toString();if(v instanceof Map<?,?> m)return "{"+String.join(",",m.entrySet().stream().map(e->json(e.getKey().toString())+":"+json(e.getValue())).toList())+"}";if(v instanceof Collection<?> c)return "["+String.join(",",c.stream().map(Stage4Migration::json).toList())+"]";return "\""+v.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
    static void save(String name,Object data)throws Exception{Files.createDirectories(output);Files.writeString(output.resolve(name+".json"),json(data)+"\n",StandardCharsets.UTF_8);}
    static List<String> statements(String source){
        var rows=new ArrayList<String>();var b=new StringBuilder();char quote=0;boolean line=false,block=false;
        for(int i=0;i<source.length();i++){char c=source.charAt(i),next=i+1<source.length()?source.charAt(i+1):0;
            if(line){if(c=='\n'){line=false;b.append(c);}continue;}
            if(block){if(c=='*'&&next=='/'){block=false;i++;b.append(' ');}continue;}
            if(quote!=0){b.append(c);if(c=='\\'&&next!=0){b.append(next);i++;}else if(c==quote){if(next==quote){b.append(next);i++;}else quote=0;}continue;}
            if(c=='-'&&next=='-'&&(i+2==source.length()||Character.isWhitespace(source.charAt(i+2)))){line=true;i++;continue;}
            if(c=='/'&&next=='*'){block=true;i++;continue;}
            if(c=='\''||c=='"'||c=='`'){quote=c;b.append(c);continue;}
            if(c==';'){if(!b.toString().isBlank())rows.add(b.toString().trim());b.setLength(0);}else b.append(c);
        }
        if(quote!=0||block)throw new IllegalArgumentException("Unclosed SQL quote/comment");if(!b.toString().isBlank())rows.add(b.toString().trim());return rows;
    }
    static List<String> columns(Connection db,String table)throws Exception{var columns=new ArrayList<String>();try(var r=db.getMetaData().getColumns(db.getCatalog(),null,table,null)){while(r.next())columns.add(r.getString("COLUMN_NAME"));}return columns;}
    static long count(Connection db,String sql)throws Exception{try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    static void requireColumns(Connection db,String table,String... fields)throws Exception{var actual=columns(db,table);for(String field:fields)if(!actual.contains(field))throw new IllegalStateException("Missing prerequisite "+table+"."+field);}
    static void prerequisites(Connection db)throws Exception{
        requireColumns(db,"sys_notice","notice_id","tenant_id","notice_title","notice_content");
        requireColumns(db,"knowledge_attach","law_name","article_no","validity_status");requireColumns(db,"knowledge_fragment","fid");
        requireColumns(db,"lawyer_case","id","tenant_id","user_id");requireColumns(db,"lawyer_case_file","case_id","oss_id");requireColumns(db,"chat_session","case_id");
        requireColumns(db,"agent_info","system_prompt","knowledge_ids","model_id");
        if(count(db,"SELECT COUNT(*) FROM agent_info WHERE agent_name='合同审查官' AND tenant_id='000000'")!=1)throw new IllegalStateException("Expected exactly one review agent in tenant 000000");
        if(count(db,"SELECT COUNT(*) FROM agent_info WHERE agent_name IN ('法律意见书助手','文书起草助手') AND status='0'")==0)throw new IllegalStateException("No enabled drafting/opinion agent for opinion migration");
    }
    static Map<String,Object> audit(Connection db)throws Exception{
        var result=new LinkedHashMap<String,Object>();result.put("time",java.time.OffsetDateTime.now().toString());result.put("database",db.getCatalog());result.put("operator",env("STAGE4_OPERATOR"));
        var tables=new LinkedHashMap<String,Object>();for(String table:TABLES)tables.put(table,columns(db,table));result.put("tables",tables);
        result.put("reviewIssuesPrompt",count(db,"SELECT COUNT(*) FROM agent_info WHERE agent_name='合同审查官' AND tenant_id='000000' AND system_prompt LIKE '%阶段4指令43%' AND system_prompt LIKE '%caseRefs%'")==1);
        result.put("opinionAgents",count(db,"SELECT COUNT(*) FROM agent_info WHERE agent_name='法律意见书助手' AND status='0'"));return result;
    }
    static void validate(Connection db)throws Exception{
        requireColumns(db,"sys_notice","recipient_user_id","reminder_key","related_case_id","read_time");
        var noticeIndexes=Map.of("uk_notice_recipient_key","tenant_id,recipient_user_id,reminder_key","idx_notice_inbox","tenant_id,recipient_user_id,status,notice_id");
        for(var entry:noticeIndexes.entrySet()){
            var keys=new TreeMap<Integer,String>();
            try(var r=db.getMetaData().getIndexInfo(db.getCatalog(),null,"sys_notice",entry.getKey().startsWith("uk_"),false)){while(r.next())if(entry.getKey().equals(r.getString("INDEX_NAME")))keys.put(r.getInt("ORDINAL_POSITION"),r.getString("COLUMN_NAME"));}
            if(!String.join(",",keys.values()).equals(entry.getValue()))throw new IllegalStateException("Missing/incompatible notice index: "+entry.getKey());
        }
        requireColumns(db,"knowledge_attach","case_no","court","source_url");
        for(var item:Map.of("case_no",200,"court",300,"source_url",2000).entrySet())try(var r=db.getMetaData().getColumns(db.getCatalog(),null,"knowledge_attach",item.getKey())){if(!r.next()||r.getInt("COLUMN_SIZE")<item.getValue())throw new IllegalStateException("Source column too short: "+item.getKey());}
        for(String file:FILES.subList(1,3))for(String sql:statements(Files.readString(sqlRoot.resolve(file)))){
            var match=Pattern.compile("(?is)^CREATE TABLE IF NOT EXISTS (\\w+)\\s*\\((.*)\\)\\s*ENGINE").matcher(sql);if(!match.find())continue;
            String table=match.group(1),body=match.group(2);var names=new ArrayList<String>();var matcher=Pattern.compile("(?m)^\\s*(\\w+)\\s+(?:BIGINT|VARCHAR|MEDIUMTEXT|DATETIME|TINYINT|CHAR|INT)\\b",Pattern.CASE_INSENSITIVE).matcher(body);while(matcher.find())names.add(matcher.group(1));requireColumns(db,table,names.toArray(String[]::new));
            var expected=Pattern.compile("(?im)^\\s*(?:UNIQUE\\s+)?KEY\\s+(\\w+)\\s*\\(([^)]+)\\)").matcher(body);
            while(expected.find()){String index=expected.group(1);var keys=new TreeMap<Integer,String>();try(var r=db.getMetaData().getIndexInfo(db.getCatalog(),null,table,false,false)){while(r.next())if(index.equals(r.getString("INDEX_NAME")))keys.put(r.getInt("ORDINAL_POSITION"),r.getString("COLUMN_NAME"));}if(!String.join(",",keys.values()).equals(expected.group(2).replaceAll("\\s+","")))throw new IllegalStateException("Missing/incompatible index: "+table+"."+index);}
        }
        if(!(Boolean)audit(db).get("reviewIssuesPrompt")||((Number)audit(db).get("opinionAgents")).longValue()<1)throw new IllegalStateException("Agent protocol migration not present");
    }
    static void verifySmoke(Connection db)throws Exception{
        String record=Files.readString(Path.of(env("STAGE4_SMOKE_RECORD_FILE")));
        var values=new LinkedHashMap<String,Long>();for(String field:List.of("userId","reportId","caseId","taskId")){
            var match=Pattern.compile("\""+field+"\"\\s*:\\s*\"([0-9]+)\"").matcher(record);if(!match.find())throw new IllegalStateException("Smoke evidence missing "+field);values.put(field,Long.parseLong(match.group(1)));
        }
        long user=values.get("userId");if(user==1)throw new IllegalStateException("Administrator smoke rejected");
        if(count(db,"SELECT COUNT(*) FROM legal_search_report WHERE id="+values.get("reportId")+" AND user_id="+user+" AND status='generated' AND del_flag=2")!=1)throw new IllegalStateException("Smoke report not found in expected original database");
        if(count(db,"SELECT COUNT(*) FROM lawyer_case_task t JOIN lawyer_case_checklist_item c ON c.task_id=t.id WHERE t.id="+values.get("taskId")+" AND t.case_id="+values.get("caseId")+" AND t.user_id="+user+" AND c.checked=1")!=1)throw new IllegalStateException("Checked task not found in expected original database");
        var ids=new LinkedHashMap<String,String>();values.forEach((key,value)->ids.put(key,value.toString()));
        var result=new LinkedHashMap<String,Object>();result.put("database",db.getCatalog());result.put("time",java.time.OffsetDateTime.now().toString());result.put("operator",env("STAGE4_OPERATOR"));result.put("ids",ids);result.put("pass",true);result.put("proof","API-created report and checked task exist in the explicitly selected original database");
        save("database-smoke-"+Path.of(env("STAGE4_SMOKE_RECORD_FILE")).getFileName().toString().replace("deploy-smoke-", "").replace(".json", ""),result);System.out.println(json(result));
    }
    static void apply(Connection db,boolean rehearsal)throws Exception{
        prerequisites(db);
        if(!rehearsal){Path backup=Path.of(env("STAGE4_BACKUP_FILE"));if(!Files.isRegularFile(backup)||Files.size(backup)<100)throw new IllegalStateException("Verified original-database backup required");if(!hashFile(backup).equals(env("STAGE4_BACKUP_SHA256")))throw new IllegalStateException("Backup checksum mismatch");}
        String lock="stage4_"+hash(db.getCatalog().getBytes(StandardCharsets.UTF_8)).substring(0,32);
        try(var s=db.prepareStatement("SELECT GET_LOCK(?,10)")){s.setString(1,lock);try(var r=s.executeQuery()){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Migration lock unavailable");}}
        try{
            try(var s=db.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS stage4_schema_migration(version VARCHAR(200) PRIMARY KEY,sha256 CHAR(64) NOT NULL,operator VARCHAR(200) NOT NULL,applied_at DATETIME NOT NULL)");}
            // Check every checksum before applying anything; do not mix migration versions.
            for(String file:FILES)try(var s=db.prepareStatement("SELECT sha256 FROM stage4_schema_migration WHERE version=?")){s.setString(1,file);try(var r=s.executeQuery()){if(r.next()&&!r.getString(1).equals(hashFile(sqlRoot.resolve(file))))throw new IllegalStateException("Migration checksum changed: "+file);}}
            var applied=new ArrayList<Map<String,Object>>();
            for(String file:FILES){boolean done;try(var s=db.prepareStatement("SELECT COUNT(*) FROM stage4_schema_migration WHERE version=?")){s.setString(1,file);try(var r=s.executeQuery()){r.next();done=r.getInt(1)>0;}}
                String sha=hashFile(sqlRoot.resolve(file));
                if(!done){for(String sql:statements(Files.readString(sqlRoot.resolve(file)))){if(Pattern.compile("(?is)^(DROP|TRUNCATE|USE|CREATE DATABASE)\\b").matcher(sql).find())throw new IllegalStateException("Non-additive SQL rejected");try(var s=db.createStatement()){s.execute(sql);}}
                    try(var s=db.prepareStatement("INSERT INTO stage4_schema_migration VALUES(?,?,?,NOW())")){s.setString(1,file);s.setString(2,sha);s.setString(3,env("STAGE4_OPERATOR"));s.executeUpdate();}
                }
                applied.add(Map.of("file",file,"sha256",sha,"result",done?"already_applied":"applied"));
            }
            validate(db);var result=audit(db);result.put("migrations",applied);result.put("rehearsal",rehearsal);save(rehearsal?"rehearsal-migrations":"migrations",result);System.out.println(json(result));
        }finally{try(var s=db.prepareStatement("SELECT RELEASE_LOCK(?)")){s.setString(1,lock);s.execute();}}
    }
    public static void main(String[] args)throws Exception{
        if(args.length==1&&args[0].equals("self-test")){var rows=statements("-- comment;\nSELECT 'a;''b'; /* ; */ SELECT 'x\\\'y';");if(rows.size()!=2||!rows.get(0).equals("SELECT 'a;''b'"))throw new AssertionError("SQL splitter");System.out.println("SQL quote/comment splitter passed");return;}
        output=Path.of(env("STAGE4_EVIDENCE_DIR"));String expected=env("STAGE4_EXPECTED_DATABASE");id(expected);if(List.of("mysql","sys","information_schema","performance_schema").contains(expected))throw new IllegalArgumentException("System database rejected");
        try(var db=DriverManager.getConnection(env("STAGE4_JDBC_URL"),env("STAGE4_DB_USER"),env("STAGE4_DB_PASSWORD"))){
            if(!expected.equals(db.getCatalog()))throw new IllegalStateException("Connected database differs from explicitly expected database");
            switch(args[0]){
                case "audit" -> {var result=audit(db);save("audit",result);System.out.println(json(result));}
                case "apply" -> apply(db,false);
                case "verify-smoke" -> verifySmoke(db);
                case "rehearse" -> {
                    String original=db.getCatalog(),target="stage4_deploy_rehearsal_"+System.currentTimeMillis();
                    try(var s=db.createStatement()){s.execute("CREATE DATABASE "+id(target)+" CHARACTER SET utf8mb4");for(String table:List.of("knowledge_attach","knowledge_fragment","chat_session","lawyer_case","lawyer_case_file","agent_info","sys_notice"))s.execute("CREATE TABLE "+id(target)+"."+id(table)+" LIKE "+id(original)+"."+id(table));s.executeUpdate("INSERT INTO "+id(target)+".agent_info SELECT * FROM "+id(original)+".agent_info WHERE agent_name IN ('合同审查官','文书起草助手','法律意见书助手')");}
                    // Only original structure and agent configuration go into rehearsal. Nothing is copied back.
                    save("rehearsal-target",Map.of("database",target,"source",original,"retained",true,"businessDataCopied",false,"result","started"));
                    db.setCatalog(target);apply(db,true);apply(db,true);
                    for(String raw:List.of(FILES.get(0),FILES.get(FILES.size()-1)))for(int repeat=0;repeat<2;repeat++)for(String sql:statements(Files.readString(sqlRoot.resolve(raw))))try(var s=db.createStatement()){s.execute(sql);}
                    validate(db);db.setCatalog(original);
                    save("rehearsal-target",Map.of("database",target,"source",original,"retained",true,"businessDataCopied",false,"result","passed","runnerRepeats",2,"rawCitationRepeats",2,"rawPersonalNoticeRepeats",2));
                }
                default -> throw new IllegalArgumentException("Expected audit, rehearse or apply");
            }
        }catch(SQLException e){System.err.println("Database operation failed, SQLState="+e.getSQLState()+", code="+e.getErrorCode()+"; credentials omitted");System.exit(1);}
    }
}
