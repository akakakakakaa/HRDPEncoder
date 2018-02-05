#include "BgfgVibe.h"

bgfg_vibe bgfg;
JNIEXPORT void JNICALL Java_com_example_hrdp_encoder_BgfgVibe_init
  (JNIEnv *env, jobject obj, jbyteArray initData, jint width, jint height) {
	jboolean isCopy;
	jbyte* rawBytes = env->GetByteArrayElements(initData, &isCopy);
	
	cv::Mat frame(height + height/2, width, CV_8UC1, rawBytes);
	cvtColor(frame, frame, 	cv::COLOR_YUV2GRAY_YV12);
	bgfg.init_model(frame);
	frame.release();
	env->ReleaseByteArrayElements(initData, rawBytes, 0);
}

JNIEXPORT void JNICALL Java_com_example_hrdp_encoder_BgfgVibe_detect
  (JNIEnv *env, jobject obj, jbyteArray data, jint width, jint height, jlong result) {
        jboolean isCopy;
        jbyte* rawBytes = env->GetByteArrayElements(data, &isCopy);

        cv::Mat frameMat(height + height/2, width, CV_8UC1, rawBytes);
        cvtColor(frameMat, frameMat, cv::COLOR_YUV2GRAY_YV12);

	cv::Mat fg = *bgfg.fg(frameMat);
	frameMat.release();	
	
	//__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "bgfg fg end\n");
	morphologyEx(fg, fg, cv::MORPH_CLOSE, cv::Mat(3, 3, CV_8U));

	std::vector<std::vector<cv::Point> > contours;
	std::vector<cv::Vec4i> hierarchy;
	//__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "findContours start\n");
	findContours( fg, contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE, cv::Point(0, 0) );
	//__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "findContours end\n");	

	double minX;
	double minY;
	double maxX;
	double maxY;
	bool check = false;
	std::vector<cv::Rect> rectList;
	for(int i=0; i<contours.size(); i++) {
		cv::Rect rect = boundingRect(contours[i]);
		if(rect.width*rect.height >= 100 && rect.width*rect.height <= 20000)
			rectList.push_back(rect);
	}
	//__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "contour to rect end %d\n", rectList.size());	

	if(rectList.size() != 0) {
		for (int i = 0; i < rectList.size() - 1; i++) {
			cv::Rect rect = rectList[i];
			for (int j = i+1; j < rectList.size(); j++) {
				cv::Rect rect2 = rectList[j];

				double x_overlap = std::max(0, std::min(rect.br().x, rect2.br().x) - std::max(rect.tl().x, rect2.tl().x));
				double y_overlap = std::max(0, std::min(rect.br().y, rect2.br().y) - std::max(rect.tl().y, rect2.tl().y));

				cv::Point center((int) (rect.tl().x + rect.br().x / 2), (int) (rect.tl().y + rect.br().y / 2));
				cv::Point center2((int) (rect2.tl().x + rect2.br().x / 2), (int) (rect2.tl().y + rect2.br().y / 2));
				double distance = sqrt(pow((double)center.x - center2.x, 2) + pow((double)center.y - center2.y, 2));
				if ((x_overlap != 0 && y_overlap != 0) || distance <= 100) {
					minX = std::min(rect2.tl().x, rect.tl().x);
					minY = std::min(rect2.tl().y, rect.tl().y);
					maxX = std::max(rect2.br().x, rect.br().x);
					maxY = std::max(rect2.br().y, rect.br().y);
					cv::Rect obj2 (cv::Point(minX, minY), cv::Point(maxX, maxY));
					//__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "%d %d", i, j);
					rectList.erase(rectList.begin() + j);
					rectList.erase(rectList.begin() + i);
					rectList.push_back(obj2);
					i=0;
					break;
				}
			}
		}
	//__android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "find rect end\n");
	
		for(int i=0; i<rectList.size()-1; i++) {
			cv::Rect rect = rectList[i];
			for(int j=i+1; j<rectList.size(); j++) {
				cv::Rect rect2 = rectList[j];
				if(rect2.width*rect2.height > rect.width*rect.height) {
					iter_swap(rectList.begin() + i, rectList.begin() + j);
				}
			}
		}
		cv::Mat* resultMat = (cv::Mat*)result;
		cv::Mat(rectList, true).copyTo(*resultMat);	
	}
	env->ReleaseByteArrayElements(data, rawBytes, 0);
}

bgfg_vibe::bgfg_vibe():R(20),N(20),noMin(1),phi(0)
{
    initDone=false;
    rnd=cv::theRNG();
    ri=0;
}
void bgfg_vibe::init()
{
    for(int i=0;i<rndSize;i++)
    {
        rndp[i]=rnd(phi);
        rndn[i]=rnd(N);
        rnd8[i]=rnd(8);
    }
    model = NULL;
}
void bgfg_vibe::setphi(int phi)
{
    this->phi=phi;
    for(int i=0;i<rndSize;i++)
    {
        rndp[i]=rnd(phi);
    }
}
void bgfg_vibe::init_model(cv::Mat& firstSample)
{
    std::vector<cv::Mat> channels;
    split(firstSample,channels);
    if(!initDone)
    {
        init();
        initDone=true;
    }   

    clear(channels.size());
    model=new Model;
    model->fgch= new cv::Mat*[channels.size()];
    model->samples=new cv::Mat**[N];
    model->fg=new cv::Mat(cv::Size(firstSample.cols,firstSample.rows), CV_8UC1);
    for(size_t s=0;s<channels.size();s++)
    {       
        model->fgch[s]=new cv::Mat(cv::Size(firstSample.cols,firstSample.rows), CV_8UC1);
        cv::Mat** samples= new cv::Mat*[N];
        for(int i=0;i<N;i++)
        {
            samples[i]= new cv::Mat(cv::Size(firstSample.cols,firstSample.rows), CV_8UC1);
        }
        for(int i=0;i<channels[s].rows;i++)
        {
            int ioff=channels[s].step.p[0]*i;
            for(int j=0;j<channels[0].cols;j++)
            {
                for(int k=0;k<1;k++)
                {
                    (samples[k]->data + ioff)[j]=channels[s].at<uchar>(i,j);
                }
                (model->fgch[s]->data + ioff)[j]=0;

                if(s==0)(model->fg->data + ioff)[j]=0;
            }
        }
        model->samples[s]=samples;
    }
}

void bgfg_vibe::clear(int channelsSize) {
	if(model != NULL) {
		for(size_t s=0;s<channelsSize;s++)
		{	
			delete model->fgch[s];
        		for(int i=0;i<N;i++)
        		{
          	  		delete model->samples[s][i];
        		}
			delete[] model->samples[s];
            	}
		delete[] model->fgch;
		delete[] model->samples;
		delete model->fg;
		delete model;
		model = NULL;
	}
}

void bgfg_vibe::fg1ch(cv::Mat& frame,cv::Mat** samples,cv::Mat* fg)
{
    int step=frame.step.p[0];
    for(int i=1;i<frame.rows-1;i++)
    {
        int ioff= step*i;
        for(int j=1;j<frame.cols-1;j++)
        {
            int count =0,index=0;
            while((count<noMin) && (index<N))
            {
                int dist= (samples[index]->data + ioff)[j]-(frame.data + ioff)[j];
                if(dist<=R && dist>=-R)
                {
                    count++; 
                }
                index++;
            }
            if(count>=noMin)
            {
                ((fg->data + ioff))[j]=0;
                int rand= rndp[rdx];
                if(rand==0)
                {
                    rand= rndn[rdx];
                    (samples[rand]->data + ioff)[j]=(frame.data + ioff)[j];
                }
                rand= rndp[rdx];
                int nxoff=ioff;
                if(rand==0)
                {
                    int nx=i,ny=j;
                    int cases= rnd8[rdx];
                    switch(cases)
                    {
                    case 0:
                        //nx--;
                        nxoff=ioff-step;
                        ny--;
                        break;
                    case 1:
                        //nx--;
                        nxoff=ioff-step;
                        ny;
                        break;
                    case 2:
                        //nx--;
                        nxoff=ioff-step;
                        ny++;
                        break; 
                    case 3:
                        //nx++;
                        nxoff=ioff+step;
                        ny--;
                        break; 
                    case 4:
                        //nx++;
                        nxoff=ioff+step;
                        ny;
                        break; 
                    case 5:
                        //nx++;
                        nxoff=ioff+step;
                        ny++;
                        break; 
                    case 6:
                        //nx;
                        ny--;
                        break; 
                    case 7:
                        //nx;
                        ny++;
                        break; 
                    }
                    rand= rndn[rdx];
                    (samples[rand]->data + nxoff)[ny]=(frame.data + ioff)[j];
                }
            }else
            {
                ((fg->data + ioff))[j]=255;
            }
        }
    }
}
cv::Mat* bgfg_vibe::fg(cv::Mat& frame)
{
    std::vector<cv::Mat> channels;
    split(frame,channels);
    for(size_t i=0;i<channels.size();i++)
    {
        fg1ch(channels[i],model->samples[i],model->fgch[i]);        
        if(i>0 && i<2)
        {
            bitwise_or(*model->fgch[i-1],*model->fgch[i],*model->fg);
        }
        if(i>=2)
        {
            bitwise_or(*model->fg,*model->fgch[i],*model->fg);
        }
    }
    if(channels.size()==1) return model->fgch[0];
    return model->fg;
}
