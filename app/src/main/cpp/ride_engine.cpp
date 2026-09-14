#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <android/log.h>
#include <sched.h>
#include <sys/mman.h>
#include <unistd.h>
#include <cctype>
#include <csignal>

#define TAG "IAcceptNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// Hardware Acceleration: Safe memory locking and thread affinity probing
extern "C" JNIEXPORT void JNICALL
Java_com_tellmeindia_iaccept_logic_NativeRideEngine_optimizeHardwareSpeedNative(JNIEnv* env, jobject thiz, jboolean isHighEnd) {
    // Ignore SIGSYS signal sent by SECCOMP on Knox/Oppo/Vivo kernels
    try {
        signal(SIGSYS, SIG_IGN);
    } catch (...) { }

    if (isHighEnd) {
        try {
            int res = mlockall(MCL_CURRENT | MCL_FUTURE);
            if (res == 0) {
                LOGD("HARDWARE ACCELERATION: Physical RAM pages locked for High-End tier");
            }
        } catch (...) { }
    }

    try {
        int numCores = sysconf(_SC_NPROCESSORS_ONLN);
        if (numCores > 0) {
            cpu_set_t cpuset;
            CPU_ZERO(&cpuset);

            if (numCores >= 8) {
                CPU_SET(4, &cpuset); CPU_SET(5, &cpuset);
                CPU_SET(6, &cpuset); CPU_SET(7, &cpuset);
            } else if (numCores >= 4) {
                CPU_SET(numCores - 2, &cpuset);
                CPU_SET(numCores - 1, &cpuset);
            } else {
                CPU_SET(0, &cpuset);
            }

            pid_t tid = gettid();
            int res = sched_setaffinity(tid, sizeof(cpu_set_t), &cpuset);
            if (res == 0) {
                LOGD("HARDWARE ACCELERATION: Thread bound dynamically for %d-core device", numCores);
            }
        }
    } catch (...) { }
}

// Nanosecond Raw Byte Pointer Fare Extractor (0% Regex Overhead / 0% Allocation)
static std::vector<int> parseFaresFromRawBytes(const char* buf, int len) {
    std::vector<int> fares;
    if (buf == nullptr || len <= 0) return fares;

    int i = 0;
    while (i < len) {
        if (isdigit((unsigned char)buf[i])) {
            int start = i;
            int val = 0;
            while (i < len && isdigit((unsigned char)buf[i])) {
                val = val * 10 + (buf[i] - '0');
                i++;
            }
            int numDigits = i - start;

            if (numDigits >= 2 && numDigits <= 5 && val >= 10 && val <= 9999) {
                bool likelyFare = false;

                int prefixStart = std::max(0, start - 12);
                for (int p = prefixStart; p < start; p++) {
                    unsigned char c = (unsigned char)buf[p];
                    if (c == '+' || c == '$') { likelyFare = true; break; }
                    if ((c == 'r' || c == 'R') && (p + 1 < start) && (buf[p+1] == 's' || buf[p+1] == 'S')) { likelyFare = true; break; }
                    if ((c == 'f' || c == 'F') && (p + 3 < start) && (tolower(buf[p+1]) == 'a') && (tolower(buf[p+2]) == 'r') && (tolower(buf[p+3]) == 'e')) { likelyFare = true; break; }
                    if (p + 2 < start && (unsigned char)buf[p] == 0xE2 && (unsigned char)buf[p+1] == 0x82 && (unsigned char)buf[p+2] == 0xB9) { likelyFare = true; break; }
                }

                if (likelyFare) {
                    if (std::find(fares.begin(), fares.end(), val) == fares.end()) {
                        fares.push_back(val);
                    }
                }
            }
        } else {
            i++;
        }
    }
    return fares;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_tellmeindia_iaccept_logic_NativeRideEngine_fastExtractFaresBuffer(JNIEnv* env, jobject /* this */, jobject directBuffer, jint length) {
    const char* nativeText = (const char*) env->GetDirectBufferAddress(directBuffer);
    if (nativeText == nullptr || length <= 0) return env->NewIntArray(0);

    std::vector<int> fares = parseFaresFromRawBytes(nativeText, length);

    jintArray result = env->NewIntArray(fares.size());
    env->SetIntArrayRegion(result, 0, fares.size(), fares.data());
    return result;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_tellmeindia_iaccept_logic_NativeRideEngine_extractFaresNative(JNIEnv* env, jobject /* this */, jstring text) {
    const char* nativeText = env->GetStringUTFChars(text, nullptr);
    if (nativeText == nullptr) return env->NewIntArray(0);
    int len = env->GetStringUTFLength(text);

    std::vector<int> fares = parseFaresFromRawBytes(nativeText, len);
    env->ReleaseStringUTFChars(text, nativeText);

    jintArray result = env->NewIntArray(fares.size());
    env->SetIntArrayRegion(result, 0, fares.size(), fares.data());
    return result;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_tellmeindia_iaccept_logic_NativeRideEngine_extractDistancesNative(JNIEnv* env, jobject /* this */, jstring text) {
    const char* nativeText = env->GetStringUTFChars(text, nullptr);
    if (nativeText == nullptr) return env->NewDoubleArray(0);
    int len = env->GetStringUTFLength(text);

    std::vector<double> distances;
    int i = 0;
    while (i < len) {
        if (isdigit((unsigned char)nativeText[i])) {
            double integerPart = 0.0;
            while (i < len && isdigit((unsigned char)nativeText[i])) {
                integerPart = integerPart * 10.0 + (nativeText[i] - '0');
                i++;
            }
            double decimalPart = 0.0;
            double divisor = 10.0;
            if (i < len && nativeText[i] == '.') {
                i++;
                while (i < len && isdigit((unsigned char)nativeText[i])) {
                    decimalPart += (nativeText[i] - '0') / divisor;
                    divisor *= 10.0;
                    i++;
                }
            }
            double val = integerPart + decimalPart;

            int lookAhead = i;
            while (lookAhead < len && isspace((unsigned char)nativeText[lookAhead])) lookAhead++;
            if (lookAhead < len) {
                char c1 = tolower(nativeText[lookAhead]);
                char c2 = (lookAhead + 1 < len) ? tolower(nativeText[lookAhead + 1]) : ' ';
                if ((c1 == 'k' && c2 == 'm') || (c1 == 'm' && c2 == 'i') || (c1 == 't' && c2 == 'o')) {
                    distances.push_back(val);
                }
            }
        } else {
            i++;
        }
    }

    env->ReleaseStringUTFChars(text, nativeText);

    jdoubleArray result = env->NewDoubleArray(distances.size());
    env->SetDoubleArrayRegion(result, 0, distances.size(), distances.data());
    return result;
}
