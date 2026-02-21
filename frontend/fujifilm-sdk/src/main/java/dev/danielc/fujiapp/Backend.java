package dev.danielc.fujiapp;

import dev.danielc.common.Camlib;

/**
 * Legacy native backend bridge stripped from UI dependencies.
 */
public final class Backend extends Camlib {

  private static volatile boolean nativeLoaded = false;
  private static volatile Throwable nativeLoadError = null;
  private static volatile boolean initialized = false;

  static {
    try {
      System.loadLibrary("fudge");
      nativeLoaded = true;
    } catch (Throwable t) {
      nativeLoadError = t;
    }
  }

  private Backend() {}

  public static synchronized void ensureNativeReady() {
    if (!nativeLoaded) {
      throw new IllegalStateException("Legacy native library 'fudge' is not available", nativeLoadError);
    }

    if (!initialized) {
      cInit();
      initialized = true;
    }
  }

  public native static void cInit();

  public native static boolean cGetKillSwitch();
  public native static int cGetTransport();
  public native static int cTryConnectWiFi(int extraTmout);
  public native static int cFujiSetup();
  public native static int cPtpFujiPing();
  public native static int[] cFujiGetObjectHandles();
  public native static int cFujiConfigImageGallery();
  public native static byte[] cFujiGetThumb(int handle);
  public native static int cFujiImportFiles(int[] handles, int filterMask);

  public native static String cFujiBeginDownloadGetObjectInfo(int handle);
  public native static int cFujiGetFile(int handle, byte[] array, int fileSize);
  public native static int cFujiDownloadFile(int handle, String path);
}
