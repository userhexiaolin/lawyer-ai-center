import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/** Guarded additive extensions; compile with DossierMigration.java. No business data backfill/deletion. */
public class DossierExtensionMigration {
    static final Map<String,Set<String>> EXTENSIONS=Map.of(
        "2026-10-21-bank-review.sql",Set.of("lawyer_case_bank_run","lawyer_case_bank_export","lawyer_case_bank_page","lawyer_case_bank_row","lawyer_case_bank_change","lawyer_case_bank_duplicate"),
        "2026-10-22-dossier-claims.sql",Set.of("lawyer_case_source_anchor","lawyer_case_claim","lawyer_case_claim_source","lawyer_case_claim_suggestion","lawyer_case_review_change","lawyer_case_ai_run","lawyer_case_ai_chunk","lawyer_case_ai_budget","lawyer_case_ai_attempt"),
        "2026-10-22-dossier-events.sql",Set.of("lawyer_case_event","lawyer_case_event_source","lawyer_case_event_precedence","lawyer_case_timeline_revision"));
    static final Set<String> ALLOWED=EXTENSIONS.keySet();
    public static void main(String[] args){try{run(args);}catch(Exception e){System.err.println("Extension migration refused/failed: "+e.getClass().getSimpleName()+"; "+(e instanceof IllegalArgumentException||e instanceof IllegalStateException?e.getMessage():"check schema/backup/permissions; credentials not logged"));System.exit(1);}}
    static void run(String[] args)throws Exception{
        if(args.length<3||!Set.of("audit","migrate").contains(args[0])||!ALLOWED.contains(args[2]))throw new IllegalArgumentException("Usage: audit|migrate <workspace> <allowlisted-sql> [--resume]");
        String database=DossierMigration.env("DOSSIER_EXPECTED_DATABASE");if(!database.matches("[A-Za-z0-9_-]+")||Set.of("mysql","sys","information_schema","performance_schema").contains(database))throw new IllegalArgumentException("Invalid database");
        Path file=Path.of(args[1]).toAbsolutePath().resolve("ruoyi-ai/docs/script/sql/update").resolve(args[2]);String hash=DossierMigration.hash(file),sql=Files.readString(file).replaceAll("(?m)--.*$","");
        var definitions=new LinkedHashMap<String,String>();for(String statement:sql.split(";")){if(statement.isBlank())continue;var table=Pattern.compile("(?s)^\\s*CREATE TABLE IF NOT EXISTS (lawyer_case_[a-z_]+) \\((.*)\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\\s*$").matcher(statement);if(!table.matches()||!EXTENSIONS.get(args[2]).contains(table.group(1))||definitions.containsKey(table.group(1)))throw new IllegalStateException("Only reviewed additive extension tables are allowed");definitions.put(table.group(1),table.group(2));}
        if(!definitions.keySet().equals(EXTENSIONS.get(args[2])))throw new IllegalStateException("Extension table manifest differs");
        try(var c=DriverManager.getConnection(DossierMigration.env("DOSSIER_JDBC_URL"),DossierMigration.env("DOSSIER_DB_USER"),DossierMigration.env("DOSSIER_DB_PASSWORD"))){
            if(!database.equals(c.getCatalog()))throw new IllegalArgumentException("Connected catalog differs from target");DossierMigration.validate(c);long before=DossierMigration.count(c,"lawyer_case_file");boolean registered=false;
            if(DossierMigration.table(c,"stage4_schema_migration"))try(var s=c.prepareStatement("SELECT sha256 FROM stage4_schema_migration WHERE version=?")){s.setString(1,args[2]);try(var r=s.executeQuery()){if(r.next()){if(!hash.equals(r.getString(1)))throw new IllegalStateException("Registered checksum changed");registered=true;}}}
            if(args[0].equals("audit")){System.out.println("{\"database\":\""+database+"\",\"legacyCount\":"+before+",\"registered\":"+registered+"}");return;}
            DossierMigration.backup(database);String operator=DossierMigration.env("DOSSIER_OPERATOR");
            try(var lock=c.prepareStatement("SELECT GET_LOCK(?,5)")){lock.setString(1,"dossier_schema_"+database);try(var r=lock.executeQuery()){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Migration lock unavailable");}}
            try{
                if(!registered&&!Arrays.asList(args).contains("--resume"))for(String table:definitions.keySet())if(DossierMigration.table(c,table))throw new IllegalStateException("Unregistered extension exists; inspect before explicit --resume");
                if(!registered)try(var s=c.createStatement()){for(String statement:sql.split(";"))if(!statement.isBlank())s.execute(statement);}
                for(var entry:definitions.entrySet()){
                    if(!DossierMigration.table(c,entry.getKey()))throw new IllegalStateException("Extension table missing");
                    var columns=Pattern.compile("(?:^|[,\\n])\\s*(\\w+)\\s+(BIGINT|VARCHAR|CHAR|INT|TEXT|MEDIUMTEXT|DECIMAL|TINYINT|DATETIME)\\b",Pattern.CASE_INSENSITIVE).matcher(entry.getValue());
                    while(columns.find())try(var r=c.getMetaData().getColumns(c.getCatalog(),null,entry.getKey(),columns.group(1))){
                        if(!r.next())throw new IllegalStateException("Extension column missing: "+entry.getKey()+"."+columns.group(1));
                        if(!r.getString("TYPE_NAME").toUpperCase(Locale.ROOT).startsWith(columns.group(2).toUpperCase(Locale.ROOT)))throw new IllegalStateException("Extension column type differs: "+entry.getKey()+"."+columns.group(1));
                        if(columns.group(2).equalsIgnoreCase("DECIMAL")&&(r.getInt("COLUMN_SIZE")!=20||r.getInt("DECIMAL_DIGITS")!=2))throw new IllegalStateException("Money requires DECIMAL(20,2): "+entry.getKey()+"."+columns.group(1));
                    }
                }
                if(DossierMigration.count(c,"lawyer_case_file")!=before)throw new IllegalStateException("Legacy association count changed");
                if(!registered){try(var s=c.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS stage4_schema_migration(version VARCHAR(200) PRIMARY KEY,sha256 CHAR(64) NOT NULL,operator VARCHAR(200) NOT NULL,applied_at DATETIME NOT NULL)");}try(var s=c.prepareStatement("INSERT INTO stage4_schema_migration VALUES(?,?,?,UTC_TIMESTAMP())")){s.setString(1,args[2]);s.setString(2,hash);s.setString(3,operator);s.executeUpdate();}}
                System.out.println("{\"result\":\""+(registered?"already_applied":"applied")+"\",\"legacyCountUnchanged\":true,\"sha256\":\""+hash+"\"}");
            }finally{try(var s=c.prepareStatement("SELECT RELEASE_LOCK(?)")){s.setString(1,"dossier_schema_"+database);s.execute();}}
        }
    }
}
