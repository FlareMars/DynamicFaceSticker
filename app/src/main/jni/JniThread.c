#include "com_flarejaven_example_jnithread_NdkJniUtils.h"
#include <jni.h>
#include <stdlib.h>
#include <pthread.h>
#include <android/log.h>
#include <unistd.h>

#include <time.h>
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswscale/swscale.h"
#include "libavutil/log.h"

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "native-activity", __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, "native-activity", __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "native-activity", __VA_ARGS__))

#define NUMTHREADS 1
#define ITEM_SIZE 2

JavaVM *g_jvm = NULL;
jobject g_obj = NULL;
int flag = 0;
pthread_t pt[NUMTHREADS];
pthread_cond_t cond;
pthread_mutex_t mutex;


int itemSize = 2; // 指明同时使用多少个贴纸
char input_strs[2][255];

// Output FFmpeg's av_log()
void custom_log(void *ptr, int level, const char* fmt, va_list vl) {
    FILE *fp=fopen("/storage/emulated/0/av_log.txt","a+");
    if(fp){
        vfprintf(fp, fmt, vl);
        fflush(fp);
        fclose(fp);
    }
}

void *thread_decode_video(void* arg) {
    AVFormatContext *pFormatCtxs[ITEM_SIZE];
    int videoIndexes[ITEM_SIZE] = {-1, -1};
    AVCodecContext *pCodecCtxs[ITEM_SIZE];
    AVCodec *pCodecs[ITEM_SIZE];
    AVFrame *pFrames[ITEM_SIZE], *pFrameRGBAs[ITEM_SIZE];
    jbyteArray arrays[ITEM_SIZE];
    uint8_t *out_buffers[ITEM_SIZE];
    AVPacket *packets[ITEM_SIZE];
    struct SwsContext *img_convert_ctxs[ITEM_SIZE];
    int byteSizeses[ITEM_SIZE];
    int ret, got_picture;
    int i, j;

    JNIEnv *env;
    jclass cls;
    jmethodID mid;

    if((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
        LOGE("%s: AttachCurrentThread() failed", __FUNCTION__);
        return NULL;
    }

    cls = (*env)->GetObjectClass(env, g_obj);
    if (cls == NULL) {
        LOGE("FindClass() Error.....");
    }

    mid = (*env)->GetMethodID(env, cls, "callback", "([BIII)V");
    if (mid == NULL) {
        LOGE("GetMethodID() Error.....");
    }

    av_log_set_callback(custom_log);
    av_register_all();
    avformat_network_init();

    for (i = 0; i < itemSize; i++) {
        pFormatCtxs[i] = avformat_alloc_context();

        if(avformat_open_input(&pFormatCtxs[i], input_strs[i], NULL, NULL) != 0) {
            LOGE("Couldn't open input stream.\n");
            return -1;
        }
        if(avformat_find_stream_info(pFormatCtxs[i], NULL) < 0) {
            LOGE("Couldn't find stream information.\n");
            return -1;
        }

        for (j = 0; j < pFormatCtxs[i]->nb_streams; j++) {
            if(pFormatCtxs[i]->streams[j]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
                videoIndexes[i] = j;
                break;
            }
        }
        if (videoIndexes[i] == -1) {
            LOGE("Couldn't find a video stream.\n");
            return -1;
        }

        pCodecCtxs[i] = pFormatCtxs[i]->streams[videoIndexes[i]]->codec;

        pCodecs[i] = avcodec_find_decoder(pCodecCtxs[i]->codec_id);
        if (pCodecs[i] == NULL) {
            LOGE("Couldn't find Codec.\n");
            return -1;
        }
        if (avcodec_open2(pCodecCtxs[i], pCodecs[i], NULL) < 0) {
            LOGE("Couldn't open codec.\n");
            return -1;
        }

        pFrames[i] = av_frame_alloc();
        pFrameRGBAs[i] = av_frame_alloc();
        out_buffers[i] = (unsigned char *)av_malloc(av_image_get_buffer_size(AV_PIX_FMT_RGBA, pCodecCtxs[i]->width, pCodecCtxs[i]->height, 1));
        av_image_fill_arrays(pFrameRGBAs[i]->data, pFrameRGBAs[i]->linesize, out_buffers[i],
            AV_PIX_FMT_RGBA, pCodecCtxs[i]->width, pCodecCtxs[i]->height, 1);
        LOGI("%d, %d", pCodecCtxs[i]->width, pCodecCtxs[i]->height);
        byteSizeses[i] = pCodecCtxs[i]->width * pCodecCtxs[i]->height * 4;
        arrays[i] = (*env)->NewByteArray(env, byteSizeses[i]);
        packets[i] = (AVPacket *)av_malloc(sizeof(AVPacket));

        img_convert_ctxs[i] = sws_getContext(pCodecCtxs[i]->width, pCodecCtxs[i]->height, pCodecCtxs[i]->pix_fmt,
            pCodecCtxs[i]->width, pCodecCtxs[i]->height, AV_PIX_FMT_RGBA, SWS_BICUBIC, NULL, NULL, NULL);

    }

    struct timeval now;
    struct timespec outtime;
    pthread_mutex_lock(&mutex);
    while (flag == 1) {
        for (i = 0; i < itemSize; i++) {
            if (av_read_frame(pFormatCtxs[i], packets[i]) >= 0) {
                if (packets[i]->stream_index == videoIndexes[i]) {
                    ret = avcodec_decode_video2(pCodecCtxs[i], pFrames[i], &got_picture, packets[i]);
                    if (ret < 0) {
                        LOGE("Decode Error.\n");
                        return -1;
                    }
                    if (got_picture) {
                        sws_scale(img_convert_ctxs[i], (const uint8_t* const*)pFrames[i]->data, pFrames[i]->linesize, 0, pCodecCtxs[i]->height,
                            pFrameRGBAs[i]->data, pFrameRGBAs[i]->linesize);

                        (*env)->SetByteArrayRegion(env, arrays[i], 0, byteSizeses[i], pFrameRGBAs[i]->data[0]);
                        (*env)->CallVoidMethod(env, g_obj, mid, arrays[i], (jint)pCodecCtxs[i]->width, (jint)pCodecCtxs[i]->height, (jint)i);
                    }
                }
                av_free_packet(packets[i]);
            } else {
        //            LOGI("video reset!");
                av_seek_frame(pFormatCtxs[i], videoIndexes[i], 0, AVSEEK_FLAG_BACKWARD);
                avcodec_flush_buffers(pFormatCtxs[i]->streams[videoIndexes[i]]->codec);
            }
        }

        gettimeofday(&now, NULL);
        outtime.tv_sec = now.tv_sec + 0; // 延时0秒
        outtime.tv_nsec = now.tv_usec * 1000 + 50 * 1000000; // 延时50毫秒
        pthread_cond_timedwait(&cond, &mutex, &outtime);
    }
    pthread_mutex_unlock(&mutex);

    for (i = 0; i < itemSize; i++) {
        sws_freeContext(img_convert_ctxs[i]);
        av_frame_free(&pFrameRGBAs[i]);
        av_frame_free(&pFrames[i]);
        avcodec_close(pCodecCtxs[i]);
        avformat_close_input(&pFormatCtxs[i]);
        (*env)->DeleteLocalRef(env, arrays[i]);
    }

    pthread_join(pt[(int)arg], NULL);

    if ((*g_jvm)->DetachCurrentThread(g_jvm) != JNI_OK) {
        LOGE("%s: DetachCurrentThread() failed", __FUNCTION__);
    }

    pthread_exit(0);
}

JNIEXPORT jstring JNICALL Java_com_flarejaven_example_jnithread_NdkJniUtils_startThread(JNIEnv * env, jobject obj) {
    Java_com_flarejaven_example_jnithread_NdkJniUtils_endThread(env, obj);

    flag = 1;
    int i;
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&cond, NULL);
    for (i = 0; i < NUMTHREADS; i++) {
        pthread_create(&pt[i], NULL, &thread_decode_video, (void *)i);
    }
}

JNIEXPORT jstring JNICALL Java_com_flarejaven_example_jnithread_NdkJniUtils_endThread(JNIEnv * env, jobject obj) {
    if (flag == 1) {
        pthread_mutex_lock(&mutex);
        flag = 0;
        pthread_cond_signal(&cond);
        pthread_mutex_unlock(&mutex);
    }
}

JNIEXPORT void JNICALL Java_com_flarejaven_example_jnithread_NdkJniUtils_setJNIEnv(JNIEnv * env, jobject obj) {
    (*env)->GetJavaVM(env, &g_jvm);
    g_obj = (*env)->NewGlobalRef(env, obj);
}

JNIEXPORT void JNICALL Java_com_flarejaven_example_jnithread_NdkJniUtils_configStickerNames(JNIEnv * env, jobject obj, jobjectArray stickerNames, jint size) {
    int i;
    char* stickerName;
    itemSize = (int)size;
    LOGI("itemSize = %d", itemSize);
    jobject *objs;
    for (int i = 0; i < itemSize; i++) {
        objs = (*env)->GetObjectArrayElement(env, stickerNames, i);
        stickerName = (*env)->GetStringUTFChars(env, (jstring)objs, NULL);
        LOGI("stickerName for #%d = %s", i, stickerName);
        sprintf(input_strs[i], "%s", stickerName);
        (*env)->ReleaseStringUTFChars(env, objs, stickerName);
        (*env)->DeleteLocalRef(env, objs);
    }
}