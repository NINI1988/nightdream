/*
 * NightDream
 * Copyright (C) 2025 Stefan Fruhner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.firebirdberlin.nightdream.services;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.firebirdberlin.nightdream.Config;
import com.firebirdberlin.nightdream.Settings;
import com.firebirdberlin.nightdream.Utility;
import com.firebirdberlin.nightdream.models.SimpleTime;

public class AlarmWifiService extends JobService {
    private static final String TAG = AlarmWifiService.class.getSimpleName();
    private static final String PREF_MANUAL_RADIO_ACTIVE = "manualRadioWifiActive";
    private static final String PREF_ALARM_ACTIVE = "alarmWifiActive";
    private static final String PREF_WIFI_WAS_ENABLED = "wifiWasEnabledBeforeRadioUse";
    private static final long NETWORK_CHECK_INTERVAL_MILLIS = 500L;
    private static final int MAX_NETWORK_CHECK_ATTEMPTS = 120;
    private static final long WIFI_SHUTDOWN_DELAY_MILLIS = 30000L;
    private static final Object WIFI_LOCK = new Object();

    public static void runWhenNetworkAvailable(
            @NonNull Context context,
            @NonNull Runnable onNetworkAvailable,
            @NonNull Runnable onTimeout) {
        Handler handler = new Handler(Looper.getMainLooper());
        waitForNetwork(
                context.getApplicationContext(),
                handler,
                onNetworkAvailable,
                onTimeout,
                0
        );
    }

    public static void runWhenManualRadioNetworkAvailable(
            @NonNull Context context,
            @NonNull Runnable onNetworkAvailable,
            @NonNull Runnable onTimeout) {
        boolean wifiActivationPending = prepareForManualRadio(context);
        if (wifiActivationPending || !Utility.hasNetworkConnection(context)) {
            runWhenNetworkAvailable(context, onNetworkAvailable, onTimeout);
        } else {
            onNetworkAvailable.run();
        }
    }

    private static void waitForNetwork(
            Context context,
            Handler handler,
            Runnable onNetworkAvailable,
            Runnable onTimeout,
            int attempt) {
        if (Utility.hasNetworkConnection(context)) {
            onNetworkAvailable.run();
            return;
        }
        if (attempt >= MAX_NETWORK_CHECK_ATTEMPTS) {
            onTimeout.run();
            return;
        }
        handler.postDelayed(
                () -> waitForNetwork(
                        context,
                        handler,
                        onNetworkAvailable,
                        onTimeout,
                        attempt + 1
                ),
                NETWORK_CHECK_INTERVAL_MILLIS
        );
    }

    public static boolean prepareForManualRadio(Context context) {
        Settings settings = new Settings(context);
        if (!settings.radioStreamActivateWiFi) {
            return false;
        }

        return acquireWifi(context, PREF_MANUAL_RADIO_ACTIVE);
    }

    public static void prepareForAlarm(Context context) {
        Settings settings = new Settings(context);
        if (settings.radioStreamActivateWiFi) {
            acquireWifi(context, PREF_ALARM_ACTIVE);
        }
    }

    public static void transferAlarmToManualRadio(Context context) {
        Settings settings = new Settings(context);
        if (settings.radioStreamActivateWiFi) {
            acquireWifi(context, PREF_MANUAL_RADIO_ACTIVE);
        }
    }

    private static boolean acquireWifi(Context context, String ownerKey) {
        synchronized (WIFI_LOCK) {
            Context appContext = context.getApplicationContext();
            SharedPreferences preferences = appContext.getSharedPreferences(Settings.PREFS_KEY, 0);
            cancelShutdownJob(appContext);

            boolean alreadyInUse = preferences.getBoolean(PREF_MANUAL_RADIO_ACTIVE, false)
                    || preferences.getBoolean(PREF_ALARM_ACTIVE, false);
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (!alreadyInUse && !preferences.contains(PREF_WIFI_WAS_ENABLED)) {
                boolean wifiWasEnabled = wifiManager != null && wifiManager.isWifiEnabled();
                preferences.edit().putBoolean(PREF_WIFI_WAS_ENABLED, wifiWasEnabled).apply();
            }
            preferences.edit().putBoolean(ownerKey, true).apply();

            if (wifiManager == null || wifiManager.isWifiEnabled()) {
                return false;
            }
            setWifiEnabled(appContext, true);
            return true;
        }
    }

    public static void restoreAfterManualRadio(Context context) {
        releaseWifi(context, PREF_MANUAL_RADIO_ACTIVE);
    }

    public static void scheduleJob(Context context, SimpleTime nextAlarmTime) {
        Settings settings = new Settings(context);
        if (!settings.radioStreamActivateWiFi || nextAlarmTime.radioStationIndex < 0) {
            cancelJob(context);
            return;
        }

        ComponentName serviceComponent = new ComponentName(context, AlarmWifiService.class);
        JobInfo.Builder builder = new JobInfo.Builder(Config.JOB_ID_ALARM_WIFI, serviceComponent);
        builder.setPersisted(true);
        builder.setRequiresCharging(false);

        long fiveMinutes = 5L * 60L * 1000L;
        long delay = Math.max(0L, nextAlarmTime.getMillis() - fiveMinutes - System.currentTimeMillis());
        builder.setMinimumLatency(delay);
        builder.setOverrideDeadline(delay + 30000L);

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null && jobScheduler.schedule(builder.build()) != JobScheduler.RESULT_SUCCESS) {
            Log.e(TAG, "Unable to schedule WiFi activation");
        }
    }

    public static void cancelJob(Context context) {
        cancelScheduledActivation(context);
        releaseWifi(context, PREF_ALARM_ACTIVE);
    }

    public static void cancelScheduledActivation(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            jobScheduler.cancel(Config.JOB_ID_ALARM_WIFI);
        }
    }

    public static void restoreAfterAlarm(Context context) {
        releaseWifi(context, PREF_ALARM_ACTIVE);
    }

    private static void releaseWifi(Context context, String ownerKey) {
        synchronized (WIFI_LOCK) {
            Context appContext = context.getApplicationContext();
            SharedPreferences preferences = appContext.getSharedPreferences(Settings.PREFS_KEY, 0);
            if (!preferences.getBoolean(ownerKey, false)) {
                return;
            }

            preferences.edit().remove(ownerKey).apply();
            boolean stillInUse = preferences.getBoolean(PREF_MANUAL_RADIO_ACTIVE, false)
                    || preferences.getBoolean(PREF_ALARM_ACTIVE, false);
            if (!stillInUse && preferences.contains(PREF_WIFI_WAS_ENABLED)) {
                scheduleWifiShutdown(appContext);
            }
        }
    }

    private static void scheduleWifiShutdown(Context context) {
        Settings settings = new Settings(context);
        SharedPreferences preferences = context.getSharedPreferences(Settings.PREFS_KEY, 0);
        if (!settings.radioStreamActivateWiFi || settings.radioStreamWifiAfterAlarm == 2
                || (settings.radioStreamWifiAfterAlarm == 1
                && preferences.getBoolean(PREF_WIFI_WAS_ENABLED, true))) {
            clearWifiSession(context);
            return;
        }

        ComponentName serviceComponent = new ComponentName(context, AlarmWifiService.class);
        JobInfo job = new JobInfo.Builder(Config.JOB_ID_WIFI_SHUTDOWN, serviceComponent)
                .setPersisted(true)
                .setMinimumLatency(WIFI_SHUTDOWN_DELAY_MILLIS)
                .setOverrideDeadline(WIFI_SHUTDOWN_DELAY_MILLIS)
                .build();
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(
                Context.JOB_SCHEDULER_SERVICE
        );
        if (jobScheduler == null
                || jobScheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            Log.e(TAG, "Unable to schedule WiFi shutdown");
        }
    }

    private static void shutdownWifiIfUnused(Context context) {
        synchronized (WIFI_LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(Settings.PREFS_KEY, 0);
            boolean stillInUse = preferences.getBoolean(PREF_MANUAL_RADIO_ACTIVE, false)
                    || preferences.getBoolean(PREF_ALARM_ACTIVE, false);
            if (!stillInUse && preferences.contains(PREF_WIFI_WAS_ENABLED)) {
                Settings settings = new Settings(context);
                boolean shouldTurnOff = settings.radioStreamActivateWiFi
                        && settings.radioStreamWifiAfterAlarm != 2
                        && (settings.radioStreamWifiAfterAlarm == 0
                        || !preferences.getBoolean(PREF_WIFI_WAS_ENABLED, true));
                if (shouldTurnOff) {
                    setWifiEnabled(context, false);
                }
                clearWifiSession(context);
            }
        }
    }

    private static void cancelShutdownJob(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(
                Context.JOB_SCHEDULER_SERVICE
        );
        if (jobScheduler != null) {
            jobScheduler.cancel(Config.JOB_ID_WIFI_SHUTDOWN);
        }
    }

    private static void clearWifiSession(Context context) {
        context.getSharedPreferences(Settings.PREFS_KEY, 0).edit()
                .remove(PREF_WIFI_WAS_ENABLED)
                .apply();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (params.getJobId() == Config.JOB_ID_WIFI_SHUTDOWN) {
            shutdownWifiIfUnused(this);
        } else {
            prepareForAlarm(this);
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    private static void setWifiEnabled(Context context, boolean enabled) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return;
        }
        try {
            boolean success = wifiManager.setWifiEnabled(enabled);
            Log.i(TAG, "WiFi is " + (success ? "" : "not ")
                    + (enabled ? "enabled" : "disabled"));
        } catch (SecurityException exception) {
            Log.w(TAG, "Cannot change WiFi state on this device", exception);
        }
    }

}
