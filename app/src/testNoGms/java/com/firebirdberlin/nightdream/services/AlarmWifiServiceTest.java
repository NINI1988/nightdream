/*
 * NightDream
 * Copyright (C) 2026 Stefan Fruhner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.firebirdberlin.nightdream.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Looper;

import com.firebirdberlin.nightdream.Settings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AlarmWifiServiceTest {
    private Context context;
    private SharedPreferences preferences;
    private WifiManager wifi;
    private JobScheduler jobs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences(Settings.PREFS_KEY, Context.MODE_PRIVATE);
        preferences.edit().clear().putBoolean("radioStreamActivateWiFi", true)
                .putString("radioStreamWifiAfterAlarm", "1").commit();
        wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        jobs = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        wifi.setWifiEnabled(false);
    }

    @Test
    public void manualRadioEnablesWifiAndSchedulesRestoration() {
        assertTrue(AlarmWifiService.prepareForManualRadio(context));
        assertTrue(wifi.isWifiEnabled());

        AlarmWifiService.restoreAfterManualRadio(context);

        assertNotNull(jobs.getPendingJob(com.firebirdberlin.nightdream.Config.JOB_ID_WIFI_SHUTDOWN));
    }

    @Test
    public void wifiAlreadyEnabledIsNotScheduledForShutdown() {
        wifi.setWifiEnabled(true);
        assertFalse(AlarmWifiService.prepareForManualRadio(context));

        AlarmWifiService.restoreAfterManualRadio(context);

        assertNull(jobs.getPendingJob(com.firebirdberlin.nightdream.Config.JOB_ID_WIFI_SHUTDOWN));
        assertTrue(wifi.isWifiEnabled());
    }

    @Test
    public void alarmAndManualRadioKeepWifiUntilBothAreFinished() {
        AlarmWifiService.prepareForAlarm(context);
        AlarmWifiService.prepareForManualRadio(context);

        AlarmWifiService.restoreAfterAlarm(context);
        assertNull(jobs.getPendingJob(com.firebirdberlin.nightdream.Config.JOB_ID_WIFI_SHUTDOWN));
        assertTrue(wifi.isWifiEnabled());

        AlarmWifiService.restoreAfterManualRadio(context);
        assertNotNull(jobs.getPendingJob(com.firebirdberlin.nightdream.Config.JOB_ID_WIFI_SHUTDOWN));
    }

    @Test
    public void restartingRadioCancelsPendingShutdown() {
        AlarmWifiService.prepareForManualRadio(context);
        AlarmWifiService.restoreAfterManualRadio(context);
        AlarmWifiService.prepareForManualRadio(context);

        assertNull(jobs.getPendingJob(com.firebirdberlin.nightdream.Config.JOB_ID_WIFI_SHUTDOWN));
        assertTrue(wifi.isWifiEnabled());
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public void radioStartedBeforeAlarmShutdownKeepsWifiUntilRadioStops() {
        int shutdownJobId = com.firebirdberlin.nightdream.Config.JOB_ID_WIFI_SHUTDOWN;
        AlarmWifiService.prepareForAlarm(context);
        assertTrue(wifi.isWifiEnabled());

        AlarmWifiService.restoreAfterAlarm(context);
        JobInfo alarmShutdown = jobs.getPendingJob(shutdownJobId);
        assertNotNull(alarmShutdown);
        assertEquals(30000L, alarmShutdown.getMinLatencyMillis());

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(29));
        AlarmWifiService.prepareForManualRadio(context);
        assertNull(jobs.getPendingJob(shutdownJobId));

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        // Deliver even a stale job to verify it cannot interrupt the active radio.
        runWifiShutdownJob();
        assertTrue(wifi.isWifiEnabled());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        assertTrue(wifi.isWifiEnabled());
        assertNull(jobs.getPendingJob(shutdownJobId));

        AlarmWifiService.restoreAfterManualRadio(context);
        JobInfo radioShutdown = jobs.getPendingJob(shutdownJobId);
        assertNotNull(radioShutdown);
        assertEquals(30000L, radioShutdown.getMinLatencyMillis());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(29));
        assertTrue(wifi.isWifiEnabled());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        runWifiShutdownJob();
        assertFalse(wifi.isWifiEnabled());
    }

    private void runWifiShutdownJob() {
        JobParameters parameters = ReflectionHelpers.newInstance(JobParameters.class);
        ReflectionHelpers.setField(parameters, "jobId",
                com.firebirdberlin.nightdream.Config.JOB_ID_WIFI_SHUTDOWN);
        ServiceController<AlarmWifiService> controller =
                Robolectric.buildService(AlarmWifiService.class).create();
        try {
            controller.get().onStartJob(parameters);
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void disabledSettingDoesNotEnableWifi() {
        preferences.edit().putBoolean("radioStreamActivateWiFi", false).commit();

        assertFalse(AlarmWifiService.prepareForManualRadio(context));
        AlarmWifiService.prepareForAlarm(context);

        assertFalse(wifi.isWifiEnabled());
        assertNull(jobs.getPendingJob(com.firebirdberlin.nightdream.Config.JOB_ID_WIFI_SHUTDOWN));
    }
}
