/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
/* Header for class com_example_yj_myapplication_Codec */

#ifndef _Included_com_example_hrdp_encoder_Codec
#define _Included_com_example_hrdp_encoder_Codec
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jint JNICALL Java_com_example_hrdp_encoder_Codec_encode_1get_1h264_1identifier
  (JNIEnv *, jobject);

JNIEXPORT jint JNICALL Java_com_example_hrdp_encoder_Codec_encode_1get_1h265_1identifier
  (JNIEnv *, jobject);

/*
 * Class:     com_example_yj_myapplication_Codec
 * Method:    encode_init
 * Signature: (IIIIIII)V
 */
JNIEXPORT void JNICALL Java_com_example_hrdp_encoder_Codec_encode_1init
  (JNIEnv *, jobject, jint, jint, jint, jint, jint, jint, jint, jint);

/*
 * Class:     com_example_yj_myapplication_Codec
 * Method:    surface_init
 * Signature: (Landroid/view/Surface;II)V
 */
JNIEXPORT void JNICALL Java_com_example_hrdp_encoder_Codec_surface_1init
  (JNIEnv *, jobject, jobject, jint, jint);

/*
 * Class:     com_example_yj_myapplication_Codec
 * Method:    encode_frame
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_example_hrdp_encoder_Codec_encode_1frame
  (JNIEnv *, jobject, jbyteArray);

/*
 * Class:     com_example_yj_myapplication_Codec
 * Method:    encode_release
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_example_hrdp_encoder_Codec_encode_1release
  (JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif
