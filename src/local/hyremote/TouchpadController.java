package local.hyremote;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import org.json.*;
import java.util.concurrent.*;

/** Cursor local; los clics y desplazamientos se envían como gestos de Android. */
final class TouchpadController {
    final Context context; final Handler main; final AdbClient adb; final WindowManager windows;
    final ProjectionController projection;
    View cursor; float x=-1,y=-1; int width,height;
    final Runnable hide=()->removeCursor();
    TouchpadController(Context context,Handler main,AdbClient adb,ProjectionController projection){this.context=context;this.main=main;this.adb=adb;this.projection=projection;windows=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);}
    void dimensions(){DisplayMetrics m=new DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);width=m.widthPixels;height=m.heightPixels;if(x<0){x=width/2f;y=height/2f;}x=clamp(x,0,width-1);y=clamp(y,0,height-1);}
    static float clamp(float n,float min,float max){return Math.max(min,Math.min(max,n));}
    double number(JSONObject data,String name,double fallback,double limit)throws JSONException{
        Object raw=data.opt(name);if(raw==null)return fallback;
        if(!(raw instanceof Number))throw new JSONException("Número inválido");double value=((Number)raw).doubleValue();
        if(Double.isNaN(value)||Double.isInfinite(value)||Math.abs(value)>limit)throw new JSONException("Número fuera de rango");return value;
    }
    synchronized JSONObject act(JSONObject data)throws Exception{
        String action=data.optString("action");
        if(action.equals("hide")){onMain(()->removeCursor());return new JSONObject().put("ok",true);}
        if(!java.util.Arrays.asList("show","move","center","click","longclick","scroll").contains(action))return new JSONObject().put("error","Gesto desconocido.");
        double dx=0,dy=0,sensitivity=1;
        try{if(action.equals("move")){dx=number(data,"dx",0,500);dy=number(data,"dy",0,500);sensitivity=number(data,"sensitivity",1,2);if(sensitivity<0.5)throw new JSONException("Sensibilidad inválida");}if(action.equals("scroll")){dy=number(data,"dy",0,1);if(dy==0)throw new JSONException("Desplazamiento vacío");}}
        catch(JSONException invalid){return new JSONObject().put("error","Gesto inválido. Volvé a intentarlo.");}
        if(!Settings.canDrawOverlays(context))return new JSONObject().put("error","Abrí HY Remote en el proyector y habilitá «Permitir cursor en pantalla».");
        dimensions();
        if(action.equals("center")){x=width/2f;y=height/2f;}
        if(action.equals("move")){float gain=width/500f*(float)sensitivity;x=clamp(x+(float)dx*gain,0,width-1);y=clamp(y+(float)dy*gain,0,height-1);}
        onMain(()->showCursor());
        int px=Math.round(x),py=Math.round(y);String result="";
        if(action.equals("click")&&projection.vendorMenu()){
            JSONObject clicked=projection.clickAt(px,py);if(clicked.has("error"))return clicked;
        }else if(action.equals("click")||action.equals("longclick"))result=mouse(action,px,py,0);
        if(action.equals("scroll")){
            if(projection.xuper()){
                // Xuper recorta el catálogo con AXIS_VSCROLL. Un gesto táctil
                // desplaza la misma lista sin provocar ese fallo de composición.
                int sx=Math.round(clamp(px,16,width-17));
                int sy=Math.round(dy<0?clamp(py,height*.50f,height*.78f):clamp(py,height*.22f,height*.50f));
                int ey=Math.round(sy+(float)dy*height*.30f);
                String swipe=adb.shell("input touchscreen swipe "+sx+" "+sy+" "+sx+" "+ey+" 300 && echo HY_SWIPE_OK");
                result=swipe.contains("HY_SWIPE_OK")&&!swipe.contains("Exception")&&!swipe.contains("Error")?"":"ERROR";
            }else result=mouse("scroll",px,py,(float)dy*3);
        }
        if(result.contains("ERROR")||result.contains("Error")||result.contains("Exception"))return new JSONObject().put("error","Android no pudo ejecutar el gesto.");
        return new JSONObject().put("ok",true).put("x",Math.round(x)).put("y",Math.round(y)).put("width",width).put("height",height);
    }
    String mouse(String action,int px,int py,float amount)throws Exception{
        String result=adb.shell("CLASSPATH="+AppCatalog.shellQuote(context.getApplicationInfo().sourceDir)+" app_process / local.hyremote.PointerInjector "+action+" "+px+" "+py+" "+amount);
        return result.contains("HY_POINTER_OK")?"":"ERROR";
    }
    void onMain(Runnable operation)throws Exception{FutureTask<Void> task=new FutureTask<>(()->{operation.run();return null;});main.post(task);task.get(3,TimeUnit.SECONDS);}
    void showCursor(){
        int size=Math.round(30*context.getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.alpha=.75f;p.x=Math.round(x)-size/2;p.y=Math.round(y)-size/2;
        if(cursor==null){cursor=new CursorView(context);windows.addView(cursor,p);}else windows.updateViewLayout(cursor,p);
        main.removeCallbacks(hide);main.postDelayed(hide,15000);
    }
    void removeCursor(){main.removeCallbacks(hide);if(cursor!=null){try{windows.removeView(cursor);}catch(IllegalArgumentException ignored){}cursor=null;}}
    void close(){main.post(()->removeCursor());}
    static final class CursorView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        CursorView(Context context){super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){float cx=getWidth()/2f,cy=getHeight()/2f,r=getWidth()*.29f;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7);paint.setColor(Color.BLACK);canvas.drawCircle(cx,cy,r,paint);paint.setStrokeWidth(3);paint.setColor(Color.rgb(197,242,119));canvas.drawCircle(cx,cy,r,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(cx,cy,3,paint);}
    }
}
