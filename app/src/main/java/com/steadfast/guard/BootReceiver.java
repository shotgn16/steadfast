package com.steadfast.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the web filter and background checks after a reboot or app update. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Jobs.schedule(context);
        Config c = Config.get(context);
        if (c.isProtected()) Guard.startVpn(context);
    }
}
