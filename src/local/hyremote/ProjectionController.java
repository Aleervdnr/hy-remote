package local.hyremote;

import android.content.Context;
import org.json.*;
import android.net.*;
import java.io.*;

final class ProjectionController {
    final Context context;final AdbClient adb;
    final ProjectionProfiles profiles;
    final String socketName="hy-projection-"+java.util.UUID.randomUUID().toString().replace("-","");
    ProjectionController(Context context,AdbClient adb){this.context=context;this.adb=adb;profiles=new ProjectionProfiles(context);}
    JSONObject bridge(String args)throws Exception{
        JSONObject data=new JSONObject();String[] words=args.split(" ");data.put("action",words[0]);if(words[0].equals("move"))data.put("corner",words[1]).put("x",Integer.parseInt(words[2])).put("y",Integer.parseInt(words[3]));if(words[0].equals("click"))data.put("x",Integer.parseInt(words[1])).put("y",Integer.parseInt(words[2]));
        LocalSocket socket=connect();
        if(socket==null){
            adb.shell("CLASSPATH="+AppCatalog.shellQuote(context.getApplicationInfo().sourceDir)+" nohup setsid app_process / local.hyremote.ProjectionBridge serve "+socketName+" "+android.os.Process.myUid()+" </dev/null >/dev/null 2>&1 & sleep 0.3");
            for(int i=0;i<25&&socket==null;i++){Thread.sleep(100);socket=connect();}
        }
        if(socket==null)return new JSONObject().put("error","No se pudo iniciar el ajuste de proyección en este firmware.");
        try(LocalSocket connection=socket){connection.setSoTimeout(6000);connection.getOutputStream().write((data.toString()+"\n").getBytes("UTF-8"));String line=new BufferedReader(new InputStreamReader(connection.getInputStream(),"UTF-8")).readLine();if(line==null)throw new IOException("El ajuste no respondió.");return new JSONObject(line);}
    }
    LocalSocket connect(){LocalSocket socket=new LocalSocket();try{socket.connect(new LocalSocketAddress(socketName,LocalSocketAddress.Namespace.ABSTRACT));return socket;}catch(IOException error){try{socket.close();}catch(IOException ignored){}return null;}}
    synchronized void close(){try{LocalSocket socket=connect();if(socket!=null)try(LocalSocket connection=socket){connection.getOutputStream().write("{\"action\":\"close\"}\n".getBytes("UTF-8"));}}catch(Exception ignored){}}
    boolean vendorMenu()throws Exception{return adb.shell("dumpsys activity activities | grep -m 1 mResumedActivity").contains(" com.htc.htcsettings/");}
    boolean xuper()throws Exception{return adb.shell("dumpsys activity activities | grep -m 1 mResumedActivity").contains(" com.android.mgstv/");}
    synchronized JSONObject clickAt(int x,int y)throws Exception{return bridge("click "+x+" "+y);}
    synchronized JSONObject act(JSONObject data)throws Exception{
        String action=data.optString("action");
        if(action.equals("profiles"))return profiles.list();
        if(action.equals("save-profile")){
            Object raw=data.opt("name");if(!(raw instanceof String))return new JSONObject().put("error","Escribí un nombre para la configuración.");
            JSONObject current=bridge("read");if(current.has("error"))return current;
            return profiles.save((String)raw,current.getJSONObject("corners"));
        }
        if(action.equals("delete-profile"))return profiles.delete(data.optString("id"));
        if(action.equals("close")){close();return new JSONObject().put("ok",true);}
        if(action.equals("read"))return bridge("read");
        if(action.equals("open")){
            JSONObject current=bridge("read");if(current.has("error"))return current;
            // Si el ajuste manual ya está abierto, conservar su estado y evitar
            // reconstruir el menú en cada cambio de perfil.
            if(adb.shell("dumpsys activity activities | grep -m 1 mResumedActivity").contains(" com.htc.htcsettings/com.htc.activity.CorrectionActivity "))return current;
            // La raíz debe ser HomeActivity. Usar ProjectionActivity como raíz deja
            // al firmware reutilizando el menú equivocado cuando se vuelve a entrar.
            String result=adb.shell("am start -W -f 0x14000000 -n com.htc.htcsettings/com.htc.activity.HomeActivity");
            if(result.contains("Error")||result.contains("Exception"))return new JSONObject().put("error","No se pudo abrir la corrección manual.");
            return bridge("open");
        }
        if(action.equals("move")){
            String corner=data.optString("corner");if(!java.util.Arrays.asList("lt","rt","rb","lb").contains(corner))return new JSONObject().put("error","Esquina inválida.");
            for(String key:new String[]{"x","y"}){Object raw=data.opt(key);if(!(raw instanceof Number))return new JSONObject().put("error","Valor inválido.");double v=((Number)raw).doubleValue();if(!Double.isFinite(v)||v!=Math.rint(v)||v<0||v>450)return new JSONObject().put("error","Valor fuera de rango.");}
            return bridge("move "+corner+" "+data.getInt("x")+" "+data.getInt("y"));
        }
        return new JSONObject().put("error","Acción de proyección desconocida.");
    }
}
