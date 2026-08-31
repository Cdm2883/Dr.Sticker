#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "libavutil/log.h"

int run_ffmpeg(int argc, char** argv);

extern int nb_input_files;
extern int nb_output_files;
extern int nb_filtergraphs;
extern int nb_decoders;

static void ffmpeg_reset(void)
{
    nb_input_files = 0;
    nb_output_files = 0;
    nb_filtergraphs = 0;
    nb_decoders = 0;
}

// ReSharper disable CppParameterMayBeConst
static void avlog_to_logcat(void* ptr, int level, const char* fmt, va_list vl)
// ReSharper restore CppParameterMayBeConst
{
    android_LogPriority priority;
    if (level > AV_LOG_INFO)
        return;
    if (level >= AV_LOG_ERROR)
        priority = ANDROID_LOG_ERROR;
    else if (level >= AV_LOG_WARNING)
        priority = ANDROID_LOG_WARN;
    else
        priority = ANDROID_LOG_INFO;

    char line[1024];
    int prefix = 1;
    av_log_format_line2(ptr, level, fmt, vl, line, sizeof(line), &prefix);
    __android_log_print(priority, "FFmpeg", "%s", line);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
    (void)vm;
    (void)reserved;
    av_log_set_callback(avlog_to_logcat);
    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL
Java_vip_cdms_drsticker_rule_utils_FFmpegCli_run0(
    JNIEnv* env,
    const jobject clazz,
    const jobjectArray jargs)
{
    (void)clazz;
    const jsize argc = (*env)->GetArrayLength(env, jargs);
    char** argv = calloc((size_t)argc + 1, sizeof(*argv));
    if (argv == NULL)
    {
        __android_log_print(ANDROID_LOG_ERROR, "FFmpeg", "out of memory");
        return 1;
    }

    argv[0] = (char*)"ffmpeg";
    for (jsize i = 0; i < argc; i++)
    {
        const jstring jstr = (*env)->GetObjectArrayElement(env, jargs, i);
        const char* str = (*env)->GetStringUTFChars(env, jstr, NULL);
        if (str != NULL) argv[i + 1] = strdup(str);
        (*env)->ReleaseStringUTFChars(env, jstr, str);
        (*env)->DeleteLocalRef(env, jstr);
    }

    const jint code = run_ffmpeg(argc + 1, argv);
    ffmpeg_reset();

    for (jsize i = 1; i <= argc; i++) free(argv[i]);
    free(argv);
    return code;
}
