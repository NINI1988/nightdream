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
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.view.View;

import com.firebirdberlin.nightdream.NightDreamActivity;
import com.firebirdberlin.nightdream.R;
import com.firebirdberlin.nightdream.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class IconVisibilityTest {
    @Test
    public void disabledIconsAreHiddenAndEnabledIconsAreVisible() {
        Context context = RuntimeEnvironment.getApplication();
        androidx.work.WorkManager.initialize(context,
                new androidx.work.Configuration.Builder().build());
        SharedPreferences preferences = context.getSharedPreferences(Settings.PREFS_KEY, 0);
        preferences.edit().putBoolean("showWeatherMenu", false)
                .putBoolean("showSmartHomeMenu", false)
                .putBoolean("showWifiStatus", false).commit();
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(true);
        context.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED));
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
                context.getPackageName() + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");

        try (ActivityController<NightDreamActivity> controller =
                     Robolectric.buildActivity(NightDreamActivity.class).setup()) {
            NightDreamActivity activity = controller.get();
            activity.onWindowFocusChanged(true);
            assertEquals(View.GONE, activity.findViewById(R.id.icon_weather_forecast).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.torch_icon).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.wifiStatusView).getVisibility());

            preferences.edit().putBoolean("showWeatherMenu", true)
                    .putBoolean("showSmartHomeMenu", true)
                    .putBoolean("showWifiStatus", true).commit();
            activity.onWindowFocusChanged(true);

            assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.icon_weather_forecast).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.torch_icon).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.wifiStatusView).getVisibility());
        }
    }

    @Test
    public void wifiIconRemainsHiddenWhenWifiIsOff() {
        Context context = RuntimeEnvironment.getApplication();
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(false);
        WifiStatusView view = new WifiStatusView(context, null);

        view.setDisplayEnabled(true);

        assertEquals(View.GONE, view.getVisibility());
    }
}
