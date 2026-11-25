package com.steadfast.guard;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Device admin: Android won't let Steadfast be uninstalled until this is deactivated (which alerts the partner). */
public class AdminReceiver extends DeviceAdminReceiver {

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        Config c = Config.get(context);
        if (c.isProtected()) {
            EventLog.record(context, EventLog.TAMPER, "Someone tried to remove Steadfast's uninstall protection.");
        }
        return "Removing this lets Steadfast be uninstalled. Your accountability partner will be told.";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Config c = Config.get(context);
        if (c.isProtected()) {
            EventLog.record(context, EventLog.TAMPER, "Uninstall protection was removed. Steadfast may be uninstalled next.");
            Partner.flushNow(context);
        }
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        Config c = Config.get(context);
        if (c.isProtected()) EventLog.record(context, EventLog.CHANGE, "Uninstall protection switched on.");
    }
}
