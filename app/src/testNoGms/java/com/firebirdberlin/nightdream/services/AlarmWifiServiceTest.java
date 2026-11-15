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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;

import com.firebirdberlin.nightdream.Settings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

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
    public void disabledSettingDoesNotEnableWifi() {
        preferences.edit().putBoolean("radioStreamActivateWiFi", false).commit();

        assertFalse(AlarmWifiService.prepareForManualRadio(context));
        AlarmWifiService.prepareForAlarm(context);

        assertFalse(wifi.isWifiEnabled());
        assertNull(jobs.getPendingJob(com.firebirdberlin.nightdream.Config.JOB_ID_WIFI_SHUTDOWN));
    }
}
