#include <jni.h>
#include <string.h>

/*
 * XOR-encrypted key storage.
 *
 * Keys are XOR-encrypted at build time in Gradle and passed as byte arrays
 * via CMake defines. No plaintext key string ever appears in the .so binary.
 * At runtime, we decrypt into a stack buffer, create a Java String, then
 * zero the buffer immediately.
 */

// XOR mask — must match the mask in build.gradle.kts
static const unsigned char XOR_MASK[] = { 0x5A, 0xC3, 0x7E, 0x91, 0xF0, 0x2D, 0xB8, 0x64 };
static const int MASK_LEN = 8;

// Decrypt XOR'd bytes into buf, then null-terminate
static void xor_decrypt(const unsigned char *enc, int len, char *buf) {
    for (int i = 0; i < len; i++) {
        buf[i] = (char)(enc[i] ^ XOR_MASK[i % MASK_LEN]);
    }
    buf[len] = '\0';
}

// Encrypted byte arrays injected at compile time
static const unsigned char enc_supabase_url[] = { ENC_SUPABASE_URL_BYTES };
static const unsigned char enc_supabase_anon_key[] = { ENC_SUPABASE_ANON_KEY_BYTES };

JNIEXPORT jstring JNICALL
Java_com_pocketpass_app_data_NativeKeys_getSupabaseUrl(JNIEnv *env, jobject thiz) {
    char buf[512];
    int len = ENC_SUPABASE_URL_LEN;
    if (len >= (int)sizeof(buf)) len = sizeof(buf) - 1;
    xor_decrypt(enc_supabase_url, len, buf);
    jstring result = (*env)->NewStringUTF(env, buf);
    memset(buf, 0, sizeof(buf));  // Zero plaintext from stack
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_pocketpass_app_data_NativeKeys_getSupabaseAnonKey(JNIEnv *env, jobject thiz) {
    char buf[512];
    int len = ENC_SUPABASE_ANON_KEY_LEN;
    if (len >= (int)sizeof(buf)) len = sizeof(buf) - 1;
    xor_decrypt(enc_supabase_anon_key, len, buf);
    jstring result = (*env)->NewStringUTF(env, buf);
    memset(buf, 0, sizeof(buf));
    return result;
}

