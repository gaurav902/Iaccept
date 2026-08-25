#include <jni.h>
#include <string>
#include <vector>
#include <regex>
#include <algorithm>
#include <android/log.h>

#define TAG "IAcceptNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// Improved fare extraction that is currency-symbol agnostic
extern "C" JNIEXPORT jintArray JNICALL
Java_com_tellmeindia_iaccept_logic_NativeRideEngine_extractFares(JNIEnv* env, jobject /* this */, jstring text) {
    const char* nativeText = env->GetStringUTFChars(text, nullptr);
    if (nativeText == nullptr) return env->NewIntArray(0);
    std::string content(nativeText);
    env->ReleaseStringUTFChars(text, nativeText);

    std::vector<int> fares;

    // 1. Match digits that follow currency indicators or are stand-alone with '+'
    // We search for patterns like ₹123, Rs 123, INR 123, or just 123 when '+' is present
    // To be safe with UTF-8, we'll look for digit sequences and check their surroundings

    std::regex combinedRegex(R"((\d{2,5}))"); // Look for 2 to 5 digit numbers
    auto words_begin = std::sregex_iterator(content.begin(), content.end(), combinedRegex);
    auto words_end = std::sregex_iterator();

    for (std::sregex_iterator i = words_begin; i != words_end; ++i) {
        std::smatch match = *i;
        int val = std::stoi(match.str());

        // Check if this number is likely a fare
        size_t start = match.position();
        bool likelyFare = false;

        // Check prefix for currency symbols (manual byte check for robustness)
        if (start > 0) {
            std::string prefix = content.substr(std::max(0, (int)start - 10), start);
            std::transform(prefix.begin(), prefix.end(), prefix.begin(), ::tolower);

            if (prefix.find("rs") != std::string::npos ||
                prefix.find("inr") != std::string::npos ||
                prefix.find("+") != std::string::npos ||
                prefix.find("fare") != std::string::npos) {
                likelyFare = true;
            }

            // UTF-8 check for ₹ (E2 82 B9)
            if (start >= 3) {
                unsigned char c1 = (unsigned char)content[start-3];
                unsigned char c2 = (unsigned char)content[start-2];
                unsigned char c3 = (unsigned char)content[start-1];
                if (c1 == 0xE2 && c2 == 0x82 && c3 == 0xB9) likelyFare = true;
            }
            // Check for ₹ with a space
            if (start >= 4) {
                unsigned char c1 = (unsigned char)content[start-4];
                unsigned char c2 = (unsigned char)content[start-3];
                unsigned char c3 = (unsigned char)content[start-2];
                if (c1 == 0xE2 && c2 == 0x82 && c3 == 0xB9) likelyFare = true;
            }
        }

        if (likelyFare && val >= 10 && val <= 9999) {
            fares.push_back(val);
        }
    }

    // Deduplicate while preserving order (some apps repeat values in hidden nodes)
    std::vector<int> uniqueFares;
    for (int f : fares) {
        if (std::find(uniqueFares.begin(), uniqueFares.end(), f) == uniqueFares.end()) {
            uniqueFares.push_back(f);
        }
    }

    jintArray result = env->NewIntArray(uniqueFares.size());
    env->SetIntArrayRegion(result, 0, uniqueFares.size(), uniqueFares.data());
    return result;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_tellmeindia_iaccept_logic_NativeRideEngine_extractDistances(JNIEnv* env, jobject /* this */, jstring text) {
    const char* nativeText = env->GetStringUTFChars(text, nullptr);
    if (nativeText == nullptr) return env->NewDoubleArray(0);
    std::string content(nativeText);
    env->ReleaseStringUTFChars(text, nativeText);

    std::vector<double> distances;
    // Catch decimals like 36.9
    std::regex distRegex(R"((\d+(?:\.\d+)?)\s?(?:km|mi|total))", std::regex_constants::icase);

    auto words_begin = std::sregex_iterator(content.begin(), content.end(), distRegex);
    auto words_end = std::sregex_iterator();

    for (std::sregex_iterator i = words_begin; i != words_end; ++i) {
        std::smatch match = *i;
        try {
            distances.push_back(std::stod(match[1].str()));
        } catch (...) {}
    }

    jdoubleArray result = env->NewDoubleArray(distances.size());
    env->SetDoubleArrayRegion(result, 0, distances.size(), distances.data());
    return result;
}
