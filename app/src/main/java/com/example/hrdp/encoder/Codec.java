package com.example.hrdp.encoder;

/**
 * Created by yj on 16. 7. 29.
 */
public class Codec {
    static {
        System.loadLibrary("openh264");
        System.loadLibrary("ndkTest");
    }

    static public native int encode_get_h264_identifier();
    static public native int encode_get_h265_identifier();
    static public native void encode_init(int width, int height, int bit_rate, int frame_rate, int color_format, int key_frame_rate, int crf, int codec_type);
    static public native byte[] encode_frame(byte[] data);
    static public native void encode_release();
}