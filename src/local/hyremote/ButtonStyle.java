package local.hyremote;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;
import android.widget.LinearLayout;

final class ButtonStyle {
    static void apply(Button button){
        button.setFocusable(true);button.setFocusableInTouchMode(false);
        float density=button.getResources().getDisplayMetrics().density;
        int[][] states={{-android.R.attr.state_enabled},{android.R.attr.state_pressed},{android.R.attr.state_focused},{android.R.attr.state_hovered},{}};
        int accent=Color.rgb(197,242,119);
        int[] backgrounds={Color.rgb(30,35,32),accent,Color.rgb(49,67,53),Color.rgb(49,67,53),Color.rgb(37,46,40)};
        int[] borders={Color.rgb(53,62,56),Color.WHITE,accent,accent,Color.rgb(85,103,91)};
        StateListDrawable drawable=new StateListDrawable();
        for(int i=0;i<states.length;i++){
            GradientDrawable shape=new GradientDrawable();shape.setColor(backgrounds[i]);shape.setCornerRadius(12*density);shape.setStroke(Math.round((i>0&&i<4?3:1)*density),borders[i]);drawable.addState(states[i],shape);
        }
        button.setBackgroundTintList(null);button.setBackground(drawable);
        button.setTextColor(new ColorStateList(states,new int[]{Color.rgb(140,153,145),Color.rgb(21,32,14),Color.WHITE,Color.WHITE,Color.WHITE}));
        button.setMinHeight(Math.round(48*density));button.setPadding(Math.round(14*density),Math.round(8*density),Math.round(14*density),Math.round(8*density));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.bottomMargin=Math.round(8*density);button.setLayoutParams(layout);
    }
}
