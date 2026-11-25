package com.steadfast.guard;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Notify.createChannel(this);
        Jobs.schedule(this);
        Config c = Config.get(this);
        if (c.isProtected()) Guard.startVpn(this);
    }
}
