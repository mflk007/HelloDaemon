package com.xdandroid.sample;

import android.app.*;
import android.content.*;

import com.karumi.dexter.Dexter;
import com.xdandroid.hellodaemon.*;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DaemonEnv.initialize(this, TraceServiceImpl.class, DaemonEnv.DEFAULT_WAKE_UP_INTERVAL);
        try {startService(new Intent(this, TraceServiceImpl.class));} catch (Exception ignored) {}
        Dexter.initialize(this);
    }
}
