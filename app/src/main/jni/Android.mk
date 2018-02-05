# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

OPENCVROOT:= /root/OpenCV-2.4.10-android-sdk
OPENCV_CAMERA_MODULES:=off
OPENCV_INSTALL_MODULES:=on
OPENCV_LIB_TYPE:=STATIC
include ${OPENCVROOT}/sdk/native/jni/OpenCV.mk

LOCAL_MODULE := ndkTest
LOCAL_SRC_FILES := encoder.c BgfgVibe.cpp
LOCAL_LDLIBS := -llog -lz -landroid
#순서 안맞으면은 안됨. libavutil이 avcodec 앞에 있으니까 안되더라
LOCAL_STATIC_LIBRARIES += libavformat libavcodec libx265 libopenh264 libavutil
include $(BUILD_SHARED_LIBRARY)
$(call import-add-path, /root/Downloads/ffmpeg-3.3)
$(call import-module, build_encoder)
$(call import-add-path, /root/Downloads/x265_2.3)
$(call import-module, build_x265)
$(call import-add-path, /root/Downloads)
$(call import-module, openh264-1.4.0-make)

