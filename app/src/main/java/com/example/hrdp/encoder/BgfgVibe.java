package com.example.hrdp.encoder;

/**
 * Created by Mansu on 2017-03-09.
 */

public class BgfgVibe {
    static public native void init(byte[] initData, int width, int height);
    static public native void detect(byte[] data, int width, int height, long resultPtr);
}