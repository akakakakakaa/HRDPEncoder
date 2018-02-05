#ifndef bgfg_vibe_hpp
#define bgfg_vibe_hpp
#include <jni.h>
#include <vector>
#include <math.h>
#include <android/log.h>
#include "opencv2/core/core.hpp"
#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/opencv_modules.hpp"

struct Model {
    cv::Mat*** samples;
    cv::Mat** fgch;
    cv::Mat* fg;
};

class bgfg_vibe
{
#define rndSize 256
    unsigned char ri;
#define rdx ri++
public:
    bgfg_vibe();
    int N,R,noMin,phi;
    void init_model(cv::Mat& firstSample);
    void setphi(int phi);
    cv::Mat* fg(cv::Mat& frame);
private:
    bool initDone;
    cv::RNG rnd;
    Model* model;
    void init();
    void clear(int channelsSize);
    void fg1ch(cv::Mat& frame,cv::Mat** samples,cv::Mat* fg);
    int rndp[rndSize],rndn[rndSize],rnd8[rndSize];
};

extern "C" {
JNIEXPORT void JNICALL Java_com_example_hrdp_encoder_BgfgVibe_init
  (JNIEnv *, jobject, jbyteArray, jint, jint);

JNIEXPORT void JNICALL Java_com_example_hrdp_encoder_BgfgVibe_detect
  (JNIEnv *, jobject, jbyteArray, jint, jint, jlong);
}
#endif
