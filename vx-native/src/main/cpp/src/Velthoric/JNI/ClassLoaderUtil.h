/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#pragma once
#include <jni.h>

namespace Velthoric {

/**
 * @brief Utility for classloader-aware JNI operations.
 * 
 * Provides a way to load Java classes using a captured ClassLoader, 
 * bypassing visibility issues when the native layer resides in a 
 * different classloader layer (e.g. NeoForge bootstrap).
 */
class ClassLoaderUtil {
public:
    /**
     * @brief Stores a global reference to the specified ClassLoader.
     * @param env JNI environment.
     * @param classLoader The ClassLoader to use for FindClass operations.
     */
    static void SetClassLoader(JNIEnv* env, jobject classLoader);

    /**
     * @brief Finds a class using the captured ClassLoader.
     * 
     * If no ClassLoader is captured, it falls back to the default env->FindClass().
     * The className should be in the format "path/to/Class" (slashes, not dots).
     * 
     * @param env JNI environment.
     * @param className The binary name of the class.
     * @return jclass Local reference to the class, or NULL if not found.
     */
    static jclass FindClass(JNIEnv* env, const char* className);

    /**
     * @brief Clears the global ClassLoader reference.
     */
    static void ClearClassLoader(JNIEnv* env);

private:
    static jobject s_ClassLoader;
    static jmethodID s_LoadClassMethod;
};

} // namespace Velthoric