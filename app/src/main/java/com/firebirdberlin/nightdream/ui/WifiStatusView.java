/*
 * NightDream
 * Copyright (C) 2025 Stefan Fruhner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.firebirdberlin.nightdream.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;

import com.firebirdberlin.nightdream.R;

public class WifiStatusView extends TextView {
    private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateStatus();
        }
    };
    private boolean receiverRegistered;
    private boolean displayEnabled = true;

    public WifiStatusView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Drawable wifiIcon = DrawableCompat.wrap(getResources().getDrawable(R.drawable.ic_wifi_status));
        setCompoundDrawablesRelativeWithIntrinsicBounds(wifiIcon, null, null, null);
        setCompoundDrawablePadding((int) (4 * getResources().getDisplayMetrics().density));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        getContext().registerReceiver(wifiStateReceiver, filter);
        receiverRegistered = true;
        updateStatus();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (receiverRegistered) {
            getContext().unregisterReceiver(wifiStateReceiver);
            receiverRegistered = false;
        }
        super.onDetachedFromWindow();
    }

    public void updateStatus() {
        WifiManager wifiManager = (WifiManager) getContext().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            setVisibility(GONE);
            return;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetwork = connectivityManager == null
                ? null : connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        setText(wifiNetwork != null && wifiNetwork.isConnected()
                ? R.string.wifi_status_connected : R.string.wifi_status_connecting);
        setVisibility(displayEnabled ? VISIBLE : GONE);
    }

    public void setDisplayEnabled(boolean enabled) {
        displayEnabled = enabled;
        updateStatus();
    }

    public void setStatusColor(int color) {
        setTextColor(color);
        Drawable[] drawables = getCompoundDrawablesRelative();
        if (drawables[0] != null) {
            DrawableCompat.setTint(drawables[0], color);
        }
    }
}
