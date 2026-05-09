/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "Velthoric/JNI/ClassLoaderUtil.h"
#include <string>
#include <algorithm>

namespace Velthoric {

jobject ClassLoaderUtil::s_ClassLoader = nullptr;
jmethodID ClassLoaderUtil::s_LoadClassMethod = nullptr;

void ClassLoaderUtil::SetClassLoader(JNIEnv* env, jobject classLoader) {
    if (s_ClassLoader) {
        env->DeleteGlobalRef(s_ClassLoader);
    }
    
    if (classLoader) {
        s_ClassLoader = env->NewGlobalRef(classLoader);
        
        jclass classLoaderClass = env->GetObjectClass(s_ClassLoader);
        s_LoadClassMethod = env->GetMethodID(classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        env->DeleteLocalRef(classLoaderClass);
    } else {
        s_ClassLoader = nullptr;
        s_LoadClassMethod = nullptr;
    }
}

jclass ClassLoaderUtil::FindClass(JNIEnv* env, const char* className) {
    if (!s_ClassLoader || !s_LoadClassMethod) {
        return env->FindClass(className);
    }

    // Convert "path/to/Class" to "path.to.Class" for loadClass()
    std::string dottedName(className);
    std::replace(dottedName.begin(), dottedName.end(), '/', '.');
    
    jstring jName = env->NewStringUTF(dottedName.c_str());
    jclass cls = (jclass)env->CallObjectMethod(s_ClassLoader, s_LoadClassMethod, jName);
    env->DeleteLocalRef(jName);

    return cls;
}

void ClassLoaderUtil::ClearClassLoader(JNIEnv* env) {
    if (s_ClassLoader) {
        env->DeleteGlobalRef(s_ClassLoader);
        s_ClassLoader = nullptr;
        s_LoadClassMethod = nullptr;
    }
}

} // namespace Velthoric

extern "C" {

/**
 * @brief JNI Bridge: Captures the mod's classloader for future use.
 */
JNIEXPORT void JNICALL
Java_net_xmx_velthoric_natives_systems_NativeManager_nSetClassLoader(JNIEnv *env, jclass clazz, jobject classLoader) {
    (void)clazz;
    Velthoric::ClassLoaderUtil::SetClassLoader(env, classLoader);
}

}