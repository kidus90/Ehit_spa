package com.ehit.spa;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class EhitWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        WidgetLogic.updateAll(c);
        WidgetLogic.schedule(c);
        WidgetLogic.fetchAsync(c, goAsync());
    }

    @Override
    public void onEnabled(Context c) {
        WidgetLogic.schedule(c);
    }

    @Override
    public void onDisabled(Context c) {
        WidgetLogic.cancel(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (WidgetLogic.ACTION_TICK.equals(a) || WidgetLogic.ACTION_REFRESH.equals(a)) {
            WidgetLogic.schedule(c); // arm the next refresh first so the 2-minute chain never breaks
            BroadcastReceiver.PendingResult pr = goAsync();
            WidgetLogic.fetchAsync(c, pr);
            return;
        }
        super.onReceive(c, i);
    }
}
