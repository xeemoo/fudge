package dev.danielc.fujiapp;

/**
 * Minimal settings bridge required by legacy native backend.
 */
public final class MySettings {

  private static final String DEFAULT_IP_ADDRESS = "192.168.0.1";
  private static final String DEFAULT_CLIENT_NAME = "Fudge";

  private static volatile String ipAddress = DEFAULT_IP_ADDRESS;
  private static volatile String clientName = DEFAULT_CLIENT_NAME;
  private static volatile String[] ipCandidates = new String[] {
    DEFAULT_IP_ADDRESS,
    "192.168.1.1"
  };

  private MySettings() {}

  public static String getIPAddress() {
    return ipAddress;
  }

  public static String getClientName() {
    return clientName;
  }

  public static void setIPAddress(String value) {
    if (value == null || value.isBlank()) return;
    ipAddress = value;
  }

  public static void setClientName(String value) {
    if (value == null || value.isBlank()) return;
    clientName = value;
  }

  public static void setIpCandidates(String[] values) {
    if (values == null || values.length == 0) return;
    ipCandidates = values;
  }

  public static String[] getIpCandidates() {
    return ipCandidates;
  }
}
