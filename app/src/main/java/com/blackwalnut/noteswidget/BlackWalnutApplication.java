package com.blackwalnut.noteswidget;

import android.app.Application;

public class BlackWalnutApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.FIREBASE_CONFIGURED) PeriodicSyncWorker.schedule(this);
    }
}
