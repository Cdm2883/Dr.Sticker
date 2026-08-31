cmake_minimum_required(VERSION 3.22)
project(drsticker LANGUAGES C)

set(FFMPEG_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/ffmpeg/build")

foreach(lib IN ITEMS avformat avfilter avcodec swscale avutil)
    add_library(${lib} SHARED IMPORTED)
    set_target_properties(${lib} PROPERTIES
            IMPORTED_LOCATION "${FFMPEG_ROOT}/${ANDROID_ABI}/lib/lib${lib}.so"
    )
endforeach()
add_library(ffmpegcli STATIC IMPORTED)
set_target_properties(ffmpegcli PROPERTIES
        IMPORTED_LOCATION "${FFMPEG_ROOT}/${ANDROID_ABI}/lib/libffmpegcli.a"
        INTERFACE_INCLUDE_DIRECTORIES "${FFMPEG_ROOT}/include"
)

add_library(drsticker SHARED
        ffmpeg-cli.c
)

target_link_options(drsticker PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(drsticker PRIVATE
        ffmpegcli
        avformat
        avfilter
        avcodec
        swscale
        avutil
        z
        log
)
