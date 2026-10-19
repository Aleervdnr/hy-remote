package local.hyremote;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Perfiles locales de las ocho coordenadas, compartidos por todos los celulares. */
final class ProjectionProfiles {
    final SharedPreferences preferences;
    ProjectionProfiles(Context context){preferences=context.getSharedPreferences("projection_profiles",Context.MODE_PRIVATE);}
    JSONArray load()throws JSONException{return new JSONArray(preferences.getString("profiles","[]"));}
    JSONObject list()throws JSONException{return new JSONObject().put("ok",true).put("profiles",load());}
    JSONObject save(String name,JSONObject corners)throws Exception{
        name=name.trim();if(name.isEmpty()||name.length()>32||name.matches(".*[\\p{Cntrl}].*"))return new JSONObject().put("error","Usá un nombre de entre 1 y 32 caracteres.");
        JSONArray items=load();int index=-1;String id=UUID.randomUUID().toString();
        for(int i=0;i<items.length();i++)if(items.getJSONObject(i).getString("name").equalsIgnoreCase(name)){index=i;id=items.getJSONObject(i).getString("id");break;}
        if(index<0&&items.length()>=12)return new JSONObject().put("error","Podés guardar hasta 12 configuraciones.");
        JSONObject entry=new JSONObject().put("id",id).put("name",name).put("corners",corners);
        if(index<0)items.put(entry);else items.put(index,entry);
        if(!preferences.edit().putString("profiles",items.toString()).commit())return new JSONObject().put("error","No se pudo guardar la configuración.");
        return new JSONObject().put("ok",true).put("profiles",items).put("savedId",id);
    }
    JSONObject delete(String id)throws Exception{
        JSONArray items=load(),kept=new JSONArray();boolean found=false;
        for(int i=0;i<items.length();i++){JSONObject entry=items.getJSONObject(i);if(entry.getString("id").equals(id))found=true;else kept.put(entry);}
        if(!found)return new JSONObject().put("error","Esa configuración ya no está guardada.");
        if(!preferences.edit().putString("profiles",kept.toString()).commit())return new JSONObject().put("error","No se pudo eliminar la configuración.");
        return new JSONObject().put("ok",true).put("profiles",kept);
    }
}
