package dev.danielc.common;

import org.json.JSONObject;

/**
 * JNI bridge mirrored from legacy app so existing native symbols stay compatible.
 */
public class Camlib {

  public static final int PTP_OF_Undefined = 0x3000;
  public static final int PTP_OF_Folder = 0x3001;
  public static final int PTP_OF_MOV = 0x300d;
  public static final int PTP_OF_JPEG = 0x3801;
  public static final int PTP_OF_RAW = 0xb103;

  public static final int PTP_OK = 0;
  public static final int PTP_NO_DEVICE = -1;
  public static final int PTP_NO_PERM = -2;
  public static final int PTP_OPEN_FAIL = -3;
  public static final int PTP_OUT_OF_MEM = -4;
  public static final int PTP_IO_ERR = -5;
  public static final int PTP_RUNTIME_ERR = -6;
  public static final int PTP_UNSUPPORTED = -7;
  public static final int PTP_CHECK_CODE = -8;
  public static final int PTP_CANCELED = -9;

  public native static byte[] cPtpGetThumb(int handle);
  public native static int cPtpGetPropValue(int code);
  public native static int cPtpOpenSession();
  public native static int cPtpCloseSession();
  public native static JSONObject cGetObjectInfo(int handle);

  public native static void cPtpObjectServiceStart(int[] handles);
  public native static JSONObject cPtpObjectServiceGet(int handle);
  public native static JSONObject cPtpObjectServiceGetIndex(int index);
  public native static JSONObject[] cPtpObjectServiceGetFilled();
  public native static int cPtpObjectServiceStep();
  public native static void cPtpObjectServiceAddPriority(int handle);

  public native static int cObjectServiceLength();
  public native static int cObjectServiceGetHandleAt(int index);

  public final static int PTP_SELET_JPEG = 1;
  public final static int PTP_SELET_RAW = 2;
  public final static int PTP_SELET_MOV = 3;

  public final static int PTP_SORT_BY_OLDEST = 1;
  public final static int PTP_SORT_BY_NEWEST = 2;
  public final static int PTP_SORT_BY_ALPHA_A_Z = 3;
  public final static int PTP_SORT_BY_ALPHA_Z_A = 4;
  public final static int PTP_SORT_BY_JPEGS_FIRST = 5;
  public final static int PTP_SORT_BY_MOVS_FIRST = 6;
  public final static int PTP_SORT_BY_RAWS_FIRST = 7;
}
