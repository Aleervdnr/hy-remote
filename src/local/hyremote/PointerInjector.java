package local.hyremote;

import android.os.*;
import android.view.*;
import java.lang.reflect.*;

/** Se ejecuta con ADB local, igual que la herramienta input de Android. */
public final class PointerInjector {
    static Object manager;static Method inject;static int deviceId=0;
    public static void main(String[] args){
        try{
            if(args.length!=4)throw new IllegalArgumentException();
            if(Looper.myLooper()==null)Looper.prepareMainLooper();
            Class<?> type=Class.forName("android.hardware.input.InputManager");manager=type.getMethod("getInstance").invoke(null);inject=type.getMethod("injectInputEvent",InputEvent.class,int.class);
            for(int id:InputDevice.getDeviceIds()){InputDevice device=InputDevice.getDevice(id);if(device!=null&&device.supportsSource(InputDevice.SOURCE_MOUSE)){deviceId=id;break;}}
            float x=Float.parseFloat(args[1]),y=Float.parseFloat(args[2]),amount=Float.parseFloat(args[3]);
            long now=SystemClock.uptimeMillis();event(MotionEvent.ACTION_HOVER_MOVE,x,y,0,0,now);
            if(args[0].equals("scroll"))event(MotionEvent.ACTION_SCROLL,x,y,0,amount,now);
            else if(args[0].equals("click")||args[0].equals("longclick")){
                event(MotionEvent.ACTION_DOWN,x,y,MotionEvent.BUTTON_PRIMARY,0,now);
                if(args[0].equals("longclick"))Thread.sleep(650);
                event(MotionEvent.ACTION_UP,x,y,0,0,now);
            }else throw new IllegalArgumentException();
            System.out.println("HY_POINTER_OK");
        }catch(Throwable error){System.out.println("HY_POINTER_ERROR "+error.getClass().getSimpleName());System.exit(1);}
        System.exit(0);
    }
    static void event(int action,float x,float y,int buttons,float scroll,long down)throws Exception{
        MotionEvent.PointerProperties properties=new MotionEvent.PointerProperties();properties.id=0;properties.toolType=MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=x;coords.y=y;coords.pressure=buttons==0?0:1;coords.size=1;coords.setAxisValue(MotionEvent.AXIS_VSCROLL,scroll);
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{properties},new MotionEvent.PointerCoords[]{coords},0,buttons,1,1,deviceId,0,InputDevice.SOURCE_MOUSE,0);
        try{if(!Boolean.TRUE.equals(inject.invoke(manager,event,2)))throw new IllegalStateException("Gesto rechazado");}finally{event.recycle();}
    }
}
