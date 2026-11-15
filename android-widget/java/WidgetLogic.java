package com.ehit.spa;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.RemoteViews;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class WidgetLogic {
    static final String ACTION_TICK = "com.ehit.spa.WIDGET_TICK";
    static final String ACTION_REFRESH = "com.ehit.spa.WIDGET_REFRESH";
    /** Refresh interval: 2 minutes. Android may delay it a little while the phone is idle. */
    static final long PERIOD_MS = 120000L;
    static final String PREFS = "ehit_widget";

    private static final String[] EM_EN = {"Meskerem", "Tikimt", "Hidar", "Tahsas", "Tir", "Yekatit", "Megabit", "Miazia", "Ginbot", "Sene", "Hamle", "Nehase", "Pagume"};
    private static final String[] EM_AM = {"መስከረም", "ጥቅምት", "ኅዳር", "ታኅሣሥ", "ጥር", "የካቲት", "መጋቢት", "ሚያዝያ", "ግንቦት", "ሰኔ", "ሐምሌ", "ነሐሴ", "ጳጉሜን"};
    private static final String[] GM_EN = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] GM_AM = {"ጃንዩ", "ፌብሩ", "ማርች", "ኤፕሪ", "ሜይ", "ጁን", "ጁላይ", "ኦገስ", "ሴፕቴ", "ኦክቶ", "ኖቬም", "ዲሴም"};
    private static final String[] WD_EN = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] WD_AM = {"እሑድ", "ሰኞ", "ማክሰኞ", "ረቡዕ", "ሐሙስ", "አርብ", "ቅዳሜ"};

    private WidgetLogic() {}

    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* ---------------- scheduling ---------------- */

    private static PendingIntent tickIntent(Context c) {
        Intent i = new Intent(c, EhitWidgetProvider.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = SystemClock.elapsedRealtime() + PERIOD_MS;
        if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, tickIntent(c));
        else am.set(AlarmManager.ELAPSED_REALTIME, at, tickIntent(c));
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickIntent(c));
    }

    /* ---------------- fetching ---------------- */

    static void fetchAsync(final Context ctx, final BroadcastReceiver.PendingResult pr) {
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    fetch(c);
                } catch (Throwable ignored) {
                } finally {
                    try {
                        updateAll(c);
                    } catch (Throwable ignored) {
                    }
                    if (pr != null) pr.finish();
                }
            }
        }).start();
    }

    private static final class HttpErr extends IOException {
        final int code;
        HttpErr(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    private static String http(String url, String body, String key, String bearer) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        try {
            h.setRequestMethod("POST");
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/json");
            h.setRequestProperty("apikey", key);
            h.setRequestProperty("Authorization", "Bearer " + bearer);
            OutputStream os = h.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            int code = h.getResponseCode();
            if (code < 200 || code >= 300) throw new HttpErr(code);
            InputStream in = h.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } finally {
            h.disconnect();
        }
    }

    /** Refreshes the shared session. The result is written back so the app picks it up too. */
    private static String refresh(SharedPreferences sp, String url, String key, JSONObject s) {
        try {
            String rt = s.optString("refresh_token", "");
            if (rt.length() == 0) return null;
            JSONObject req = new JSONObject();
            req.put("refresh_token", rt);
            String out = http(url + "/auth/v1/token?grant_type=refresh_token", req.toString(), key, key);
            JSONObject r = new JSONObject(out);
            long now = System.currentTimeMillis() / 1000;
            s.put("access_token", r.getString("access_token"));
            s.put("refresh_token", r.optString("refresh_token", rt));
            long ei = r.optLong("expires_in", 3600);
            s.put("expires_in", ei);
            s.put("expires_at", r.optLong("expires_at", now + ei));
            if (r.has("token_type")) s.put("token_type", r.getString("token_type"));
            sp.edit().putString("sess", s.toString()).apply();
            return s.getString("access_token");
        } catch (Exception e) {
            return null;
        }
    }

    static void fetch(Context c) {
        SharedPreferences sp = p(c);
        String url = sp.getString("url", null), key = sp.getString("apikey", null), sess = sp.getString("sess", null);
        if (url == null || key == null || sess == null || !sp.getBoolean("show", false)) return;
        try {
            JSONObject s = new JSONObject(sess);
            String access = s.optString("access_token", "");
            long left = s.optLong("expires_at", 0) - System.currentTimeMillis() / 1000;
            if (access.length() == 0 || left < 90) {
                access = refresh(sp, url, key, s);
                if (access == null) throw new IOException("session");
            }
            String out;
            try {
                out = http(url + "/rest/v1/rpc/today_bundle", "{}", key, access);
            } catch (HttpErr e) {
                if (e.code != 401) throw e;
                access = refresh(sp, url, key, s);
                if (access == null) throw e;
                out = http(url + "/rest/v1/rpc/today_bundle", "{}", key, access);
            }
            JSONObject rep = new JSONObject(out).getJSONObject("report");
            double inc = 0, cash = 0, tr = 0, ex = 0;
            JSONArray tx = rep.optJSONArray("transactions");
            if (tx != null) for (int i = 0; i < tx.length(); i++) {
                JSONObject o = tx.getJSONObject(i);
                if (!o.isNull("deleted_at") || "rejected".equals(o.optString("status"))) continue;
                inc += o.optDouble("total", 0);
                cash += o.optDouble("cash_amount", 0);
                tr += o.optDouble("transfer_amount", 0);
            }
            JSONArray ea = rep.optJSONArray("expenses");
            if (ea != null) for (int i = 0; i < ea.length(); i++) {
                JSONObject o = ea.getJSONObject(i);
                if (!o.isNull("deleted_at") || "rejected".equals(o.optString("status"))) continue;
                ex += o.optDouble("amount", 0);
            }
            JSONObject t = new JSONObject();
            t.put("net", inc - ex);
            t.put("inc", inc);
            t.put("ex", ex);
            t.put("cash", cash);
            t.put("tr", tr);
            t.put("hand", cash - ex);
            t.put("date", rep.optString("report_date", ""));
            t.put("at", System.currentTimeMillis());
            sp.edit().putString("totals", t.toString()).putBoolean("off", false).apply();
        } catch (Exception e) {
            sp.edit().putBoolean("off", true).apply();
        }
    }

    /* ---------------- drawing ---------------- */

    private static String fmt(double v) {
        return new DecimalFormat("#,##0.##").format(v);
    }

    private static CharSequence money(double v, String cur) {
        String num = fmt(v);
        SpannableString sp = new SpannableString(num + " " + cur);
        sp.setSpan(new RelativeSizeSpan(0.68f), num.length(), sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    private static long jdnG(int y, int m, int d) {
        int a = (14 - m) / 12, yy = y + 4800 - a, mm = m + 12 * a - 3;
        return d + (153 * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
    }

    /** Date label such as "Sun, 5 Hidar 2019" (Ethiopian) or "Sun, 15 Nov 2026". */
    private static String dateLabel(String ymd, boolean am, boolean et) {
        try {
            String[] q = ymd.split("-");
            int y = Integer.parseInt(q[0]), m = Integer.parseInt(q[1]), d = Integer.parseInt(q[2]);
            long j = jdnG(y, m, d);
            String wd = (am ? WD_AM : WD_EN)[(int) ((j + 1) % 7)];
            if (!et) return wd + ", " + d + " " + (am ? GM_AM : GM_EN)[m - 1] + " " + y;
            long r = (j - 1723856) % 1461;
            long n = (r % 365) + 365 * (r / 1460);
            long ey = 4 * ((j - 1723856) / 1461) + r / 365 - r / 1460;
            int em = (int) (n / 30), ed = (int) (n % 30) + 1;
            return wd + ", " + ed + " " + (am ? EM_AM : EM_EN)[em] + " " + ey;
        } catch (Exception e) {
            return ymd;
        }
    }

    private static String tr(boolean am, String en) {
        if (!am) return en;
        switch (en) {
            case "Net profit": return "የተጣራ ትርፍ";
            case "Income": return "ገቢ";
            case "Expenses": return "ወጪዎች";
            case "Cash": return "ጥሬ ገንዘብ";
            case "Transfer": return "ባንክ ዝውውር";
            case "Cash in hand": return "በእጅ ያለ ገንዘብ";
            case "Live": return "ቀጥታ";
            case "Offline": return "ከመስመር ውጭ";
            case "Open the app to sign in": return "ለመግባት መተግበሪያውን ይክፈቱ";
            case "Totals are hidden for this account": return "ለዚህ መለያ ድምሩ አይታይም";
            default: return en;
        }
    }

    private static final int[] LBL = {R.id.l1, R.id.l2, R.id.l3, R.id.l4, R.id.l5, R.id.l6};
    private static final int[] VAL = {R.id.v1, R.id.v2, R.id.v3, R.id.v4, R.id.v5, R.id.v6};
    private static final String[] NAMES = {"Net profit", "Income", "Expenses", "Cash", "Transfer", "Cash in hand"};
    private static final String[] KEYS = {"net", "inc", "ex", "cash", "tr", "hand"};

    static void updateAll(Context ctx) {
        Context c = ctx.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, EhitWidgetProvider.class));
        if (ids == null || ids.length == 0) return;

        SharedPreferences sp = p(c);
        boolean am = "am".equals(sp.getString("lang", "en"));
        boolean et = "et".equals(sp.getString("cal", "gc"));
        boolean signedIn = sp.getString("sess", null) != null;
        boolean show = sp.getBoolean("show", false);
        boolean off = sp.getBoolean("off", false);
        JSONObject t = null;
        try {
            String ts = sp.getString("totals", null);
            if (ts != null) t = new JSONObject(ts);
        } catch (Exception ignored) {
        }
        String cur = am ? "ብር" : "ETB";

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.ehit_widget);
        String date;
        if (!signedIn) date = tr(am, "Open the app to sign in");
        else if (!show && t == null && sp.contains("lang")) date = tr(am, "Totals are hidden for this account");
        else if (t != null && t.optString("date").length() > 0) date = dateLabel(t.optString("date"), am, et);
        else date = " ";
        rv.setTextViewText(R.id.wdate, date);

        boolean live = t != null && !off;
        String time = t == null ? "" : new SimpleDateFormat("HH:mm", Locale.US).format(new Date(t.optLong("at", 0)));
        rv.setTextViewText(R.id.wstatus, t == null ? "–" : tr(am, live ? "Live" : "Offline") + " · " + time);
        rv.setTextColor(R.id.wstatus, live ? 0xFF14532D : 0xFF7C2D12);

        for (int i = 0; i < 6; i++) {
            rv.setTextViewText(LBL[i], tr(am, NAMES[i]));
            if (t != null) rv.setTextViewText(VAL[i], money(t.optDouble(KEYS[i], 0), cur));
            else rv.setTextViewText(VAL[i], "–");
        }
        rv.setViewVisibility(R.id.wgrid, signedIn && show ? View.VISIBLE : View.INVISIBLE);

        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) {
            rv.setOnClickPendingIntent(R.id.wroot, PendingIntent.getActivity(c, 2, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        Intent ref = new Intent(c, EhitWidgetProvider.class).setAction(ACTION_REFRESH);
        rv.setOnClickPendingIntent(R.id.wstatus, PendingIntent.getBroadcast(c, 3, ref,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        for (int id : ids) mgr.updateAppWidget(id, rv);
    }
}
