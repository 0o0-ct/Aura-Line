#pragma once
#include <cstdint>
typedef int jint; typedef float jfloat; typedef unsigned char jboolean; typedef int jsize;
#define JNI_TRUE 1
struct _jobject{}; typedef _jobject* jobject;
struct JNIEnv; typedef float* jfloatArray_t;
struct JNIEnv {
  float* data; int len;
  jsize GetArrayLength(void*){return len;}
  void* GetPrimitiveArrayCritical(void*, void*){return data;}
  void ReleasePrimitiveArrayCritical(void*, void*, int){}
};
typedef void* jfloatArray;
#define JNIEXPORT
#define JNICALL
