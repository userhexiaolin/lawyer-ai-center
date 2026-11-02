import java.nio.file.*;
import java.sql.*;
import cn.hutool.crypto.digest.BCrypt;

/** Local-only synthetic ordinary account. Never changes registration, existing users or roles. */
class Stage4SmokeUser {
    static String env(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+key);return value;}
    public static void main(String[] args)throws Exception{
        String url=env("STAGE4_JDBC_URL"),expected=env("STAGE4_EXPECTED_DATABASE"),name=env("STAGE4_NEW_SMOKE_USERNAME"),password=env("STAGE4_NEW_SMOKE_PASSWORD"),tenant=System.getenv().getOrDefault("STAGE4_TENANT_ID","000000");
        if(!url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/.*")||!name.matches("deploy53_[0-9]{13}")||password.length()<16)throw new IllegalArgumentException("Only explicitly named synthetic local accounts are permitted");
        try(var db=DriverManager.getConnection(url,env("STAGE4_DB_USER"),env("STAGE4_DB_PASSWORD"))){
            if(!expected.equals(db.getCatalog()))throw new IllegalStateException("Database mismatch");
            db.setAutoCommit(false);
            try{
                long role;try(var s=db.prepareStatement("SELECT role_id FROM sys_role WHERE tenant_id=? AND role_key='user' AND status='0' AND del_flag='0'")){s.setString(1,tenant);try(var r=s.executeQuery()){if(!r.next())throw new IllegalStateException("No ordinary user role");role=r.getLong(1);if(role==1||r.next())throw new IllegalStateException("Ambiguous/administrator role rejected");}}
                long user=System.currentTimeMillis()*1000+53;
                try(var s=db.prepareStatement("INSERT INTO sys_user(user_id,tenant_id,user_name,nick_name,user_type,password,status,del_flag,create_time) VALUES(?,?,?,?,'sys_user',?,'0','0',NOW())")){s.setLong(1,user);s.setString(2,tenant);s.setString(3,name);s.setString(4,"部署53合成冒烟");s.setString(5,BCrypt.hashpw(password));s.executeUpdate();}
                try(var s=db.prepareStatement("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)")){s.setLong(1,user);s.setLong(2,role);s.executeUpdate();}
                db.commit();
                System.out.println("Created one synthetic local ordinary user; existing users, roles and registration unchanged.");
            }catch(Exception e){db.rollback();throw e;}
        }catch(SQLException e){System.err.println("Smoke-user provisioning failed, SQLState="+e.getSQLState()+", code="+e.getErrorCode());System.exit(1);}
    }
}
