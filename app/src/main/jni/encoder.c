//
// Created by yj on 16. 7. 29.
//
#include "com_example_hrdp_encoder_Codec.h"
/*
 * Class:     com_example_hrdp_encoder_Codec
 * Method:    encode_init
 * Signature: (IIIIII)V
 */

AVCodec *d_codec = NULL;
AVCodecContext *d_c = NULL;
AVFrame* d_frame = NULL;
uint8_t *buffer = NULL;

JNIEXPORT jint JNICALL Java_com_example_hrdp_encoder_Codec_encode_1get_1h264_1identifier
  (JNIEnv *env, jobject jobj)
  {
    return AV_CODEC_ID_H264;
  }

JNIEXPORT jint JNICALL Java_com_example_hrdp_encoder_Codec_encode_1get_1h265_1identifier
  (JNIEnv *env, jobject jobj)
  {
    return AV_CODEC_ID_H265;
  }


JNIEXPORT void JNICALL Java_com_example_hrdp_encoder_Codec_encode_1init
  (JNIEnv *env, jobject jobj, jint width, jint height, jint bit_rate, jint frame_rate, jint color_format, jint key_frame_rate, jint crf, jint codec)
  {
    //__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "android_ndk_codec_set_start\n");
    avcodec_register_all();
    d_codec = avcodec_find_encoder(codec);
    if (!d_codec) {
        __android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "Codec not found\n");
        exit(1);
    }

    d_c = avcodec_alloc_context3(d_codec);
    if (!d_c) {
        __android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "Could not allocate video codec context\n");
        exit(1);
    }

    /* put sample parameters */
    d_c->bit_rate = bit_rate;
    /* resolution must be a multiple of two */
    d_c->width = width;
    d_c->height = height;
    /* frames per second */
    d_c->time_base.num = 1;
    d_c->time_base.den = frame_rate;
    d_c->gop_size = key_frame_rate;
    //c->max_b_frames = 1;
    __android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "color format is AV_PIX_FMT_YUV420P\n");
    d_c->pix_fmt = AV_PIX_FMT_YUV420P;
    d_c->codec_id = codec;
    d_c->codec_type = AVMEDIA_TYPE_VIDEO;
    //c->keyint_min = 1;
    //c->i_quant_factor = (float)0.71;
    //c->b_frame_strategy = 20;
    //c->qcompress = (float)0.6;
    //c->qmin = 20;
    //c->qmax = 51;
    //c->max_qdiff = 4;
    //c->refs = 4;
    //c->trellis = 1;
    //d_c->thread_count = 1;

    //AVDictionary *codec_options = NULL;
    //av_opt_set(d_c->priv_data, "x265-params", "profile=main:crf=43:vbv-bufsize=32:vbv-maxrate=32", 0);
    //av_opt_set(d_c->priv_data, "tune", "zerolatency", 0);
	if(codec == AV_CODEC_ID_H265) {
		char x265_params[100];
		sprintf(x265_params, "profile=main:crf=%d:vbv-bufsize=%d:vbv-maxrate=%d:rc-lookahead=0:bframes=0", crf, 2*bit_rate/1024, bit_rate/1024);
		av_opt_set(d_c->priv_data, "x265-params", x265_params, 0);
	}
    //av_opt_set(d_c->priv_data, "x265-params", "profile=main:crf=40:vbv-bufsize=32:vbv-maxrate=32:rc-lookahead=0:bframes=0:pass=2", 0);
    av_opt_set(d_c->priv_data, "preset", "ultrafast", 0);
    //av_opt_set(d_c->priv_data, "x265-params", "profile=main:crf=43:vbv-bufsize=32:vbv-maxrate=32");
 /* open it */
    if (avcodec_open2(d_c, d_codec, NULL) < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "Could not open codec\n");
        exit(1);
    }

    d_frame = av_frame_alloc();
    if (!d_frame) {
        __android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "Could not allocate video frame\n");
        exit(1);
    }
    d_frame->format = d_c->pix_fmt;
    d_frame->width  = d_c->width;
    d_frame->height = d_c->height;

    /*
    int size = avpicture_get_size(frame->format, frame->width, frame->height);
    buffer = (uint8_t*)av_malloc(size);
    avpicture_fill((AVPicture*)frame, buffer, frame->format, frame->width, frame->height);
    */
  }

/*
 * Class:     com_example_yj_myapplication_Codec
 * Method:    encode_frame
 * Signature: ([B)[B
 */

void NV21_YUV420P(const unsigned char* image_src, unsigned char* y, unsigned char* u, unsigned char* v, image_width, int image_height) {
    unsigned char* src_ptr = image_src;
    unsigned char* y_ptr = y;
    unsigned char* u_ptr = u;
    unsigned char* v_ptr = v;

    memcpy(y_ptr, image_src, image_width*image_height);
    src_ptr += image_width*image_height;
    for(int i=0; i<image_width*image_height/2; i+=2) {
        *v_ptr = *src_ptr;
        src_ptr++;
        v_ptr++;
        *u_ptr = *src_ptr;
        src_ptr++;
        u_ptr++;
    }
}
/*
void NV21_YUV420P(const unsigned char* image_src, unsigned char* image_dst, int image_width, int image_height) {
    unsigned char* p = image_dst;
    memcpy(p, image_src, image_width * image_height * 3 / 2);
    const unsigned char* pNV = image_src + image_width * image_height;
    unsigned char* pV = p + image_width * image_height;
    unsigned char* pU = p + image_width * image_height + ((image_width * image_height) >> 2);
    for(int i=0; i<(image_width*image_height)/2; i++) {
        if((i%2) == 0) *pV++ = *(pNV + i);
        else *pU++ = *(pNV + i);
    }
}
*/
JNIEXPORT jbyteArray JNICALL Java_com_example_hrdp_encoder_Codec_encode_1frame
  (JNIEnv *env, jobject jobj, jbyteArray data) {
    //__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "android_ndk_encode_1\n");
    jboolean isCopy;
    jbyte* rawBytes = (*env)->GetByteArrayElements(env, data, &isCopy);
    //unsigned char * image_dst = (unsigned char*)malloc(d_frame->width * d_frame->height * 3 / 2);
    //unsigned char* y = (unsigned char*)malloc(d_frame->width * d_frame->height);
    //unsigned char* u = (unsigned char*)malloc(d_frame->width * d_frame->height / 4);
    //unsigned char* v = (unsigned char*)malloc(d_frame->width * d_frame->height / 4);
    //NV21_YUV420P(rawBytes, y, u, v, d_frame->width, d_frame->height);
    //NV21_YUV420P(rawBytes, image_dst, d_frame->width, d_frame->height);
    //memcpy(frame->data[0], rawBytes, frame->linesize[0] * frame->height);
    //memcpy(frame->data[2], rawBytes + frame->linesize[0] * frame->height, frame->linesize[1] * frame->height / 2);
    //memcpy(frame->data[1], rawBytes + (frame->linesize[0] * frame->height + frame->linesize[1] * frame->height / 2), frame->linesize[2] * frame->height / 2);
    d_frame->linesize[0] = d_frame->width;
    d_frame->linesize[1] = d_frame->width / 2;
    d_frame->linesize[2] = d_frame->width / 2;

    //__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "%d %d %d %d\n",frame->linesize[0], frame->linesize[1], frame->linesize[2], frame->height);

    d_frame->data[0] = rawBytes;
    d_frame->data[1] = rawBytes + d_frame->linesize[0] * d_frame->height;
    d_frame->data[2] = rawBytes + d_frame->linesize[0] * d_frame->height + d_frame->linesize[1] * d_frame->height / 2;

    //__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "android_ndk_encode_2\n");

    int got_output;
    AVPacket pkt;
    av_init_packet(&pkt);
    pkt.data=NULL;
    pkt.size=0;

    d_frame->pts = av_frame_get_best_effort_timestamp(d_frame);
    //__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "timestamp disable\n");
    //__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "android_ndk_encode_3\n");
    int ret = avcodec_encode_video2(d_c, &pkt, d_frame, &got_output);
    //__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "android_ndk_encode_4\n");
    if(ret < 0) {
        (*env)->ReleaseByteArrayElements(env, data, rawBytes, 0);
        av_free_packet(&pkt);
	//free(y);
        //free(u);
        //free(v);
        //__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "error encoding frame\n");
        exit(1);
    }
    //free(y);
    //free(u);
    //free(v);
    //free(image_dst);
    if(got_output) {
        jbyteArray result = (*env)->NewByteArray(env, 17+pkt.size);
        (*env)->SetByteArrayRegion(env, result, 17, pkt.size, (jbyte*)pkt.data);

        (*env)->ReleaseByteArrayElements(env, data, rawBytes, 0);
        av_free_packet(&pkt);
        //__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "encoding packet is not null\n");
        return result;
    }
    else {
        (*env)->ReleaseByteArrayElements(env, data, rawBytes, 0);
        av_free_packet(&pkt);
        //__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "encoding packet is null\n");
        return (*env)->NewByteArray(env, 0);
    }
  }

/*
 * Class:     com_example_yj_myapplication_Codec
 * Method:    encode_release
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_example_hrdp_encoder_Codec_encode_1release
  (JNIEnv *a, jobject b) {
    if(buffer != NULL) {
        av_freep(&buffer);
        buffer = NULL;
    }

    if(d_frame != NULL) {
        av_frame_free(&d_frame);
        d_frame = NULL;
    }
    if(d_c != NULL) {
        avcodec_close(d_c);
        av_free(d_c);
        d_c = NULL;
    }
  }
