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
    private static final String PREF_WIFI_CHANGED_BY_ALARM = "radioStreamWifiChangedByAlarm";
    private static final String PREF_WIFI_CHANGED_BY_MANUAL_RADIO =
            "radioStreamWifiChangedByManualRadio";
    private static final long NETWORK_CHECK_INTERVAL_MILLIS = 500L;
    private static final int MAX_NETWORK_CHECK_ATTEMPTS = 120;

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

        return enableWifiIfNeeded(context, PREF_WIFI_CHANGED_BY_MANUAL_RADIO);
    }

    public static void prepareForAlarm(Context context) {
        Settings settings = new Settings(context);
        if (settings.radioStreamActivateWiFi) {
            enableWifiIfNeeded(context, PREF_WIFI_CHANGED_BY_ALARM);
        }
    }

    private static boolean enableWifiIfNeeded(Context context, String changedStateKey) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || wifiManager.isWifiEnabled()) {
            return false;
        }

        setChangedState(context, changedStateKey, true);
        setWifiEnabled(context, true);
        return true;
    }

    public static void restoreAfterManualRadio(Context context) {
        Settings settings = new Settings(context);
        boolean changedByRadio = getChangedState(context, PREF_WIFI_CHANGED_BY_MANUAL_RADIO);
        if (settings.radioStreamActivateWiFi && settings.radioStreamWifiAfterAlarm != 2
                && (settings.radioStreamWifiAfterAlarm == 0 || changedByRadio)) {
            setWifiEnabled(context, false);
        }
        clearChangedState(context, PREF_WIFI_CHANGED_BY_MANUAL_RADIO);
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
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            jobScheduler.cancel(Config.JOB_ID_ALARM_WIFI);
        }
    }

    public static void restoreAfterAlarm(Context context) {
        Settings settings = new Settings(context);
        if (!settings.radioStreamActivateWiFi) {
            clearChangedState(context, PREF_WIFI_CHANGED_BY_ALARM);
            return;
        }
        if (settings.radioStreamWifiAfterAlarm == 2) {
            clearChangedState(context, PREF_WIFI_CHANGED_BY_ALARM);
            return;
        }

        boolean changedByAlarm = getChangedState(context, PREF_WIFI_CHANGED_BY_ALARM);
        if (settings.radioStreamWifiAfterAlarm == 0 || changedByAlarm) {
            setWifiEnabled(context, false);
        }
        clearChangedState(context, PREF_WIFI_CHANGED_BY_ALARM);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        prepareForAlarm(this);
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

    private static void setChangedState(Context context, String key, boolean changed) {
        SharedPreferences preferences = context.getSharedPreferences(Settings.PREFS_KEY, 0);
        preferences.edit().putBoolean(key, changed).apply();
    }

    private static boolean getChangedState(Context context, String key) {
        return context.getSharedPreferences(Settings.PREFS_KEY, 0).getBoolean(key, false);
    }

    private static void clearChangedState(Context context, String key) {
        SharedPreferences preferences = context.getSharedPreferences(Settings.PREFS_KEY, 0);
        preferences.edit().remove(key).apply();
    }
}
