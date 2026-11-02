import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;

/** Task85 additive migration runner. JDBC credentials are supplied in environment, never argv/output. */
public class DossierMigration {
    static final String VERSION="2026-10-21-dossier-materials.sql";
    static final List<String> TABLES=List.of("lawyer_case_upload","lawyer_case_file_version","lawyer_case_file_page","lawyer_case_parse_job","lawyer_case_parse_log");
    static String env(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalArgumentException("Required setting: "+key);return value;}
    static String hash(Path file)throws Exception{try(var input=Files.newInputStream(file)){var md=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)md.update(buffer,0,n);return HexFormat.of().formatHex(md.digest());}}
    static boolean table(Connection c,String name)throws Exception{try(var r=c.getMetaData().getTables(c.getCatalog(),null,name,new String[]{"TABLE"})){return r.next();}}
    static boolean column(Connection c,String name)throws Exception{try(var r=c.getMetaData().getColumns(c.getCatalog(),null,"lawyer_case_file",name)){return r.next();}}
    static boolean index(Connection c,String name)throws Exception{try(var r=c.getMetaData().getIndexInfo(c.getCatalog(),null,"lawyer_case_file",false,false)){while(r.next())if(name.equals(r.getString("INDEX_NAME")))return true;return false;}}
    static long count(Connection c,String name)throws Exception{try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+name)){r.next();return r.getLong(1);}}
    static void backup(String database)throws Exception{
        var receipt=new Properties();try(var input=Files.newBufferedReader(Path.of(env("DOSSIER_BACKUP_RECEIPT")),java.nio.charset.StandardCharsets.UTF_8)){receipt.load(input);}
        Path file=Path.of(receipt.getProperty("file","")).toAbsolutePath();
        if(!database.equals(receipt.getProperty("database"))||!Files.isRegularFile(file)||Files.size(file)==0||!hash(file).equals(receipt.getProperty("sha256")))throw new IllegalStateException("Backup receipt does not match target database/file/hash");
    }
    static void validate(Connection c)throws Exception{
        for(String t:TABLES)if(!table(c,t))throw new IllegalStateException("Missing table "+t);
        for(String name:List.of("id","display_name","category","current_version_id"))if(!column(c,name))throw new IllegalStateException("Missing material column "+name);
        for(String name:List.of("id","current_version_id"))try(var r=c.getMetaData().getColumns(c.getCatalog(),null,"lawyer_case_file",name)){if(!r.next()||r.getInt("DATA_TYPE")!=Types.BIGINT)throw new IllegalStateException("Material ID column must use BIGINT: "+name);}
        try(var s=c.createStatement()){s.executeQuery("SELECT file_version_id,page_index,extract_status,ocr_status,preview_status,text_content,text_hash,version FROM lawyer_case_file_page LIMIT 0").close();s.executeQuery("SELECT requested_pages,lease_token,lease_until,active_version_id FROM lawyer_case_parse_job LIMIT 0").close();}
    }
    public static void main(String[] args){try{run(args);}catch(Exception e){System.err.println("Dossier migration refused/failed; type="+e.getClass().getSimpleName()+"; "+(e instanceof IllegalArgumentException||e instanceof IllegalStateException?e.getMessage():"inspect target schema, backup and permissions; credentials not logged"));System.exit(1);}}
    static void run(String[] args)throws Exception{
        if(args.length<1||args[0].equals("--help")){System.out.println("DossierMigration audit|migrate <workspace-root> [--resume]; requires DOSSIER_JDBC_URL/DB_USER/DB_PASSWORD/EXPECTED_DATABASE; migrate also requires OPERATOR and verified BACKUP_RECEIPT.");return;}
        if(args.length<2||!Set.of("audit","migrate").contains(args[0]))throw new IllegalArgumentException("Invalid command");
        Path sql=Path.of(args[1]).toAbsolutePath().resolve("ruoyi-ai/docs/script/sql/update/"+VERSION);
        String expected=env("DOSSIER_EXPECTED_DATABASE");if(!expected.matches("[A-Za-z0-9_-]+")||Set.of("mysql","sys","information_schema","performance_schema").contains(expected))throw new IllegalArgumentException("Invalid target database");
        try(var c=DriverManager.getConnection(env("DOSSIER_JDBC_URL"),env("DOSSIER_DB_USER"),env("DOSSIER_DB_PASSWORD"))){
            if(!expected.equals(c.getCatalog()))throw new IllegalArgumentException("Connected catalog differs from explicitly named target");
            if(!table(c,"lawyer_case_file")||!table(c,"sys_oss"))throw new IllegalStateException("Existing case archive/storage schema required");
            long before=count(c,"lawyer_case_file");
            if(args[0].equals("audit")){System.out.println("{\"database\":\""+expected+"\",\"caseFileCount\":"+before+",\"materialColumnsPresent\":"+column(c,"id")+",\"migrationRegistered\":"+registered(c,hash(sql))+"}");return;}
            backup(expected);String operator=env("DOSSIER_OPERATOR"),checksum=hash(sql);boolean resume=Arrays.asList(args).contains("--resume");
            try(var lock=c.prepareStatement("SELECT GET_LOCK(?,5)")){lock.setString(1,"dossier_schema_"+expected);try(var r=lock.executeQuery()){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Migration lock unavailable");}}
            try{
                if(registered(c,checksum)){validate(c);System.out.println("{\"result\":\"already_applied\",\"caseFileCount\":"+before+"}");return;}
                if(!resume&&TABLES.stream().anyMatch(t->{try{return table(c,t);}catch(Exception e){throw new RuntimeException(e);}}))throw new IllegalStateException("Unregistered dossier tables exist; inspect them before an explicit --resume");
                try(var s=c.createStatement()){
                    for(var field:Map.of("id","BIGINT NULL","display_name","VARCHAR(255) NULL","category","VARCHAR(40) NOT NULL DEFAULT 'unclassified'","current_version_id","BIGINT NULL").entrySet())if(!column(c,field.getKey()))s.execute("ALTER TABLE lawyer_case_file ADD COLUMN "+field.getKey()+" "+field.getValue());
                    if(!index(c,"uk_case_file_id"))s.execute("ALTER TABLE lawyer_case_file ADD UNIQUE KEY uk_case_file_id(id)");
                    String text=Files.readString(sql).replaceAll("(?m)--.*$","");for(String statement:text.split(";"))if(!statement.isBlank()&&!statement.stripLeading().startsWith("ALTER TABLE")){
                        if(TABLES.stream().noneMatch(t->statement.stripLeading().startsWith("CREATE TABLE IF NOT EXISTS "+t+" (")))throw new IllegalStateException("Unexpected non-additive migration statement");
                        s.execute(statement);
                    }
                    validate(c);if(count(c,"lawyer_case_file")!=before)throw new IllegalStateException("Original association count changed");
                    s.execute("CREATE TABLE IF NOT EXISTS stage4_schema_migration(version VARCHAR(200) PRIMARY KEY,sha256 CHAR(64) NOT NULL,operator VARCHAR(200) NOT NULL,applied_at DATETIME NOT NULL)");
                }
                try(var s=c.prepareStatement("INSERT INTO stage4_schema_migration VALUES(?,?,?,UTC_TIMESTAMP())")){s.setString(1,VERSION);s.setString(2,checksum);s.setString(3,operator);s.executeUpdate();}
                System.out.println("{\"result\":\"applied\",\"sha256\":\""+checksum+"\",\"caseFileCountBefore\":"+before+",\"caseFileCountAfter\":"+count(c,"lawyer_case_file")+"}");
            }finally{try(var s=c.prepareStatement("SELECT RELEASE_LOCK(?)")){s.setString(1,"dossier_schema_"+expected);s.execute();}}
        }
    }
    static boolean registered(Connection c,String checksum)throws Exception{
        if(!table(c,"stage4_schema_migration"))return false;
        try(var s=c.prepareStatement("SELECT sha256 FROM stage4_schema_migration WHERE version=?")){s.setString(1,VERSION);try(var r=s.executeQuery()){if(!r.next())return false;if(!checksum.equals(r.getString(1)))throw new IllegalStateException("Registered migration checksum differs; add a new migration instead of rewriting it");return true;}}
    }
}
