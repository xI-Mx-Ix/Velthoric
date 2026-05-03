cmake_minimum_required(VERSION 3.15)
project(vxnative)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(MSVC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

# Configuration
if(NOT JOLT_JNI_VERSION)
    message(FATAL_ERROR "JOLT_JNI_VERSION not set! Please pass it via -DJOLT_JNI_VERSION=...")
endif()

set(RELEASE_URL "https://github.com/xI-Mx-Ix/vx-jolt-jni/releases/download/${JOLT_JNI_VERSION}")

# Directories for external dependencies
set(EXTERNAL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/external")
set(INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/include")
file(MAKE_DIRECTORY ${EXTERNAL_DIR})
file(MAKE_DIRECTORY ${INCLUDE_DIR})

# Header Download & Extraction
set(HEADERS_ZIP "${EXTERNAL_DIR}/jolt-headers.zip")
if(NOT EXISTS "${INCLUDE_DIR}/Jolt/Jolt.h")
    message(STATUS "Downloading Jolt headers from ${RELEASE_URL}...")
    file(DOWNLOAD "${RELEASE_URL}/jolt-headers-${JOLT_JNI_VERSION}.zip" "${HEADERS_ZIP}" SHOW_PROGRESS)
    
    message(STATUS "Extracting headers to ${INCLUDE_DIR}...")
    execute_process(COMMAND ${CMAKE_COMMAND} -E tar xf "${HEADERS_ZIP}" WORKING_DIRECTORY "${INCLUDE_DIR}")
endif()

# Native Library Download
if(WIN32)
    set(LIB_FILENAME "joltjni-${JOLT_JNI_VERSION}-windows-x64-ReleaseDp")
    set(DLL_NAME "${LIB_FILENAME}.dll")
    set(LIB_NAME "${LIB_FILENAME}.lib")
    
    if(NOT EXISTS "${EXTERNAL_DIR}/${DLL_NAME}")
        message(STATUS "Downloading ${DLL_NAME}...")
        file(DOWNLOAD "${RELEASE_URL}/${DLL_NAME}" "${EXTERNAL_DIR}/${DLL_NAME}" SHOW_PROGRESS)
    endif()
    if(NOT EXISTS "${EXTERNAL_DIR}/${LIB_NAME}")
        message(STATUS "Downloading ${LIB_NAME}...")
        file(DOWNLOAD "${RELEASE_URL}/${LIB_NAME}" "${EXTERNAL_DIR}/${LIB_NAME}" SHOW_PROGRESS)
    endif()

    add_library(joltjni SHARED IMPORTED)
    set_target_properties(joltjni PROPERTIES
        IMPORTED_LOCATION "${EXTERNAL_DIR}/${DLL_NAME}"
        IMPORTED_IMPLIB "${EXTERNAL_DIR}/${LIB_NAME}"
        INTERFACE_INCLUDE_DIRECTORIES "${INCLUDE_DIR}"
    )
elseif(APPLE)
    # macOS handling
    if(CMAKE_OSX_ARCHITECTURES)
        if(CMAKE_OSX_ARCHITECTURES MATCHES "arm64")
            set(ARCH "arm64")
        else()
            set(ARCH "x64")
        endif()
    elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
        set(ARCH "arm64")
    else()
        set(ARCH "x64")
    endif()
    set(DYLIB_NAME "joltjni-${JOLT_JNI_VERSION}-macos-${ARCH}-ReleaseDp.dylib")
    if(NOT EXISTS "${EXTERNAL_DIR}/${DYLIB_NAME}")
        file(DOWNLOAD "${RELEASE_URL}/${DYLIB_NAME}" "${EXTERNAL_DIR}/${DYLIB_NAME}" SHOW_PROGRESS)
    endif()
    add_library(joltjni SHARED IMPORTED)
    set_target_properties(joltjni PROPERTIES
        IMPORTED_LOCATION "${EXTERNAL_DIR}/${DYLIB_NAME}"
        INTERFACE_INCLUDE_DIRECTORIES "${INCLUDE_DIR}"
    )
else()
    # Linux handling — CMAKE_SYSTEM_PROCESSOR is set by the toolchain or -D flag for cross-builds
    if(CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
        set(ARCH "arm64")
    else()
        set(ARCH "x64")
    endif()
    set(SO_NAME "joltjni-${JOLT_JNI_VERSION}-linux-${ARCH}-ReleaseDp.so")
    if(NOT EXISTS "${EXTERNAL_DIR}/${SO_NAME}")
        file(DOWNLOAD "${RELEASE_URL}/${SO_NAME}" "${EXTERNAL_DIR}/${SO_NAME}" SHOW_PROGRESS)
    endif()
    add_library(joltjni SHARED IMPORTED)
    set_target_properties(joltjni PROPERTIES
        IMPORTED_LOCATION "${EXTERNAL_DIR}/${SO_NAME}"
        INTERFACE_INCLUDE_DIRECTORIES "${INCLUDE_DIR}"
    )
endif()

# JNI Setup
if(CMAKE_CROSSCOMPILING AND DEFINED ENV{JAVA_HOME})
    set(JNI_INCLUDE_DIRS "$ENV{JAVA_HOME}/include")
    # Add the platform-specific subdirectory
    if(EXISTS "$ENV{JAVA_HOME}/include/linux")
        list(APPEND JNI_INCLUDE_DIRS "$ENV{JAVA_HOME}/include/linux")
    elseif(EXISTS "$ENV{JAVA_HOME}/include/darwin")
        list(APPEND JNI_INCLUDE_DIRS "$ENV{JAVA_HOME}/include/darwin")
    endif()
    set(JNI_LIBRARIES "")
else()
    find_package(JNI REQUIRED)
endif()

# Define vxnative Library
add_library(vxnative SHARED
    src/Velthoric/PhysicsSync/BatchPhysicsSync.cpp
    src/Velthoric/Terrain/TerrainGenerator.cpp
    src/Velthoric/Terrain/Interaction/TerrainInteraction.cpp
    src/Velthoric/Terrain/Contact/TerrainContactListener.cpp
)

target_include_directories(vxnative PRIVATE 
    ${JNI_INCLUDE_DIRS}
    ${INCLUDE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/generated
)

target_compile_definitions(vxnative PRIVATE 
    JPH_DOUBLE_PRECISION 
    JPH_SHARED_LIBRARY
    JPH_OBJECT_STREAM
    JPH_USE_CPU_COMPUTE
    JPH_NO_DEBUG
)

target_link_libraries(vxnative PRIVATE joltjni ${JNI_LIBRARIES})

if(MSVC)
    target_compile_options(vxnative PRIVATE /GR- /EHs- /W4)
else()
    target_compile_options(vxnative PRIVATE -fno-rtti -fno-exceptions -Wall -Wextra)
endif()

# Output naming and static runtime linking
set_target_properties(vxnative PROPERTIES 
    OUTPUT_NAME "vxnative"
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>"
)