package local.hyremote;

import android.app.UiAutomation;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import java.io.*;
import android.graphics.Rect;

/** Adaptador del menú original HY300Pro. Se ejecuta como shell, sin escribir propiedades. */
public final class ProjectionBridge {
    static final String[] CORNERS={"lt","rt","rb","lb"};
    static final String PREFIX="persist.display.keystone_";
    static UiAutomation ui; static HandlerThread thread;
    static Method getProp;
    static Object inputManager;static Method inject;
    static volatile long lastRequest;
    static void serve(String name,int owner)throws Exception{
        if(!name.matches("hy-projection-[a-f0-9]{32}")||owner<10000)throw new IllegalArgumentException();
        try(LocalServerSocket server=new LocalServerSocket(name)){
            lastRequest=SystemClock.elapsedRealtime();
            Timer expiry=new Timer(true);expiry.scheduleAtFixedRate(new TimerTask(){public void run(){if(SystemClock.elapsedRealtime()-lastRequest>45000){try{server.close();}catch(IOException ignored){}System.exit(0);}}},5000,5000);
            try{connect();boolean active=true;while(active){
                try(LocalSocket socket=server.accept()){
                    if(socket.getPeerCredentials().getUid()!=owner)continue;
                    lastRequest=SystemClock.elapsedRealtime();socket.setSoTimeout(4000);
                    JSONObject response;
                    try{StringBuilder line=new StringBuilder();InputStream input=socket.getInputStream();int ch;while((ch=input.read())!=-1&&ch!='\n'){if(line.length()>=2048)throw new IllegalArgumentException("Solicitud inválida.");line.append((char)ch);}
                        JSONObject data=new JSONObject(line.toString());String action=data.optString("action");
                        if(action.equals("close")){response=new JSONObject().put("ok",true);active=false;}
                        else if(action.equals("read"))response=result(read());
                        else if(action.equals("open")){open();response=result(read());}
                        else if(action.equals("click"))response=click(data.getInt("x"),data.getInt("y"));
                        else if(action.equals("move")){int corner=Arrays.asList(CORNERS).indexOf(data.optString("corner"));if(corner<0)throw new IllegalArgumentException("Esquina inválida.");move(corner,data.getInt("x"),data.getInt("y"));response=result(read());}
                        else throw new IllegalArgumentException("Acción inválida.");
                    }catch(Throwable error){response=error(error);}
                    socket.getOutputStream().write((response.toString()+"\n").getBytes("UTF-8"));lastRequest=SystemClock.elapsedRealtime();
                }
            }}finally{expiry.cancel();}
        }
    }
    static JSONObject error(Throwable error)throws Exception{Throwable cause=error instanceof InvocationTargetException?error.getCause():error;return new JSONObject().put("error",cause.getMessage()==null?"No se pudo ajustar la proyección.":cause.getMessage());}
    static AccessibilityNodeInfo clickableAt(AccessibilityNodeInfo node,int x,int y){
        Rect bounds=new Rect();node.getBoundsInScreen(bounds);if(!node.isVisibleToUser()||!bounds.contains(x,y))return null;
        for(int i=node.getChildCount()-1;i>=0;i--){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){AccessibilityNodeInfo found=clickableAt(child,x,y);child.recycle();if(found!=null)return found;}}
        return node.isEnabled()&&node.isClickable()?AccessibilityNodeInfo.obtain(node):null;
    }
    static JSONObject click(int x,int y)throws Exception{
        AccessibilityNodeInfo r=ui.getRootInActiveWindow();
        if(r==null)return new JSONObject().put("error","No se pudo leer el menú. Volvé a intentarlo.");
        if(!"com.htc.htcsettings".contentEquals(r.getPackageName())){r.recycle();return new JSONObject().put("error","Cambió la pantalla del proyector. Volvé a intentarlo.");}
        AccessibilityNodeInfo target=clickableAt(r,x,y);r.recycle();
        if(target==null)return new JSONObject().put("error","Apuntá al centro de un botón del menú.");
        boolean ok=target.performAction(AccessibilityNodeInfo.ACTION_CLICK);target.recycle();
        return ok?new JSONObject().put("ok",true):new JSONObject().put("error","El menú no aceptó ese botón.");
    }
    static String property(String key)throws Exception{return (String)getProp.invoke(null,key,"");}
    static void supported()throws Exception{
        getProp=Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class);
        if(!property("ro.product.model").equals("HY300Pro")||!property("ro.build.display.id").equals("UI_Veng.projector.20241211.154238"))throw new IllegalStateException("Esta versión del proyector todavía no está verificada.");
    }
    static int[] read()throws Exception{
        int[] v=new int[8];for(int i=0;i<4;i++)for(int j=0;j<2;j++){v[2*i+j]=Integer.parseInt(property(PREFIX+CORNERS[i]+(j==0?"x":"y")));if(v[2*i+j]<0||v[2*i+j]>450)throw new IllegalStateException("La corrección actual está fuera del rango admitido.");}return v;
    }
    static JSONObject result(int[] v)throws Exception{
        JSONObject corners=new JSONObject();for(int i=0;i<4;i++)corners.put(CORNERS[i],new JSONObject().put("x",v[2*i]).put("y",v[2*i+1]));
        return new JSONObject().put("ok",true).put("corners",corners);
    }
    static void connect()throws Exception{
        thread=new HandlerThread("hy-projection-ui");thread.start();
        Class<?> connection=Class.forName("android.app.UiAutomationConnection"),iface=Class.forName("android.app.IUiAutomationConnection");
        ui=(UiAutomation)UiAutomation.class.getConstructor(Looper.class,iface).newInstance(thread.getLooper(),connection.getConstructor().newInstance());
        UiAutomation.class.getMethod("connect",int.class).invoke(ui,1);
    }
    static AccessibilityNodeInfo root()throws Exception{
        for(int i=0;i<15;i++){AccessibilityNodeInfo r=ui.getRootInActiveWindow();if(r!=null&&"com.htc.htcsettings".contentEquals(r.getPackageName())&&!r.findAccessibilityNodeInfosByViewId("com.htc.htcsettings:id/check_lt").isEmpty())return r;if(r!=null)r.recycle();Thread.sleep(100);}
        throw new IllegalStateException("Abrí la corrección manual en el proyector antes de ajustar.");
    }
    static void select(int corner)throws Exception{
        AccessibilityNodeInfo r=root();List<AccessibilityNodeInfo> found=r.findAccessibilityNodeInfosByViewId("com.htc.htcsettings:id/check_"+CORNERS[corner]);
        boolean ok=!found.isEmpty()&&(found.get(0).isChecked()||found.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));for(AccessibilityNodeInfo n:found)n.recycle();r.recycle();
        if(!ok)throw new IllegalStateException("No se pudo seleccionar la esquina.");Thread.sleep(40);
        r=root();found=r.findAccessibilityNodeInfosByViewId("com.htc.htcsettings:id/check_"+CORNERS[corner]);ok=!found.isEmpty()&&found.get(0).isChecked();for(AccessibilityNodeInfo n:found)n.recycle();r.recycle();if(!ok)throw new IllegalStateException("No se confirmó la esquina seleccionada.");
    }
    static AccessibilityNodeInfo waitNode(String id)throws Exception{
        for(int i=0;i<20;i++){AccessibilityNodeInfo r=ui.getRootInActiveWindow();if(r!=null){List<AccessibilityNodeInfo> nodes=r.findAccessibilityNodeInfosByViewId("com.htc.htcsettings:id/"+id);r.recycle();if(!nodes.isEmpty()){AccessibilityNodeInfo first=nodes.remove(0);for(AccessibilityNodeInfo n:nodes)n.recycle();return first;}}Thread.sleep(100);}throw new IllegalStateException("No se encontró el menú de corrección manual.");
    }
    static void open()throws Exception{
        AccessibilityNodeInfo projection=waitNode("home_projection_rl");boolean entered=projection.performAction(AccessibilityNodeInfo.ACTION_CLICK);projection.recycle();if(!entered)throw new IllegalStateException("No se pudo abrir el menú de proyección.");
        AccessibilityNodeInfo item=waitNode("main_rv");List<AccessibilityNodeInfo> tiles=item.findAccessibilityNodeInfosByViewId("com.htc.htcsettings:id/rl_item");item.recycle();
        if(tiles.size()!=3)throw new IllegalStateException("El menú de proyección no coincide con esta versión.");
        boolean clicked=tiles.get(2).performAction(AccessibilityNodeInfo.ACTION_CLICK);for(AccessibilityNodeInfo n:tiles)n.recycle();if(!clicked)throw new IllegalStateException("No se pudo abrir la corrección trapezoidal.");
        AccessibilityNodeInfo manual=waitNode("correction_rl");clicked=manual.isEnabled()&&manual.performAction(AccessibilityNodeInfo.ACTION_CLICK);manual.recycle();if(!clicked)throw new IllegalStateException("Elegí el modo manual de corrección en el proyector.");
        AccessibilityNodeInfo r=root();r.recycle();
    }
    static void keys(int code,int count)throws Exception{
        if(count==0)return;
        if(inputManager==null){Class<?> type=Class.forName("android.hardware.input.InputManager");inputManager=type.getMethod("getInstance").invoke(null);inject=type.getMethod("injectInputEvent",InputEvent.class,int.class);}
        // El menú verificado acelera las repeticiones 1,2,...,8 pasos. Cortar cada
        // pulsación antes de superar el destino, y conservar el paso simple al afinar.
        while(count>0){long t=SystemClock.uptimeMillis();int repeat=0,step=1;
            try{while(count>=step){KeyEvent down=new KeyEvent(t,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,code,repeat,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_DPAD);
                if(!Boolean.TRUE.equals(inject.invoke(inputManager,down,2)))throw new IllegalStateException("El proyector rechazó el ajuste.");count-=step;repeat++;step=Math.min(8,step+1);}}
            finally{KeyEvent up=new KeyEvent(t,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_DPAD);if(!Boolean.TRUE.equals(inject.invoke(inputManager,up,2)))throw new IllegalStateException("El proyector rechazó el ajuste.");}
        }
    }
    static void move(int corner,int x,int y)throws Exception{
        int[] before=read();int dx=x-before[2*corner],dy=y-before[2*corner+1];
        if(x<0||x>450||y<0||y>450||Math.abs(dx)>40||Math.abs(dy)>40)throw new IllegalArgumentException("Ajustá la esquina en movimientos más cortos.");
        select(corner);
        boolean right=corner==1||corner==2,bottom=corner>=2;
        keys((dx>0)^right?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT,Math.abs(dx));
        keys((dy>0)^bottom?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_UP,Math.abs(dy));
        Thread.sleep(60);int[] after=read();
        if(after[2*corner]!=x||after[2*corner+1]!=y)throw new IllegalStateException("El proyector limitó el movimiento. Actualizá los valores.");
        for(int i=0;i<8;i++)if(i/2!=corner&&before[i]!=after[i])throw new IllegalStateException("Cambió otra esquina. Actualizá antes de continuar.");
    }
    public static void main(String[] args){
        try{supported();if(args.length==3&&args[0].equals("serve")){serve(args[1],Integer.parseInt(args[2]));}
            else if(args.length==1&&args[0].equals("read")){System.out.println(result(read()));}
            else if(args.length==1&&args[0].equals("open")){connect();open();System.out.println(result(read()));}
            else if(args.length==4&&args[0].equals("move")){int corner=Arrays.asList(CORNERS).indexOf(args[1]);if(corner<0)throw new IllegalArgumentException("Esquina inválida.");int x=Integer.parseInt(args[2]),y=Integer.parseInt(args[3]);connect();move(corner,x,y);System.out.println(result(read()));}
            else throw new IllegalArgumentException("Acción inválida.");
        }catch(Throwable error){try{Throwable cause=error instanceof InvocationTargetException?error.getCause():error;System.out.println(new JSONObject().put("error",cause.getMessage()==null?"No se pudo ajustar la proyección.":cause.getMessage()));}catch(Exception ignored){}}
        finally{if(ui!=null)try{UiAutomation.class.getMethod("disconnect").invoke(ui);}catch(Exception ignored){}if(thread!=null)thread.quitSafely();}System.exit(0);
    }
}
