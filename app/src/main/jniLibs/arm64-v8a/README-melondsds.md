# Bundled melonDS DS (OpenGL ES build)

`libmelondsds_libretro_android.so` is melonDS DS built from
https://github.com/dukewan/melonds-ds, branch `youdian-gles`,
commit 17212b325e9ebbf39c2fc5f8fe081145a7bf78fc (melonDS from dukewan/melonDS `youdian-gles`).
Licensed GPL-3.0; the source is at the links above.

Built with Android NDK r27 (27.0.12077973), stripped:

    cmake -S . -B build-gles -G Ninja \
      -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DCMAKE_BUILD_TYPE=Release \
      -DENABLE_OPENGL=ON -DOPENGL_PROFILE=OpenGLES3 -DMELONDS_GLES=ON
    cmake --build build-gles
    llvm-strip --strip-unneeded build-gles/src/libretro/melondsds_libretro_android.so

Arcoon uses it ahead of any downloaded melonDS DS (`CoreInfo.prefersBundledBuild`).
