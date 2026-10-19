package local.hyremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import io.nayuki.qrcodegen.QrCode;

/** Genera el QR en el proyector. Nunca envía el enlace a un servicio externo. */
public final class QrView extends View {
    private final Paint paint=new Paint();
    private QrCode qr;
    private String address="";
    public QrView(Context context){super(context);setContentDescription("QR para conectar el celular al control de este proyector");}
    public void setAddress(String value){
        if(address.equals(value))return;
        address=value;qr=value.startsWith("http://")?QrCode.encodeText(value,QrCode.Ecc.MEDIUM):null;invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(Color.WHITE);if(qr==null)return;
        int modules=qr.size+8,scale=Math.min(getWidth(),getHeight())/modules;
        if(scale<1)return;
        int left=(getWidth()-modules*scale)/2+4*scale,top=(getHeight()-modules*scale)/2+4*scale;
        paint.setColor(Color.BLACK);paint.setAntiAlias(false);
        for(int y=0;y<qr.size;y++)for(int x=0;x<qr.size;x++)if(qr.getModule(x,y))canvas.drawRect(left+x*scale,top+y*scale,left+(x+1)*scale,top+(y+1)*scale,paint);
    }
}
