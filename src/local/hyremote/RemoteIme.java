package local.hyremote;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.*;
import android.graphics.Color;

public class RemoteIme extends InputMethodService {
    static volatile RemoteIme instance;
    @Override public void onCreate(){super.onCreate();instance=this;}
    @Override public void onDestroy(){instance=null;super.onDestroy();}
    @Override public View onCreateInputView(){
        LinearLayout row=new LinearLayout(this);row.setPadding(20,8,20,8);row.setBackgroundColor(Color.rgb(28,34,32));
        TextView title=new TextView(this);title.setText("HY Remote · escribí desde el celular");title.setTextColor(Color.WHITE);title.setTextSize(20);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button hide=new Button(this);hide.setText("Ocultar");hide.setOnClickListener(v->requestHideSelf(0));ButtonStyle.apply(hide);hide.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));row.addView(hide);return row;
    }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    boolean write(String text){InputConnection input=getCurrentInputConnection();return input!=null&&input.commitText(text,1);}
}
