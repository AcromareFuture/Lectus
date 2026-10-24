import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads an offline H2 snapshot. Values remain in a private host-side file. */
public class ExportLegacyMembers {
 public static void main(String[] args) throws Exception {
  var encoder=Base64.getEncoder();
  try(var c=DriverManager.getConnection(System.getenv("LEGACY_JDBC"),"lectus",System.getenv("LEGACY_PASSWORD"));
      var out=Files.newBufferedWriter(Path.of(args[0]),StandardCharsets.UTF_8)) {
   c.setReadOnly(true);
   for(String table:List.of("v_member","v_member_phone_identity","v_member_address","v_member_favorite","v_member_point_ledger","v_member_balance_ledger","v_member_signin_log")) {
    try(var rs=c.createStatement().executeQuery("SELECT * FROM "+table)) {
     var meta=rs.getMetaData();int n=meta.getColumnCount(),count=0;
     out.write("TABLE\t"+table);out.newLine();
     for(int i=1;i<=n;i++){if(i>1)out.write("\t");out.write(meta.getColumnName(i).toLowerCase(Locale.ROOT));}out.newLine();
     while(rs.next()){for(int i=1;i<=n;i++){if(i>1)out.write("\t");String value=rs.getString(i);out.write(value==null?"~":encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8)));}out.newLine();count++;}
     System.out.println(table+" rows="+count);
    } catch(SQLException e) { if(!e.getSQLState().equals("42S02"))throw e; }
   }
  }
 }
}
