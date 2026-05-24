cmake_minimum_required(VERSION 3.10.2)
project(MemeizerPaddleOCR)

set(PaddleLite_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../paddleocr/PaddleLite")
set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../paddleocr/OpenCV/sdk/native/jni")

include_directories(${PaddleLite_DIR}/cxx/include)
find_package(OpenCV REQUIRED)
include_directories(${OpenCV_INCLUDE_DIRS})
aux_source_directory(. SOURCES)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -frtti -fexceptions -Wno-format -ffast-math -Ofast -Os")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden -fdata-sections -ffunction-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,-z,nocopyreloc")

add_library(Native SHARED ${SOURCES})

find_library(log-lib log)

add_library(paddle_light_api_shared SHARED IMPORTED)
set_target_properties(
    paddle_light_api_shared
    PROPERTIES IMPORTED_LOCATION
    ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/libpaddle_light_api_shared.so
)

target_link_libraries(
    Native
    paddle_light_api_shared
    ${OpenCV_LIBS}
    GLESv2
    EGL
    jnigraphics
    ${log-lib}
)

foreach(extra_lib libc++_shared.so libpaddle_light_api_shared.so libhiai.so libhiai_ir.so libhiai_ir_build.so)
    add_custom_command(
        TARGET Native
        POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
        ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/${extra_lib}
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/${extra_lib}
    )
endforeach()
