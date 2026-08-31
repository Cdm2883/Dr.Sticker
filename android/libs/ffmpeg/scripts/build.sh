#!/usr/bin/env bash

set -euo pipefail

# --- components enabled on top of --disable-everything ----------------------
# Extend these lists as needed; every name must be a valid configure component
# (see the FFmpeg configure --help component lists).
# NOTE: unknown names are silently ignored by configure, so keep them verified against the source.
DECODERS=(png apng mjpeg gif webp bmp vp8 vp9)
DEMUXERS=(image2 image2pipe gif webp_anim apng matroska)
ENCODERS=(gif png)
MUXERS=(gif image2)
FILTERS=(scale format fps crop pad transpose null palettegen paletteuse split)
PROTOCOLS=(file pipe)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FFMPEG_SOURCE_DIR="${FFMPEG_SOURCE_DIR:-$SCRIPT_DIR/../source}"
FFMPEG_BUILD_DIR="${FFMPEG_BUILD_DIR:-$SCRIPT_DIR/../build}"
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-27}"
JOBS="${JOBS:-$(nproc 2>/dev/null || echo 4)}"
ABIS="${ANDROID_ABI_LIST:-arm64-v8a armeabi-v7a x86 x86_64}"

fail() { echo "ERROR: $*" >&2; exit 1; }
[ -n "${ANDROID_NDK_ROOT:-}" ] || fail "ANDROID_NDK_ROOT is not set."

if command -v cygpath >/dev/null 2>&1; then
    FFMPEG_SOURCE_DIR="$(cygpath -u "$FFMPEG_SOURCE_DIR")"
    FFMPEG_BUILD_DIR="$(cygpath -u "$FFMPEG_BUILD_DIR")"
    ANDROID_NDK_ROOT="$(cygpath -u "$ANDROID_NDK_ROOT")"
fi
: "${TMP:=/tmp}" "${TEMP:=/tmp}"
export TMP TEMP

[ -f "$FFMPEG_SOURCE_DIR/configure" ] \
    || fail "FFmpeg source not found at '$FFMPEG_SOURCE_DIR'. Run scripts/fetch.ps1 first."

[ -d "$ANDROID_NDK_ROOT" ] || fail "NDK not found at '$ANDROID_NDK_ROOT'."
command -v make >/dev/null 2>&1 || fail "make not found. msys2: pacman -S make"

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) HOST_TAG="windows-x86_64" ;;
    Darwin)               HOST_TAG="darwin-x86_64" ;;
    *)                    HOST_TAG="linux-x86_64" ;;
esac
TOOLCHAIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
SYSROOT="$TOOLCHAIN/sysroot"
STRIP="$TOOLCHAIN/bin/llvm-strip"
for tool in "$TOOLCHAIN/bin/llvm-ar" "$TOOLCHAIN/bin/llvm-nm" \
            "$TOOLCHAIN/bin/llvm-ranlib" "$STRIP"; do
    [ -f "$tool" ] || fail "NDK tool missing: $tool"
done

if [ -z "${HOST_CC:-}" ]; then
    HOST_CC="$(command -v gcc || command -v cc || command -v clang || true)"
fi
case "$HOST_CC" in
    [A-Za-z]:/*|[A-Za-z]:\\*)
        if command -v cygpath >/dev/null 2>&1; then
            HOST_CC="$(cygpath -u "$HOST_CC")"
        fi
        ;;
esac
[ -n "$HOST_CC" ] \
    || fail "Host C compiler not found; set HOST_CC or install one (msys2: pacman -S mingw-w64-x86_64-gcc)"
PATH="$(dirname "$HOST_CC"):$PATH"
HOST_CC_DISPLAY="$HOST_CC"
case "$HOST_CC" in
    *" "*)
        mkdir -p "$FFMPEG_BUILD_DIR/hostbin"
        printf '#!/bin/sh\nexec "%s" "$@"\n' "$HOST_CC" > "$FFMPEG_BUILD_DIR/hostbin/hostcc"
        chmod +x "$FFMPEG_BUILD_DIR/hostbin/hostcc"
        PATH="$FFMPEG_BUILD_DIR/hostbin:$PATH"
        HOST_CC=hostcc
        ;;
esac
export PATH

archive_cli_objects() {
    local objdir="$1" out_dir="$2"
    local objcopy="$TOOLCHAIN/bin/llvm-objcopy"
    local -a cli_objects=()
    local obj
    # fftools/Makefile#OBJS-ffmpeg
    for obj in "$objdir"/fftools/*.o \
               "$objdir"/fftools/graph/*.o \
               "$objdir"/fftools/textformat/*.o \
               "$objdir"/fftools/resources/*.o \
               "$objdir"/compat/*.o; do
        [ -f "$obj" ] || continue
        case "${obj##*/}" in ffprobe*.o|ffplay*.o) continue ;; esac
        "$objcopy" --redefine-sym main=run_ffmpeg "$obj"
        cli_objects+=("$obj")
    done
    "$TOOLCHAIN/bin/llvm-ar" rcs "$out_dir/lib/libffmpegcli.a" "${cli_objects[@]}"
    printf '    %-22s %s\n' "libffmpegcli.a" "$(du -h "$out_dir/lib/libffmpegcli.a" | cut -f1)"
}

build_abi() {
    local abi="$1" arch cpu triple abi_cflags
    case "$abi" in
        arm64-v8a)
            arch="aarch64" 
            cpu="armv8-a" 
            triple="aarch64-linux-android" 
            abi_cflags="" 
            ;;
        armeabi-v7a)
            arch="arm" 
            cpu="armv7-a" 
            triple="armv7a-linux-androideabi"
            abi_cflags="-mfpu=neon -mfloat-abi=softfp" 
            ;;
        x86)
            arch="x86" 
            cpu="i686" 
            triple="i686-linux-android" 
            abi_cflags="" 
            ;;
        x86_64)
            arch="x86_64" 
            cpu="" 
            triple="x86_64-linux-android" 
            abi_cflags="" 
            ;;
        *) 
            fail "Unsupported ABI '$abi'." ;;
    esac

    local cc="$TOOLCHAIN/bin/${triple}${ANDROID_API_LEVEL}-clang"
    [ -f "$cc" ] || fail "clang wrapper not found: $cc (ANDROID_API_LEVEL $ANDROID_API_LEVEL not supported by this NDK?)"

    local out_dir="$FFMPEG_BUILD_DIR/$abi"
    local obj_dir="$FFMPEG_BUILD_DIR/obj/$abi"
    rm -rf "$out_dir" "$obj_dir"
    mkdir -p "$obj_dir"

    local -a CONFIGURE_ARGS=(
        --prefix="$out_dir"
        --libdir="$out_dir/lib"
        --incdir="$FFMPEG_BUILD_DIR/include"
        --bindir="$out_dir/bin"
        --cc="$cc" --ld="$cc"
        --host-cc="$HOST_CC"
        --ar="$TOOLCHAIN/bin/llvm-ar"
        --nm="$TOOLCHAIN/bin/llvm-nm"
        --ranlib="$TOOLCHAIN/bin/llvm-ranlib"
        --strip="$STRIP"
        --sysroot="$SYSROOT"
        --extra-cflags="$abi_cflags -Os -ffunction-sections -fdata-sections"
        --extra-ldflags="-Wl,--gc-sections -Wl,-z,max-page-size=16384"
        --enable-cross-compile --target-os=android
        --arch="$arch"
        --enable-shared --disable-static --enable-pic
        --enable-small
        --disable-doc --disable-debug
        --disable-autodetect --disable-network --disable-iconv
        --enable-zlib
        --disable-avdevice --disable-swresample
        --disable-everything
    )
    [ -z "$cpu" ] || CONFIGURE_ARGS+=(--cpu="$cpu")
    [ "$arch" != "arm" ] || CONFIGURE_ARGS+=(--enable-neon)

    if [ "$abi" = "x86" ]; then
        # 32-bit x86 assembly emits non-PIC relocations rejected by Android.
        CONFIGURE_ARGS+=(--disable-inline-asm --disable-x86asm)
    elif [ "$abi" = "x86_64" ] && ! command -v nasm >/dev/null 2>&1; then
        CONFIGURE_ARGS+=(--disable-x86asm)
    fi

    local name
    for name in "${DECODERS[@]}";  do CONFIGURE_ARGS+=(--enable-decoder="$name");  done
    for name in "${DEMUXERS[@]}";  do CONFIGURE_ARGS+=(--enable-demuxer="$name");  done
    for name in "${ENCODERS[@]}";  do CONFIGURE_ARGS+=(--enable-encoder="$name");  done
    for name in "${MUXERS[@]}";    do CONFIGURE_ARGS+=(--enable-muxer="$name");    done
    for name in "${FILTERS[@]}";   do CONFIGURE_ARGS+=(--enable-filter="$name");   done
    for name in "${PROTOCOLS[@]}"; do CONFIGURE_ARGS+=(--enable-protocol="$name"); done

    echo "==> [$abi] configure"
    (
        cd "$obj_dir"
        "$FFMPEG_SOURCE_DIR/configure" "${CONFIGURE_ARGS[@]}" 2>&1 | tee configure.log
        echo "==> [$abi] make -j$JOBS"
        make -j"$JOBS" 2>&1 | tee build.log
        echo "==> [$abi] install"
        make install 2>&1 | tee install.log
    )

    for artifact in "$out_dir"/lib/*.so "$out_dir"/bin/*; do
        [ -f "$artifact" ] || continue
        "$STRIP" --strip-unneeded "$artifact"
        printf '    %-22s %s\n' "${artifact##*/}" "$(du -h "$artifact" | cut -f1)"
    done
    archive_cli_objects "$obj_dir" "$out_dir"
    echo "==> [$abi] done: $out_dir"
}

FFMPEG_VERSION="$(cat "$FFMPEG_SOURCE_DIR/RELEASE" 2>/dev/null || echo unknown)"
echo "-- FFmpeg:      $FFMPEG_VERSION ($FFMPEG_SOURCE_DIR)"
echo "-- Output:      $FFMPEG_BUILD_DIR"
echo "-- Android NDK: $ANDROID_NDK_ROOT"
echo "-- Target ABIs: $ABIS"
echo "-- API Level:   $ANDROID_API_LEVEL"
echo "-- Build Jobs:  $JOBS"
echo "-- Host CC:     $HOST_CC_DISPLAY"

for abi in $ABIS; do
    build_abi "$abi"
done
echo "-- All ABIs built. Shared headers: $FFMPEG_BUILD_DIR/include"
