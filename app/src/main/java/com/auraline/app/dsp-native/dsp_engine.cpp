#include <jni.h>
#include <string>
#include <cmath>
#include <vector>

extern "C" JNIEXPORT jstring JNICALL
Java_com_auraline_dsp_1native_DspEngine_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "AuraLine DSP nativo (Oboe, C++17, NEON)";
    return env->NewStringUTF(hello.c_str());
}
