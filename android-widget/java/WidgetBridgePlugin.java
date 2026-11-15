package com.ehit.spa;

import android.content.SharedPreferences;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONObject;

/**
 * Lets the web app and the home-screen widget share ONE login session and the latest totals.
 * The web app's Supabase client stores its session here (instead of only in the WebView),
 * so the widget can refresh the session itself without logging the app out.
 */
@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {

    private SharedPreferences sp() {
        return WidgetLogic.p(getContext());
    }

    private String slot(String key) {
        return key.endsWith("-auth-token") ? "sess" : "kv:" + key;
    }

    @PluginMethod
    public void getItem(PluginCall call) {
        JSObject r = new JSObject();
        String k = call.getString("key");
        String v = k == null ? null : sp().getString(slot(k), null);
        r.put("value", v == null ? JSONObject.NULL : v);
        call.resolve(r);
    }

    @PluginMethod
    public void setItem(PluginCall call) {
        String k = call.getString("key");
        String v = call.getString("value");
        if (k != null && v != null) sp().edit().putString(slot(k), v).apply();
        call.resolve();
    }

    @PluginMethod
    public void removeItem(PluginCall call) {
        String k = call.getString("key");
        if (k != null) {
            SharedPreferences.Editor e = sp().edit().remove(slot(k));
            if (k.endsWith("-auth-token")) e.remove("totals").putBoolean("show", false);
            e.apply();
            if (k.endsWith("-auth-token")) WidgetLogic.updateAll(getContext());
        }
        call.resolve();
    }

    /** url, apikey, lang ("en"/"am"), cal ("gc"/"et"), show (may this user see totals?) */
    @PluginMethod
    public void saveSettings(PluginCall call) {
        boolean wasShown = sp().getBoolean("show", false);
        boolean show = Boolean.TRUE.equals(call.getBoolean("show", false));
        SharedPreferences.Editor e = sp().edit();
        String url = call.getString("url");
        String key = call.getString("apikey");
        if (url != null) e.putString("url", url);
        if (key != null) e.putString("apikey", key);
        e.putString("lang", call.getString("lang", "en"));
        e.putString("cal", call.getString("cal", "gc"));
        e.putBoolean("show", show);
        if (!show) e.remove("totals");
        e.apply();
        WidgetLogic.schedule(getContext());
        WidgetLogic.updateAll(getContext());
        if (show && !wasShown) WidgetLogic.fetchAsync(getContext(), null);
        call.resolve();
    }

    /** net, inc, ex, cash, tr, hand, date (report date, yyyy-mm-dd) */
    @PluginMethod
    public void saveTotals(PluginCall call) {
        JSObject d = call.getData();
        try {
            d.put("at", System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        sp().edit().putString("totals", d.toString()).putBoolean("off", false).apply();
        WidgetLogic.updateAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void clearAll(PluginCall call) {
        sp().edit().remove("totals").putBoolean("show", false).apply();
        WidgetLogic.updateAll(getContext());
        call.resolve();
    }
}
