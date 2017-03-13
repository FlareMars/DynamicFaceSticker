LOCAL_PATH := $(call my-dir) # Android.mk 所在位置,即jni目录

include $(CLEAR_VARS)
LOCAL_MODULE := ijkffmpeg
LOCAL_SRC_FILES := libijkffmpeg.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := jni-thread
LOCAL_SRC_FILES := JniThread.c
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_LDLIBS :=-llog
LOCAL_SHARED_LIBRARIES := ijkffmpeg

include $(BUILD_SHARED_LIBRARY)
