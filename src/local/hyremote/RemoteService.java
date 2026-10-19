package local.hyremote;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public class RemoteService extends Service {
    volatile boolean running;
    ServerSocket listener;
    ExecutorService clients;
    final Handler main=new Handler(Looper.getMainLooper());
    AdbClient adb;
    TouchpadController touchpad;
    ProjectionController projection;
    static final Map<String,Integer> KEYS=new HashMap<>();
    static{String[] names={"up","down","left","right","ok","home","back","play","volup","voldown","mute","enter","delete","menu"};int[] values={19,20,21,22,23,3,4,85,24,25,164,66,67,82};for(int i=0;i<names.length;i++)KEYS.put(names[i],values[i]);}
    static synchronized String token(Context context){
        android.content.SharedPreferences preferences=context.getSharedPreferences("remote",MODE_PRIVATE);String value=preferences.getString("key",null);
        if(value==null){byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);StringBuilder b=new StringBuilder();for(byte item:bytes)b.append(String.format(Locale.ROOT,"%02x",item&255));value=b.toString();preferences.edit().putString("key",value).commit();}return value;
    }
    @Override public void onCreate(){
        super.onCreate();NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("remote","Control desde el celular",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,new Notification.Builder(this,"remote").setContentTitle("HY Remote activo").setContentText("Control local desde tu celular").setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open).build());
        running=true;adb=new AdbClient(this);new Thread(()->{while(running){if(!AdbClient.ready)try{adb.shell("echo HY_READY");}catch(Exception ignored){}try{Thread.sleep(5000);}catch(InterruptedException stopped){return;}}},"hy-adb-init").start();
        projection=new ProjectionController(this,adb);
        touchpad=new TouchpadController(this,main,adb,projection);
        token(this);running=true;clients=new ThreadPoolExecutor(3,3,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(12));
        new Thread(()->{try{listener=new ServerSocket();listener.setReuseAddress(true);listener.bind(new InetSocketAddress(8767));while(running){Socket socket=listener.accept();try{clients.execute(()->serve(socket));}catch(RejectedExecutionException ex){socket.close();}}}catch(IOException ignored){}},"hy-http").start();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_STICKY;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){running=false;if(touchpad!=null)touchpad.close();if(projection!=null)projection.close();try{if(listener!=null)listener.close();}catch(IOException ignored){}if(clients!=null)clients.shutdownNow();super.onDestroy();}
    String line(InputStream input)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();int c;while((c=input.read())!=-1){if(c=='\n')break;if(c!='\r')bytes.write(c);if(bytes.size()>8192)throw new IOException("Header too large");}return c==-1&&bytes.size()==0?null:bytes.toString("UTF-8");}
    void serve(Socket socket){try(Socket close=socket){socket.setSoTimeout(6000);InputStream input=new BufferedInputStream(socket.getInputStream());String first=line(input);if(first==null)return;String[] parts=first.split(" ");if(parts.length<2)return;
        URI uri=URI.create(parts[1]);String path=uri.getPath();Map<String,String> headers=new HashMap<>();String h;int total=0;while((h=line(input))!=null&&!h.isEmpty()){total+=h.length();if(total>16000)throw new IOException("Headers too large");int colon=h.indexOf(':');if(colon>0)headers.put(h.substring(0,colon).trim().toLowerCase(Locale.ROOT),h.substring(colon+1).trim());}
        String key=token(this);boolean query=false;String q=uri.getRawQuery();if(q!=null)for(String field:q.split("&")){if(field.equals("token="+key))query=true;}
        boolean cookie=false;for(String field:headers.getOrDefault("cookie","").split(";"))if(field.trim().equals("hy_session="+key))cookie=true;
        if(!(query||cookie)){reply(socket,403,"application/json",json("error","Abrí el enlace completo mostrado en HY Remote."),null);return;}
        if("GET".equals(parts[0])&&"/".equals(path)){ByteArrayOutputStream body=new ByteArrayOutputStream();try(InputStream asset=getAssets().open("index.html")){byte[] buf=new byte[8192];int n;while((n=asset.read(buf))!=-1)body.write(buf,0,n);}reply(socket,200,"text/html",body.toByteArray(),key);return;}
        if("GET".equals(parts[0])&&"/api/status".equals(path)){JSONObject result=new JSONObject();result.put("connected",AdbClient.ready);result.put("target","HY300 Pro · directo");result.put("adbReady",true);reply(socket,200,"application/json",result.toString().getBytes(StandardCharsets.UTF_8),key);return;}
        if("GET".equals(parts[0])&&"/api/apps".equals(path)){reply(socket,200,"application/json",AppCatalog.list(this).toString().getBytes(StandardCharsets.UTF_8),null);return;}
        if(!"POST".equals(parts[0])||!"1".equals(headers.get("x-hy-control"))||!headers.getOrDefault("content-type","").startsWith("application/json")){reply(socket,403,"application/json",json("error","Solicitud no autorizada."),null);return;}
        int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<1||length>4096){reply(socket,400,"application/json",json("error","Solicitud inválida."),null);return;}
        byte[] body=new byte[length];new DataInputStream(input).readFully(body);JSONObject data=new JSONObject(new String(body,StandardCharsets.UTF_8));
        JSONObject result;try{result=act(path,data);}catch(Exception ex){result=new JSONObject().put("error","No se pudo conectar al control interno. Abrí HY Remote y aceptá la autorización de depuración si aparece.");}reply(socket,result.has("error")?400:200,"application/json",result.toString().getBytes(StandardCharsets.UTF_8),null);
    }catch(Exception ignored){/* No almacenar texto, claves ni datos de las solicitudes. */}}
    JSONObject act(String path,JSONObject data)throws Exception{
        if("/api/projection".equals(path))return projection.act(data);
        if("/api/pointer".equals(path))return touchpad.act(data);
        if("/api/favorites".equals(path))return AppCatalog.save(this,data);
        if("/api/app".equals(path)){
            AppCatalog.Entry app=AppCatalog.installed(this).get(data.optString("app"));if(app==null)return new JSONObject().put("error","Esta app no está disponible. Actualizá la lista.");
            String result=adb.shell("am start -n "+AppCatalog.shellQuote(app.component.flattenToShortString()));if(result.contains("Error")||result.contains("Exception"))return new JSONObject().put("error","No se pudo abrir la aplicación.");
            return new JSONObject().put("ok",true).put("app",app.label);
        }
        if("/api/key".equals(path)){
            Integer key=KEYS.get(data.optString("key"));if(key==null)return new JSONObject().put("error","Botón desconocido.");String result=adb.shell("input keyevent "+key);
            return result.contains("Exception")||result.contains("Error")?new JSONObject().put("error","Android rechazó el botón."):new JSONObject().put("ok",true);
        }
        if("/api/text".equals(path)){
            String value=data.optString("text");if(value.isEmpty()||value.length()>300)return new JSONObject().put("error","Escribí entre 1 y 300 caracteres.");
            FutureTask<Boolean> write=new FutureTask<>(()->RemoteIme.instance!=null&&RemoteIme.instance.write(value));main.post(write);
            if(write.get(3,TimeUnit.SECONDS))return new JSONObject().put("ok",true);
            return new JSONObject().put("error","Seleccioná un campo de texto que use el teclado de Android. Las apps con teclado propio requieren sus flechas.");
        }
        return new JSONObject().put("error","Acción desconocida.");
    }
    byte[] json(String k,String value){try{return new JSONObject().put(k,value).toString().getBytes(StandardCharsets.UTF_8);}catch(Exception ignored){return new byte[0];}}
    void reply(Socket socket,int status,String type,byte[] body,String session)throws IOException{
        String headers="HTTP/1.1 "+status+" "+(status==200?"OK":"Error")+"\r\nContent-Type: "+type+"; charset=utf-8\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'\r\n";
        if(session!=null){
            // Chrome puede descartar cookies de sesión al cerrar su proceso en Android.
            // Renovar también desde /api/status migra las pestañas ya abiertas.
            java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",Locale.US);date.setTimeZone(TimeZone.getTimeZone("GMT"));
            String expires=date.format(new Date(System.currentTimeMillis()+31536000000L));
            headers+="Set-Cookie: hy_session="+session+"; Max-Age=31536000; Expires="+expires+"; HttpOnly; SameSite=Strict; Path=/\r\n";
        }
        OutputStream out=socket.getOutputStream();out.write((headers+"\r\n").getBytes(StandardCharsets.UTF_8));out.write(body);out.flush();
    }
}
