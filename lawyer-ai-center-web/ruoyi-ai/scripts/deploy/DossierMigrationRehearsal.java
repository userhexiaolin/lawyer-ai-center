import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

/** Synthetic-only rehearsal. Never migrates the configured business catalog. */
public class DossierMigrationRehearsal {
    static String setting(String config,String field){var m=Pattern.compile("(?m)^          "+field+": (.+)$").matcher(config);if(!m.find())throw new IllegalStateException("Local datasource setting missing");return m.group(1).strip();}
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    record Result(int code,String output){}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]).toAbsolutePath(),evidence=root.resolve("docs/product/o001/evidence/task-85");
        String config=Files.readString(root.resolve("ruoyi-ai/ruoyi-admin/src/main/resources/application-dev.yml"));
        String configured=setting(config,"url"),user=setting(config,"username"),password=setting(config,"password");
        require(configured.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost)(?::[0-9]+)?/.*"),"Only local test instance allowed");
        String db="codex_dossier85_migration_"+UUID.randomUUID().toString().replace("-","").substring(0,10);
        try(var c=DriverManager.getConnection(configured,user,password);var s=c.createStatement()){s.execute("CREATE DATABASE `"+db+"` CHARACTER SET utf8mb4");}
        int query=configured.indexOf('?');if(query<0)query=configured.length();int slash=configured.lastIndexOf('/',query);
        String url=configured.substring(0,slash+1)+db+configured.substring(query);
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
            s.execute("CREATE TABLE lawyer_case_file(tenant_id VARCHAR(20),user_id BIGINT,case_id BIGINT,oss_id BIGINT,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id,user_id,case_id,oss_id))");
            s.execute("CREATE TABLE sys_oss(oss_id BIGINT PRIMARY KEY)");
            s.execute("INSERT INTO lawyer_case_file VALUES('000000',101,11,9001,'2026-10-20 12:00:00')");
        }
        Path backup=evidence.resolve("migration-synthetic-backup.sql"),receipt=evidence.resolve("migration-synthetic-backup.properties");
        Files.writeString(backup,"-- SYNTHETIC ONLY; not a business backup\nCREATE TABLE lawyer_case_file(tenant_id VARCHAR(20),user_id BIGINT,case_id BIGINT,oss_id BIGINT,created_at DATETIME,PRIMARY KEY(tenant_id,user_id,case_id,oss_id));\nINSERT INTO lawyer_case_file VALUES('000000',101,11,9001,'2026-10-20 12:00:00');\nCREATE TABLE sys_oss(oss_id BIGINT PRIMARY KEY);\n");
        var properties=new Properties();properties.setProperty("database",db);properties.setProperty("file",backup.toString());properties.setProperty("sha256",DossierMigration.hash(backup));
        try(var writer=Files.newBufferedWriter(receipt,StandardCharsets.UTF_8)){properties.store(writer,"SYNTHETIC ONLY");}
        var env=new HashMap<String,String>();env.put("DOSSIER_JDBC_URL",url);env.put("DOSSIER_DB_USER",user);env.put("DOSSIER_DB_PASSWORD",password);env.put("DOSSIER_EXPECTED_DATABASE",db);env.put("DOSSIER_OPERATOR","synthetic-rehearsal");env.put("DOSSIER_BACKUP_RECEIPT",receipt.toString());
        var records=new ArrayList<String>();
        var bad=new HashMap<>(env);bad.put("DOSSIER_EXPECTED_DATABASE",db+"_wrong");
        var wrong=invoke(root,bad,"audit");require(wrong.code()!=0,"Target mismatch must fail");records.add("target_mismatch_rejected");
        bad=new HashMap<>(env);bad.put("DOSSIER_BACKUP_RECEIPT",receipt.resolveSibling("absent-backup-receipt").toString());
        require(invoke(root,bad,"migrate").code()!=0,"Missing backup must fail");
        try(var c=DriverManager.getConnection(url,user,password)){require(!DossierMigration.column(c,"id"),"Refused attempt must not mutate schema");}records.add("missing_backup_rejected_without_ddl");
        var applied=invoke(root,env,"migrate");require(applied.code()==0&&applied.output().contains("\"applied\""),"First migration must apply: "+applied.output());records.add("first_apply");
        var repeated=invoke(root,env,"migrate");require(repeated.code()==0&&repeated.output().contains("already_applied"),"Repeat must be no-op");records.add("repeat_noop");
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
            try(var r=s.executeQuery("SELECT * FROM lawyer_case_file")){require(r.next()&&r.getLong("oss_id")==9001&&r.getLong("user_id")==101&&r.getString("created_at").startsWith("2026-10-20 12:00:00")&&!r.next(),"Original association must be intact");}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM stage4_schema_migration")){r.next();require(r.getInt(1)==1,"Single ledger registration required");}
            s.executeUpdate("UPDATE stage4_schema_migration SET sha256=REPEAT('0',64)");
        }records.add("legacy_row_and_single_ledger_preserved");
        require(invoke(root,env,"migrate").code()!=0,"Checksum drift must fail");records.add("checksum_drift_rejected");
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.executeUpdate("DELETE FROM stage4_schema_migration");}
        require(invoke(root,env,"migrate").code()!=0,"Unregistered schema needs explicit resume");records.add("unregistered_schema_rejected");
        require(invoke(root,env,"migrate","--resume").code()==0,"Explicit resume must validate and register");records.add("explicit_resume_validated");
        var audit=invoke(root,env,"audit");require(audit.code()==0&&audit.output().contains("\"migrationRegistered\":true"),"Audit receipt required");records.add("audit_registered");
        Files.writeString(evidence.resolve("migration-rehearsal.json"),"{\"database\":\""+db+"\",\"syntheticOnly\":true,\"businessDatabaseModified\":false,\"checks\":[\""+String.join("\",\"",records)+"\"],\"result\":\"PASS\"}\n");
        System.out.println("Migration rehearsal PASS: "+records.size()+" checks; synthetic database="+db+"; business database untouched");
    }
    static Result invoke(Path root,Map<String,String> env,String action,String... extra)throws Exception{
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),"DossierMigration",action,root.toString()));command.addAll(List.of(extra));
        var builder=new ProcessBuilder(command).redirectErrorStream(true);builder.environment().putAll(env);var process=builder.start();
        if(!process.waitFor(45,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Migration subprocess timeout");}
        return new Result(process.exitValue(),new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
    }
}
