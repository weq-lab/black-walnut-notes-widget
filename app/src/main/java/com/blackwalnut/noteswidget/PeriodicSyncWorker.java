package com.blackwalnut.noteswidget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class PeriodicSyncWorker extends Worker {
    private static final String UNIQUE_WORK = "black-walnut-periodic-firestore-sync";

    public PeriodicSyncWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!FirebaseAuthController.isConfigured(getApplicationContext())
                || FirebaseAuthController.currentUser(getApplicationContext()) == null) {
            return Result.success();
        }
        return FirestoreSyncManager.performPeriodicSync(getApplicationContext())
                ? Result.success()
                : Result.retry();
    }

    static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                PeriodicSyncWorker.class,
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
        ).setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }
}
