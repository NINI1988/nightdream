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

package com.firebirdberlin.nightdream.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Looper;

import com.firebirdberlin.nightdream.Config;
import com.firebirdberlin.nightdream.R;
import com.firebirdberlin.nightdream.Settings;
import com.firebirdberlin.nightdream.services.AlarmWifiService;
import com.firebirdberlin.nightdream.services.RadioStreamService;
import com.firebirdberlin.radiostreamapi.models.RadioStation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class WebRadioWifiTest {
    @Test
    public void destroyingCurrentRadioStillSchedulesWifiShutdown() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(Settings.PREFS_KEY, 0).edit().clear()
                .putBoolean("radioStreamActivateWiFi", true)
                .putString("radioStreamWifiAfterAlarm", "1").commit();
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(false);
        AlarmWifiService.prepareForManualRadio(context);
        ServiceController<RadioStreamService> controller =
                Robolectric.buildService(RadioStreamService.class).create();
        RadioStreamService.streamingMode = RadioStreamService.StreamingMode.RADIO;

        controller.destroy();

        JobScheduler jobs = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo shutdown = jobs.getPendingJob(Config.JOB_ID_WIFI_SHUTDOWN);
        assertNotNull(shutdown);
        assertEquals(30000L, shutdown.getMinLatencyMillis());
    }

    @Test
    public void switchingStationKeepsWifiEnabledAfterThirtySeconds() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(Settings.PREFS_KEY, 0).edit().clear()
                .putBoolean("radioStreamActivateWiFi", true)
                .putString("radioStreamWifiAfterAlarm", "1").commit();
        Settings settings = new Settings(context);
        RadioStation station = new RadioStation();
        station.name = "Test radio";
        station.stream = "https://example.com/radio";
        station.countryCode = "DE";
        station.favIcon = "";
        settings.persistFavoriteRadioStation(station, 0);
        settings.persistFavoriteRadioStation(station, 1);

        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(false);
        AlarmWifiService.prepareForManualRadio(context);
        ConnectivityManager connectivity = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = new NetworkCapabilities();
        shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        shadowOf(connectivity).setNetworkCapabilities(
                connectivity.getActiveNetwork(), capabilities);
        JobScheduler jobs = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        ServiceController<RadioStreamService> controller =
                Robolectric.buildService(RadioStreamService.class).create();
        RadioStreamService.isRunning = true;
        RadioStreamService.streamingMode = RadioStreamService.StreamingMode.RADIO;
        try {
            WebRadioLayout view = new WebRadioLayout(context);
            view.setActiveStation(0);
            view.findViewById(R.id.web_radio_button2).performClick();

            Intent start = shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();
            assertNotNull(start);
            assertEquals(1, start.getIntExtra(RadioStreamService.EXTRA_RADIO_STATION_INDEX, -1));

            // Android may destroy the stopped service after the next start acquired WiFi.
            Intent stopped = shadowOf(RuntimeEnvironment.getApplication()).getNextStoppedService();
            assertNotNull(stopped);
            controller.destroy();
            controller = null;
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
            JobInfo shutdown = jobs.getPendingJob(Config.JOB_ID_WIFI_SHUTDOWN);
            if (shutdown != null) {
                assertEquals(30000L, shutdown.getMinLatencyMillis());
                JobParameters parameters = ReflectionHelpers.newInstance(JobParameters.class);
                ReflectionHelpers.setField(parameters, "jobId", Config.JOB_ID_WIFI_SHUTDOWN);
                Robolectric.buildService(AlarmWifiService.class).create().get()
                        .onStartJob(parameters);
            }

            assertTrue("Switching stations must keep WiFi enabled after 30 seconds",
                    wifi.isWifiEnabled());
            assertNull(jobs.getPendingJob(Config.JOB_ID_WIFI_SHUTDOWN));
        } finally {
            if (controller != null) {
                controller.destroy();
            }
            RadioStreamService.isRunning = false;
            RadioStreamService.streamingMode = RadioStreamService.StreamingMode.INACTIVE;
        }
    }
}

