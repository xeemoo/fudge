package dev.danielc.common;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.PatternMatcher;
import android.provider.Settings;
import java.lang.reflect.Method;

/**
 * Legacy Wi-Fi helper mirrored from android/app so native cTryConnectWiFi()
 * can always resolve a valid Android network handle.
 */
public class WiFiComm {
  public static final String TAG = "wifi";

  private static void logD(String message) {}

  private static void logE(String message) {}

  private static ConnectivityManager cm = null;
  static Network wifiDevice = null;
  static Network foundWiFiDevice = null;

  public void setConnectivityManager(ConnectivityManager cm) {
    WiFiComm.cm = cm;
  }

  public static void registerConnectivityManager(ConnectivityManager connectivityManager) {
    cm = connectivityManager;
  }

  // Error codes
  public static final int NOT_AVAILABLE = -101;
  public static final int NOT_CONNECTED = -102;
  public static final int UNSUPPORTED_SDK = -103;

  // Non-static event handlers since a frontend may need to spawn two listeners
  public Handler handler = null;
  public Runnable onAvailable = null;
  public Runnable onWiFiSelectAvailable = null;
  public Runnable onWiFiSelectCancel = null;
  public boolean blockEvents = false;

  void run(Runnable r) {
    if (blockEvents) return;
    if (r != null) {
      if (handler == null) {
        r.run();
      } else {
        handler.post(r);
      }
    }
  }

  ConnectivityManager.NetworkCallback lastCallback = null;

  /** Opens an Android 10+ popup to prompt the user to select a WiFi network */
  public int connectToAccessPoint(Context ctx, String password, PatternMatcher pattern) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ConnectivityManager connectivityManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (lastCallback != null) {
        connectivityManager.unregisterNetworkCallback(lastCallback);
      }

      WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder();
      builder.setSsidPattern(pattern);

      if (password != null) {
        builder.setWpa2Passphrase(password);
        logD(String.format("password: %s", password));
      }
      NetworkSpecifier specifier = builder.build();

      NetworkRequest request = new NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .setNetworkSpecifier(specifier)
        .build();

      lastCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
          logD("Network selected by user: " + network);
          foundWiFiDevice = network;
          run(onWiFiSelectAvailable);
          isHandlingConflictingConnections();
        }

        @Override
        public void onUnavailable() {
          logD("Network unavailable, not selected by user");
          foundWiFiDevice = null;
          run(onWiFiSelectCancel);
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
          logE("capabilities changed");
        }
      };
      connectivityManager.requestNetwork(request, lastCallback);
      return 0;
    }
    return UNSUPPORTED_SDK;
  }

  public void startNetworkListeners(Context ctx) {
    ConnectivityManager m = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkRequest.Builder requestBuilder = new NetworkRequest.Builder();
    requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);

    ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
      Intent settings = null;

      @Override
      public void onAvailable(Network network) {
        logD("Wifi network is available: " + network.getNetworkHandle());
        if (settings != null && ctx instanceof Activity) {
          ((Activity) ctx).finish();
        }

        wifiDevice = network;
        run(onAvailable);
      }

      @Override
      public void onLost(Network network) {
        logE("Lost network");
        if (network.equals(wifiDevice)) {
          wifiDevice = null;
        }
      }

      @Override
      public void onUnavailable() {
        logE("Network unavailable");
        wifiDevice = null;
      }

      @Override
      public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        logE("capabilities changed");
      }
    };

    try {
      m.requestNetwork(requestBuilder.build(), networkCallback);
    } catch (Exception e) {
      Intent goToSettings = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
      goToSettings.setData(Uri.parse("package:" + ctx.getPackageName()));
      ctx.startActivity(goToSettings);
    }
  }

  public static synchronized void setFoundWiFiDevice(Network network) {
    foundWiFiDevice = network;
    logD("setFoundWiFiDevice handle=" + (network == null ? "null" : network.getNetworkHandle()));
  }

  public static synchronized void clearFoundWiFiDevice() {
    foundWiFiDevice = null;
    logD("clearFoundWiFiDevice");
  }

  public static synchronized void clearFoundWiFiDeviceIfMatches(Network network) {
    if (network == null) {
      return;
    }
    if (network.equals(foundWiFiDevice)) {
      foundWiFiDevice = null;
      logD("clearFoundWiFiDeviceIfMatches handle=" + network.getNetworkHandle());
    }
  }

  public static synchronized void setDefaultWiFiDevice(Network network) {
    wifiDevice = network;
    logD("setDefaultWiFiDevice handle=" + (network == null ? "null" : network.getNetworkHandle()));
  }

  public static synchronized void clearDefaultWiFiDeviceIfMatches(Network network) {
    if (network == null) {
      return;
    }
    if (network.equals(wifiDevice)) {
      wifiDevice = null;
      logD("clearDefaultWiFiDeviceIfMatches handle=" + network.getNetworkHandle());
    }
  }

  /** Determine if the device is handling two different WiFi connections at the same time, on the same band. */
  public static boolean isHandlingConflictingConnections() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      if (isNetworkValid(wifiDevice) && isNetworkValid(foundWiFiDevice)) {
        NetworkCapabilities c1 = cm.getNetworkCapabilities(wifiDevice);
        if (c1 == null) return false;
        WifiInfo info1 = (WifiInfo) c1.getTransportInfo();
        if (info1 == null) return false;
        int mainBand = info1.getFrequency() / 100;

        NetworkCapabilities c2 = cm.getNetworkCapabilities(foundWiFiDevice);
        if (c2 == null) return false;
        WifiInfo info2 = (WifiInfo) c2.getTransportInfo();
        if (info2 == null) return false;
        if (info1.equals(info2)) return false;
        int secondBand = info2.getFrequency() / 100;
        return mainBand == secondBand;
      }
    }
    return false;
  }

  public static boolean isNetworkValid(Network net) {
    if (cm == null) return false;
    if (net == null) return false;
    @SuppressWarnings("deprecation")
    NetworkInfo wifiInfo = cm.getNetworkInfo(net);
    if (wifiInfo == null) return false;
    return wifiInfo.isAvailable();
  }

  @SuppressLint("ObsoleteSdkInt")
  public static long getNetworkHandle() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return UNSUPPORTED_SDK;
    }

    if (isNetworkValid(foundWiFiDevice)) {
      logD("Returning found wifi device");
      return foundWiFiDevice.getNetworkHandle();
    }

    if (isNetworkValid(wifiDevice)) {
      logD("Returning default WiFi");
      return wifiDevice.getNetworkHandle();
    }

    logD("WiFi network not available");
    return NOT_AVAILABLE;
  }

  public static boolean isWiFiModuleCapableOfHandlingTwoConnections(Context ctx) {
    WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      return wm.isStaConcurrencyForLocalOnlyConnectionsSupported();
    }
    return false;
  }

  public static boolean isHotSpotEnabled(Context ctx) {
    WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    try {
      Method m = wm.getClass().getDeclaredMethod("isWifiApEnabled");
      m.setAccessible(true);
      if (!(boolean) m.invoke(wm)) {
        return false;
      }
    } catch (Exception e) {
      return false;
    }

    return true;
  }
}
