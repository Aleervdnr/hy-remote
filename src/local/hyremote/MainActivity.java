package local.hyremote;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.widget.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    TextView status,link;
    QrView qr;
    String lastAddress="";
    final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
    final Runnable update=new Runnable(){public void run(){if(status!=null)refresh();ui.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startForegroundService(new Intent(this, RemoteService.class));
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(32,20,32,20);root.setBackgroundColor(Color.rgb(16,20,19));
        TextView title=new TextView(this);title.setText("HY Remote · conectá tu celular");title.setTextSize(25);title.setTextColor(Color.WHITE);title.setPadding(0,0,0,16);root.addView(title);
        LinearLayout columns=new LinearLayout(this);root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pairing=new LinearLayout(this);pairing.setOrientation(1);
        int size=Math.min((int)(getResources().getDisplayMetrics().heightPixels*.53f),(int)(getResources().getDisplayMetrics().widthPixels*.34f));
        columns.addView(pairing,new LinearLayout.LayoutParams(size,-1));
        qr=new QrView(this);pairing.addView(qr,new LinearLayout.LayoutParams(size,size));
        TextView caption=new TextView(this);caption.setText("Escaneá con la cámara del celular.\nAmbos deben usar el mismo Wi-Fi.");caption.setTextSize(14);caption.setTextColor(Color.WHITE);caption.setPadding(0,12,0,8);pairing.addView(caption);
        link=new TextView(this);link.setTextSize(10);link.setTextColor(Color.LTGRAY);pairing.addView(link);
        ScrollView scroll=new ScrollView(this);LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1);right.leftMargin=28;columns.addView(scroll,right);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);scroll.addView(layout);
        status=new TextView(this);status.setTextSize(18);status.setTextColor(Color.rgb(197,242,119));status.setPadding(0,0,0,12);layout.addView(status);
        Button reconnect=new Button(this);reconnect.setText("Comprobar conexión interna");reconnect.setTextColor(Color.WHITE);reconnect.setOnClickListener(v->{reconnect.setEnabled(false);new Thread(()->{try{new AdbClient(this).shell("echo HY_READY");}catch(Exception ignored){}runOnUiThread(()->{reconnect.setEnabled(true);refresh();});}).start();});layout.addView(reconnect);
        Button debug=new Button(this);debug.setText("Ajustes de depuración");debug.setTextColor(Color.WHITE);debug.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(android.content.ActivityNotFoundException ex){startActivity(new Intent(Settings.ACTION_SETTINGS));}});layout.addView(debug);
        Button cursor=new Button(this);cursor.setText("Permitir cursor en pantalla");cursor.setTextColor(Color.WHITE);cursor.setOnClickListener(v->{if(Settings.canDrawOverlays(this)){Toast.makeText(this,"Cursor habilitado. Abrí Touchpad en el celular.",Toast.LENGTH_LONG).show();return;}try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));}catch(android.content.ActivityNotFoundException ex){startActivity(new Intent(Settings.ACTION_SETTINGS));}});layout.addView(cursor);
        Button keyboard=new Button(this);keyboard.setText("Usar teclado del celular");keyboard.setTextColor(Color.WHITE);keyboard.setOnClickListener(v->{keyboard.setEnabled(false);new Thread(()->{try{AdbClient client=new AdbClient(this);String old=client.shell("settings get secure default_input_method").trim();if(!old.startsWith("local.hyremote/")&&old.matches("[A-Za-z0-9_./]+"))getSharedPreferences("remote",MODE_PRIVATE).edit().putString("previousIme",old).commit();client.shell("ime enable local.hyremote/.RemoteIme");client.shell("ime set local.hyremote/.RemoteIme");runOnUiThread(()->Toast.makeText(this,"Teclado del celular activado",Toast.LENGTH_LONG).show());}catch(Exception ignored){runOnUiThread(()->Toast.makeText(this,"Falta conectar el control interno",Toast.LENGTH_LONG).show());}runOnUiThread(()->keyboard.setEnabled(true));}).start();});layout.addView(keyboard);
        Button restore=new Button(this);restore.setText("Restaurar teclado anterior");restore.setTextColor(Color.WHITE);restore.setOnClickListener(v->new Thread(()->{try{String old=getSharedPreferences("remote",MODE_PRIVATE).getString("previousIme","");if(old.matches("[A-Za-z0-9_./]+"))new AdbClient(this).shell("ime set "+old);}catch(Exception ignored){}}).start());layout.addView(restore);
        Button home=new Button(this);home.setText("Volver al inicio del proyector");home.setTextColor(Color.WHITE);home.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_HOME);startActivity(i);});layout.addView(home);
        TextView note=new TextView(this);note.setText("Primera vez: habilitá la depuración y aceptá el aviso de autorización (permitir siempre). Después, escaneá el QR.\nGuardá el enlace como favorito. Es privado y solo funciona con el proyector encendido.");note.setTextSize(13);note.setTextColor(Color.LTGRAY);layout.addView(note);
        EditText test=new EditText(this);test.setHint("Campo de prueba del teclado · no se guarda");test.setTextColor(Color.WHITE);test.setHintTextColor(Color.LTGRAY);test.setSingleLine(true);layout.addView(test);
        for(int i=0;i<layout.getChildCount();i++)if(layout.getChildAt(i) instanceof Button)ButtonStyle.apply((Button)layout.getChildAt(i));
        setContentView(root);reconnect.requestFocus();refresh();
    }
    @Override public void onResume(){super.onResume();ui.removeCallbacks(update);ui.post(update);}
    @Override public void onPause(){ui.removeCallbacks(update);super.onPause();}
    void refresh(){
        String state=AdbClient.ready?"Control interno conectado":"Pendiente: depuración / autorización";
        if(!status.getText().toString().equals(state))status.setText(state);
        String address=address();if(!lastAddress.equals(address)){lastAddress=address;link.setText(address);qr.setAddress(address);qr.setVisibility(address.startsWith("http://")?android.view.View.VISIBLE:android.view.View.INVISIBLE);}
    }
    String address(){
        try{List<NetworkInterface> interfaces=Collections.list(NetworkInterface.getNetworkInterfaces());Collections.sort(interfaces,(a,b)->Boolean.compare(!a.getName().startsWith("wlan"),!b.getName().startsWith("wlan")));for(NetworkInterface i:interfaces){if(!i.isUp()||i.getName().startsWith("tun")||i.getName().startsWith("dummy"))continue;Enumeration<InetAddress> addresses=i.getInetAddresses();while(addresses.hasMoreElements()){InetAddress a=addresses.nextElement();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&a.isSiteLocalAddress())return "http://"+a.getHostAddress()+":8767/?token="+RemoteService.token(this);}}}catch(Exception ignored){}
        return "Esperando conexión Wi-Fi";
    }
}
