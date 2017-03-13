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

JavaVM *g_jvm = NULL;
jobject g_obj = NULL;
int flag = 0;
pthread_t pt[NUMTHREADS];
pthread_cond_t cond;
pthread_mutex_t mutex;

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
    AVFormatContext *pFormatCtx;
    int videoIndex;
    AVCodecContext *pCodecCtx;
    AVCodec *pCodec;
    AVFrame *pFrame, *pFrameRGBA;
    jbyteArray array;
    uint8_t *out_buffer;
    AVPacket *packet;
    struct SwsContext *img_convert_ctx;
    int frame_cnt;
    int byteSizes;
    char input_str[500] = {0};
    int ret, got_picture;
    int i;

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

    sprintf(input_str, "%s", "/storage/emulated/0/F_bunny.flv");
//    sprintf(input_str, "%s", "/storage/emulated/0/B_vegetable.flv");

    av_log_set_callback(custom_log);
    av_register_all();
    avformat_network_init();

    pFormatCtx = avformat_alloc_context();

    if(avformat_open_input(&pFormatCtx, input_str, NULL, NULL) != 0) {
        LOGE("Couldn't open input stream.\n");
        return -1;
    }
    if(avformat_find_stream_info(pFormatCtx, NULL) < 0) {
        LOGE("Couldn't find stream information.\n");
        return -1;
    }

    videoIndex = -1;
    for (i = 0; i < pFormatCtx->nb_streams; i++) {
        if(pFormatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoIndex = i;
            break;
        }
    }
    if(videoIndex == -1) {
        LOGE("Couldn't find a video stream.\n");
        return -1;
    }

    pCodecCtx = pFormatCtx->streams[videoIndex]->codec;

    pCodec = avcodec_find_decoder(pCodecCtx->codec_id);
    if (pCodec == NULL) {
        LOGE("Couldn't find Codec.\n");
        return -1;
    }
    if (avcodec_open2(pCodecCtx, pCodec, NULL) < 0) {
        LOGE("Couldn't open codec.\n");
        return -1;
    }

    pFrame = av_frame_alloc();
    pFrameRGBA = av_frame_alloc();
    out_buffer = (unsigned char *)av_malloc(av_image_get_buffer_size(AV_PIX_FMT_RGBA, pCodecCtx->width, pCodecCtx->height, 1));
    av_image_fill_arrays(pFrameRGBA->data, pFrameRGBA->linesize, out_buffer,
        AV_PIX_FMT_RGBA, pCodecCtx->width, pCodecCtx->height, 1);
    LOGI("%d, %d", pCodecCtx->width, pCodecCtx->height);
    byteSizes = pCodecCtx->width * pCodecCtx->height * 4;
    array = (*env)->NewByteArray(env, byteSizes);
    packet = (AVPacket *)av_malloc(sizeof(AVPacket));

    img_convert_ctx = sws_getContext(pCodecCtx->width, pCodecCtx->height, pCodecCtx->pix_fmt,
        pCodecCtx->width, pCodecCtx->height, AV_PIX_FMT_RGBA, SWS_BICUBIC, NULL, NULL, NULL);

    frame_cnt = 0;
    struct timeval now;
    struct timespec outtime;
    pthread_mutex_lock(&mutex);
    while (flag == 1) {
        if (av_read_frame(pFormatCtx, packet) >= 0) {
            if (packet->stream_index == videoIndex) {
                ret = avcodec_decode_video2(pCodecCtx, pFrame, &got_picture, packet);
                if (ret < 0) {
                    LOGE("Decode Error.\n");
                    return -1;
                }
                if (got_picture) {
                    sws_scale(img_convert_ctx, (const uint8_t* const*)pFrame->data, pFrame->linesize, 0, pCodecCtx->height,
                        pFrameRGBA->data, pFrameRGBA->linesize);
                    frame_cnt++;
//                    LOGI("frame #%d", frame_cnt);

                    (*env)->SetByteArrayRegion(env, array, 0, byteSizes, pFrameRGBA->data[0]);
                    (*env)->CallVoidMethod(env, g_obj, mid, array, (jint)pCodecCtx->width, (jint)pCodecCtx->height, (jint)0);
                }
            }
            av_free_packet(packet);
        } else {
//            LOGI("video reset!");
            frame_cnt = 0;
            av_seek_frame(pFormatCtx, videoIndex, 0, AVSEEK_FLAG_BACKWARD);
            avcodec_flush_buffers(pFormatCtx->streams[videoIndex]->codec);
        }

        gettimeofday(&now, NULL);
        outtime.tv_sec = now.tv_sec + 0; // 延时0秒
        outtime.tv_nsec = now.tv_usec * 1000 + 30 * 1000000; // 延时30毫秒
        pthread_cond_timedwait(&cond, &mutex, &outtime);
    }
    pthread_mutex_unlock(&mutex);

    sws_freeContext(img_convert_ctx);
    av_frame_free(&pFrameRGBA);
    av_frame_free(&pFrame);
    avcodec_close(pCodecCtx);
    avformat_close_input(&pFormatCtx);
    (*env)->DeleteLocalRef(env, array);

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