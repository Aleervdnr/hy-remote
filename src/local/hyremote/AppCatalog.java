package local.hyremote;

import android.content.*;
import android.content.pm.*;
import org.json.*;
import java.text.Collator;
import java.util.*;

final class AppCatalog {
    static final class Entry {
        final String pkg,label;
        final ComponentName component;
        Entry(ResolveInfo info,PackageManager pm){pkg=info.activityInfo.packageName;component=new ComponentName(pkg,info.activityInfo.name);CharSequence name=info.loadLabel(pm);label=name==null?pkg:name.toString();}
    }
    static LinkedHashMap<String,Entry> installed(Context context){
        PackageManager pm=context.getPackageManager();Map<String,Entry> found=new HashMap<>();
        // Priorizar la entrada de TV si una app ofrece ambas interfaces.
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for(ResolveInfo info:pm.queryIntentActivities(intent,0)){
                ActivityInfo activity=info.activityInfo;
                if(activity==null||!activity.exported||!activity.enabled||!activity.applicationInfo.enabled)continue;
                if(activity.permission!=null&&context.checkSelfPermission(activity.permission)!=PackageManager.PERMISSION_GRANTED)continue;
                if(!found.containsKey(activity.packageName))found.put(activity.packageName,new Entry(info,pm));
            }
        }
        ArrayList<Entry> entries=new ArrayList<>(found.values());Collator collator=Collator.getInstance(new Locale("es"));
        Collections.sort(entries,(a,b)->{int order=collator.compare(a.label,b.label);return order!=0?order:a.pkg.compareTo(b.pkg);});
        LinkedHashMap<String,Entry> result=new LinkedHashMap<>();for(Entry entry:entries)result.put(entry.pkg,entry);return result;
    }
    static JSONArray favorites(Context context,Map<String,Entry> apps)throws JSONException{
        SharedPreferences pref=context.getSharedPreferences("remote",Context.MODE_PRIVATE);
        // Conservar los accesos de la versión anterior si esas apps están instaladas.
        String saved=pref.getString("favorites",null);
        JSONArray source=saved==null?new JSONArray(Arrays.asList("com.android.mgstv","com.google.android.youtube.tv","com.netflix.mediaclient","io.privado.android")):new JSONArray(saved);
        JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();for(int i=0;i<source.length();i++){String pkg=source.optString(i);if(apps.containsKey(pkg)&&seen.add(pkg))result.put(pkg);}return result;
    }
    static JSONObject list(Context context)throws JSONException{
        Map<String,Entry> apps=installed(context);JSONArray array=new JSONArray();for(Entry app:apps.values())array.put(new JSONObject().put("package",app.pkg).put("label",app.label));
        return new JSONObject().put("apps",array).put("favorites",favorites(context,apps));
    }
    static JSONObject save(Context context,JSONObject data)throws JSONException{
        JSONArray values=data.optJSONArray("favorites");if(values==null||values.length()>24)return new JSONObject().put("error","Elegí hasta 24 accesos directos.");
        Map<String,Entry> apps=installed(context);Set<String> seen=new HashSet<>();JSONArray saved=new JSONArray();
        for(int i=0;i<values.length();i++){Object value=values.get(i);if(!(value instanceof String)||!apps.containsKey((String)value))return new JSONObject().put("error","Una app ya no está disponible. Actualizá la lista.");if(seen.add((String)value))saved.put(value);}
        if(!context.getSharedPreferences("remote",Context.MODE_PRIVATE).edit().putString("favorites",saved.toString()).commit())return new JSONObject().put("error","No se pudieron guardar los accesos.");
        return new JSONObject().put("ok",true).put("favorites",saved);
    }
    static String shellQuote(String value){return "'"+value.replace("'","'\"'\"'")+"'";}
}
