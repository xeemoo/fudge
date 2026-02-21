package dev.danielc.fujiapp;

import org.json.JSONObject;

/**
 * Minimal frontend bridge required by legacy native backend callbacks.
 */
public final class Frontend {

  private Frontend() {}

  public static void downloadingFile(JSONObject info) {}

  public static void downloadedFile(String path) {}

  public static void print(int resId) {}

  public static void print(String arg) {}

  public static void sendCamName(String value) {}

  public static void notifyDownloadProgress(int percent) {}

  public static void notifyDownloadSpeed(int mbps) {}

  public static void onCameraRegistered(String model, String name, String ip) {}

  public static void onCameraWantsToConnect(String model, String name) {}
}
