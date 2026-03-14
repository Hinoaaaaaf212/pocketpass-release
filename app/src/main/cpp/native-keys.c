#include <jni.h>
#include <string.h>
#include <stdlib.h>

#ifndef ALLOW_DEBUG
#include <stdio.h>
#include <unistd.h>
#endif

/*
 * Multi-layer encrypted key storage with anti-tamper protection.
 *
 * Build-time encryption (Gradle):
 *   1. Split plaintext into even-indexed (part A) and odd-indexed (part B) bytes
 *   2. Derive 16-byte key from master seed + secret index
 *   3. Encrypt each part: XOR with key -> AES S-box -> bit rotate left 3
 *
 * Runtime decryption reverses all steps. CRC-8 verifies integrity.
 * Release builds include anti-debugging and APK signing verification.
 */

/* ---- AES S-box and inverse S-box ---- */
static const unsigned char _cfg_tbl_fwd[256] = {
    0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
    0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
    0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
    0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
    0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
    0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
    0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
    0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
    0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
    0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
    0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
    0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
    0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
    0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
    0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
    0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
};

/* Inverse S-box (computed from forward S-box) */
static const unsigned char _cfg_tbl_inv[256] = {
    0x52,0x09,0x6a,0xd5,0x30,0x36,0xa5,0x38,0xbf,0x40,0xa3,0x9e,0x81,0xf3,0xd7,0xfb,
    0x7c,0xe3,0x39,0x82,0x9b,0x2f,0xff,0x87,0x34,0x8e,0x43,0x44,0xc4,0xde,0xe9,0xcb,
    0x54,0x7b,0x94,0x32,0xa6,0xc2,0x23,0x3d,0xee,0x4c,0x95,0x0b,0x42,0xfa,0xc3,0x4e,
    0x08,0x2e,0xa1,0x66,0x28,0xd9,0x24,0xb2,0x76,0x5b,0xa2,0x49,0x6d,0x8b,0xd1,0x25,
    0x72,0xf8,0xf6,0x64,0x86,0x68,0x98,0x16,0xd4,0xa4,0x5c,0xcc,0x5d,0x65,0xb6,0x92,
    0x6c,0x70,0x48,0x50,0xfd,0xed,0xb9,0xda,0x5e,0x15,0x46,0x57,0xa7,0x8d,0x9d,0x84,
    0x90,0xd8,0xab,0x00,0x8c,0xbc,0xd3,0x0a,0xf7,0xe4,0x58,0x05,0xb8,0xb3,0x45,0x06,
    0xd0,0x2c,0x1e,0x8f,0xca,0x3f,0x0f,0x02,0xc1,0xaf,0xbd,0x03,0x01,0x13,0x8a,0x6b,
    0x3a,0x91,0x11,0x41,0x4f,0x67,0xdc,0xea,0x97,0xf2,0xcf,0xce,0xf0,0xb4,0xe6,0x73,
    0x96,0xac,0x74,0x22,0xe7,0xad,0x35,0x85,0xe2,0xf9,0x37,0xe8,0x1c,0x75,0xdf,0x6e,
    0x47,0xf1,0x1a,0x71,0x1d,0x29,0xc5,0x89,0x6f,0xb7,0x62,0x0e,0xaa,0x18,0xbe,0x1b,
    0xfc,0x56,0x3e,0x4b,0xc6,0xd2,0x79,0x20,0x9a,0xdb,0xc0,0xfe,0x78,0xcd,0x5a,0xf4,
    0x1f,0xdd,0xa8,0x33,0x88,0x07,0xc7,0x31,0xb1,0x12,0x10,0x59,0x27,0x80,0xec,0x5f,
    0x60,0x51,0x7f,0xa9,0x19,0xb5,0x4a,0x0d,0x2d,0xe5,0x7a,0x9f,0x93,0xc9,0x9c,0xef,
    0xa0,0xe0,0x3b,0x4d,0xae,0x2a,0xf5,0xb0,0xc8,0xeb,0xbb,0x3c,0x83,0x53,0x99,0x61,
    0x17,0x2b,0x04,0x7e,0xba,0x77,0xd6,0x26,0xe1,0x69,0x14,0x63,0x55,0x21,0x0c,0x7d
};

/* ---- Master seed (split into two XOR halves) ---- */
static const unsigned char _res_map_0[32] = {
    0x3A, 0xF1, 0x82, 0x4D, 0xC6, 0x19, 0xE7, 0x5B,
    0x90, 0x2C, 0xD8, 0x63, 0xAB, 0x74, 0x0E, 0xF5,
    0x48, 0xBC, 0x31, 0x9A, 0xE2, 0x57, 0x0F, 0xC4,
    0x86, 0x6D, 0xA1, 0x38, 0xDB, 0x14, 0x7F, 0xE9
};
static const unsigned char _res_map_1[32] = {
    0x71, 0x8C, 0xD5, 0x22, 0xA9, 0x6E, 0x43, 0xB0,
    0xFE, 0x15, 0x97, 0x5A, 0xC8, 0x03, 0x64, 0x8F,
    0x2D, 0xE6, 0x5C, 0xF3, 0xAA, 0x39, 0x76, 0x81,
    0xBD, 0x4A, 0xDE, 0x07, 0x95, 0x6B, 0x10, 0xA4
};

/* ---- Encrypted data arrays ---- */
static const unsigned char enc_r0_a[] = { ENC_URL_A_BYTES };
static const unsigned char enc_r0_b[] = { ENC_URL_B_BYTES };
static const unsigned char enc_r1_a[] = { ENC_KEY_A_BYTES };
static const unsigned char enc_r1_b[] = { ENC_KEY_B_BYTES };
static const unsigned char enc_r2_a[] = { ENC_SECRET_A_BYTES };
static const unsigned char enc_r2_b[] = { ENC_SECRET_B_BYTES };

/* ---- Decoy arrays (anti-static-analysis noise) ---- */
__attribute__((used))
static const unsigned char _manifest_cache[] = {
    0xDE, 0xAD, 0xBE, 0xEF, 0xCA, 0xFE, 0xBA, 0xBE,
    0x12, 0x34, 0x56, 0x78, 0x9A, 0xBC, 0xDE, 0xF0,
    0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF,
    0xFE, 0xDC, 0xBA, 0x98, 0x76, 0x54, 0x32, 0x10
};

__attribute__((used))
static const unsigned char _resource_idx[] = {
    0xA5, 0x5A, 0x3C, 0xC3, 0x69, 0x96, 0xF0, 0x0F,
    0x71, 0x8E, 0xD2, 0x4B, 0xE7, 0x13, 0xAC, 0x58,
    0x2D, 0xB6, 0xC4, 0x7F, 0x90, 0x01, 0xE8, 0x3A,
    0x55, 0xCB, 0x47, 0xFD, 0x86, 0x19, 0x64, 0xA0
};

/* ---- Volatile memset to prevent compiler optimizing away zeroing ---- */
static void __attribute__((noinline)) secure_zero(void *ptr, size_t len) {
    volatile unsigned char *p = (volatile unsigned char *)ptr;
    while (len--) *p++ = 0;
}

/* ---- Derive 16-byte key from master seed + secret index ---- */
static void __attribute__((noinline)) _validate_config_cache(
    const unsigned char *seed, int index, unsigned char *key_out
) {
    for (int i = 0; i < 16; i++) {
        unsigned char s = seed[(index * 16 + i) % 32];
        unsigned char mix = seed[(i * 7 + index * 3 + 5) % 32];
        key_out[i] = (unsigned char)((s ^ mix ^ (index + i * 13 + 0xA5)) & 0xFF);
    }
}

/* ---- CRC-8 integrity check ---- */
static unsigned char __attribute__((noinline)) _parse_manifest_entry(
    const unsigned char *data, int len
) {
    unsigned char crc = 0xFF;
    for (int i = 0; i < len; i++) {
        crc ^= data[i];
        for (int bit = 0; bit < 8; bit++) {
            if (crc & 0x80)
                crc = (unsigned char)(((crc << 1) ^ 0x1D) & 0xFF);
            else
                crc = (unsigned char)((crc << 1) & 0xFF);
        }
    }
    return crc;
}

/* ---- Multi-layer decryption ---- */
static void __attribute__((noinline)) _verify_resource_integrity(
    const unsigned char *enc_a, int a_len,
    const unsigned char *enc_b, int b_len,
    int total_len, int expected_check, int secret_index,
    char *out, int *out_valid
) {
    unsigned char seed[32];
    unsigned char key[16];
    unsigned char dec_a[512];
    unsigned char dec_b[512];
    int i;

    *out_valid = 0;

    if (a_len > 512 || b_len > 512 || total_len > 1024) return;

    /* Reconstruct master seed from two XOR-split halves */
    for (i = 0; i < 32; i++) {
        seed[i] = _res_map_0[i] ^ _res_map_1[i];
    }

    /* Derive per-secret key */
    _validate_config_cache(seed, secret_index, key);

    /* Decrypt part A: un-rotate -> inverse S-box -> XOR key */
    for (i = 0; i < a_len; i++) {
        unsigned char v = enc_a[i];
        /* Un-rotate right by 3 (reverse of left-rotate 3) */
        v = (unsigned char)(((v >> 3) | (v << 5)) & 0xFF);
        /* Inverse S-box */
        v = _cfg_tbl_inv[v];
        /* XOR with key */
        v ^= key[i % 16];
        dec_a[i] = v;
    }

    /* Decrypt part B: same steps */
    for (i = 0; i < b_len; i++) {
        unsigned char v = enc_b[i];
        v = (unsigned char)(((v >> 3) | (v << 5)) & 0xFF);
        v = _cfg_tbl_inv[v];
        v ^= key[i % 16];
        dec_b[i] = v;
    }

    /* Interleave: even indices from A, odd from B */
    for (i = 0; i < total_len; i++) {
        if (i % 2 == 0)
            out[i] = (char)dec_a[i / 2];
        else
            out[i] = (char)dec_b[i / 2];
    }
    out[total_len] = '\0';

    /* CRC-8 integrity check */
    unsigned char actual_check = _parse_manifest_entry((unsigned char *)out, total_len);
    if (actual_check == (unsigned char)expected_check) {
        *out_valid = 1;
    }

    /* Zero intermediates */
    secure_zero(seed, sizeof(seed));
    secure_zero(key, sizeof(key));
    secure_zero(dec_a, sizeof(dec_a));
    secure_zero(dec_b, sizeof(dec_b));
}

/* ---- Decoy functions ---- */
__attribute__((used, noinline))
static int _check_resource_alignment(const unsigned char *data, int len) {
    int sum = 0;
    for (int i = 0; i < len; i++) sum += data[i] ^ _manifest_cache[i % 32];
    return sum & 0xFF;
}

__attribute__((used, noinline))
static int _validate_manifest_header(const unsigned char *hdr, int sz) {
    int acc = 0x5A;
    for (int i = 0; i < sz; i++) acc = ((acc << 1) ^ hdr[i] ^ _resource_idx[i % 32]) & 0xFFFF;
    return acc;
}

__attribute__((used, noinline))
static void _rebuild_config_index(unsigned char *dst, const unsigned char *src, int n) {
    for (int i = 0; i < n; i++) dst[i] = src[i] ^ _manifest_cache[(i * 3 + 7) % 32];
}

/* ---- Anti-tamper checks (release only) ---- */
#ifndef ALLOW_DEBUG

/* APK signing certificate SHA-256 fingerprint (pre-XOR'd with mask for obfuscation) */
static const unsigned char _cert_fp_a[16] = {
    0xFE, 0x39, 0xD6, 0x90, 0xC3, 0x8C, 0x74, 0x9A,
    0xE8, 0x76, 0x56, 0xB7, 0xA8, 0x69, 0x2F, 0x9B
};
static const unsigned char _cert_fp_b[16] = {
    0x8F, 0x0D, 0x22, 0x1C, 0x3B, 0x7B, 0x26, 0x2C,
    0x8E, 0x90, 0xE3, 0x5E, 0xB3, 0x7D, 0xFE, 0x39
};
static const unsigned char _cert_fp_mask = 0x5C;

static int __attribute__((noinline)) _check_tracer(void) {
    char buf[256];
    FILE *f = fopen("/proc/self/status", "r");
    if (!f) return 0; /* can't check = allow */
    int traced = 0;
    while (fgets(buf, sizeof(buf), f)) {
        if (strncmp(buf, "TracerPid:", 10) == 0) {
            int pid = atoi(buf + 10);
            if (pid != 0) traced = 1;
            break;
        }
    }
    fclose(f);
    secure_zero(buf, sizeof(buf));
    return traced;
}

static int __attribute__((noinline)) _check_cmdline(void) {
    char buf[256];
    memset(buf, 0, sizeof(buf));
    FILE *f = fopen("/proc/self/cmdline", "r");
    if (!f) return 0;
    int n = (int)fread(buf, 1, sizeof(buf) - 1, f);
    fclose(f);
    buf[n] = '\0';
    /* Expected: starts with com.pocketpass.app */
    const char *expected = "com.pocketpass.app";
    int result = (strncmp(buf, expected, strlen(expected)) != 0) ? 1 : 0;
    secure_zero(buf, sizeof(buf));
    return result;
}

static int __attribute__((noinline)) _verify_signing_cert(JNIEnv *env) {
    /* Get current application context via ActivityThread */
    jclass at_class = (*env)->FindClass(env, "android/app/ActivityThread");
    if (!at_class) return 0;
    jmethodID cat = (*env)->GetStaticMethodID(env, at_class, "currentApplication", "()Landroid/app/Application;");
    if (!cat) return 0;
    jobject app = (*env)->CallStaticObjectMethod(env, at_class, cat);
    if (!app) return 0;

    /* Get PackageManager */
    jclass ctx_class = (*env)->GetObjectClass(env, app);
    jmethodID gpm = (*env)->GetMethodID(env, ctx_class, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jobject pm = (*env)->CallObjectMethod(env, app, gpm);
    if (!pm) return 0;

    /* Get package name */
    jmethodID gpn = (*env)->GetMethodID(env, ctx_class, "getPackageName", "()Ljava/lang/String;");
    jstring pkg_name = (jstring)(*env)->CallObjectMethod(env, app, gpn);
    if (!pkg_name) return 0;

    /* getPackageInfo with GET_SIGNING_CERTIFICATES (0x08000000) */
    jclass pm_class = (*env)->GetObjectClass(env, pm);
    jmethodID gpi = (*env)->GetMethodID(env, pm_class, "getPackageInfo",
        "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    jobject pkg_info = (*env)->CallObjectMethod(env, pm, gpi, pkg_name, (jint)0x08000000);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        /* Fallback to GET_SIGNATURES (0x00000040) for API < 28 */
        pkg_info = (*env)->CallObjectMethod(env, pm, gpi, pkg_name, (jint)0x00000040);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            return 0;
        }
    }
    if (!pkg_info) return 0;

    /* Get signing info - try signingInfo first (API 28+) */
    jclass pi_class = (*env)->GetObjectClass(env, pkg_info);
    jbyteArray cert_bytes = NULL;

    jfieldID si_field = (*env)->GetFieldID(env, pi_class, "signingInfo", "Landroid/content/pm/SigningInfo;");
    if (si_field && !(*env)->ExceptionCheck(env)) {
        jobject signing_info = (*env)->GetObjectField(env, pkg_info, si_field);
        if (signing_info) {
            jclass si_class = (*env)->GetObjectClass(env, signing_info);
            jmethodID gas = (*env)->GetMethodID(env, si_class, "getApkContentsSigners", "()[Landroid/content/pm/Signature;");
            if (gas) {
                jobjectArray sigs = (jobjectArray)(*env)->CallObjectMethod(env, signing_info, gas);
                if (sigs && (*env)->GetArrayLength(env, sigs) > 0) {
                    jobject sig = (*env)->GetObjectArrayElement(env, sigs, 0);
                    jclass sig_class = (*env)->GetObjectClass(env, sig);
                    jmethodID tba = (*env)->GetMethodID(env, sig_class, "toByteArray", "()[B");
                    cert_bytes = (jbyteArray)(*env)->CallObjectMethod(env, sig, tba);
                }
            }
        }
    }
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);

    /* Fallback: signatures field */
    if (!cert_bytes) {
        jfieldID sigs_field = (*env)->GetFieldID(env, pi_class, "signatures", "[Landroid/content/pm/Signature;");
        if (sigs_field && !(*env)->ExceptionCheck(env)) {
            jobjectArray sigs = (jobjectArray)(*env)->GetObjectField(env, pkg_info, sigs_field);
            if (sigs && (*env)->GetArrayLength(env, sigs) > 0) {
                jobject sig = (*env)->GetObjectArrayElement(env, sigs, 0);
                jclass sig_class = (*env)->GetObjectClass(env, sig);
                jmethodID tba = (*env)->GetMethodID(env, sig_class, "toByteArray", "()[B");
                cert_bytes = (jbyteArray)(*env)->CallObjectMethod(env, sig, tba);
            }
        }
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    }

    if (!cert_bytes) return 0;

    /* Compute SHA-256 of certificate */
    jclass md_class = (*env)->FindClass(env, "java/security/MessageDigest");
    jmethodID gi = (*env)->GetStaticMethodID(env, md_class, "getInstance",
        "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    jstring sha256_str = (*env)->NewStringUTF(env, "SHA-256");
    jobject md = (*env)->CallStaticObjectMethod(env, md_class, gi, sha256_str);
    jmethodID digest = (*env)->GetMethodID(env, md_class, "digest", "([B)[B");
    jbyteArray hash_arr = (jbyteArray)(*env)->CallObjectMethod(env, md, digest, cert_bytes);

    if (!hash_arr || (*env)->GetArrayLength(env, hash_arr) != 32) return 0;

    jbyte *hash = (*env)->GetByteArrayElements(env, hash_arr, NULL);
    if (!hash) return 0;

    /* Compare against expected fingerprint (stored pre-XOR'd with mask) */
    int match = 1;
    for (int i = 0; i < 16; i++) {
        if (((unsigned char)hash[i] ^ _cert_fp_mask) != _cert_fp_a[i]) {
            match = 0;
            break;
        }
    }
    if (match) {
        for (int i = 0; i < 16; i++) {
            if (((unsigned char)hash[16 + i] ^ _cert_fp_mask) != _cert_fp_b[i]) {
                match = 0;
                break;
            }
        }
    }

    (*env)->ReleaseByteArrayElements(env, hash_arr, hash, JNI_ABORT);
    return match ? 0 : 1; /* 0 = OK, 1 = tampered */
}

static volatile int _tamper_state = -1; /* -1=unchecked, 0=ok, 1=tampered */

static int __attribute__((noinline)) _run_tamper_checks(JNIEnv *env) {
    if (_tamper_state >= 0) return _tamper_state;

    if (_check_tracer()) { _tamper_state = 1; return 1; }
    if (_check_cmdline()) { _tamper_state = 1; return 1; }
    if (_verify_signing_cert(env)) { _tamper_state = 1; return 1; }

    _tamper_state = 0;
    return 0;
}
#endif /* !ALLOW_DEBUG */

/* ---- Helper: decrypt and return jstring ---- */
static jstring __attribute__((noinline)) _get_decrypted(
    JNIEnv *env,
    const unsigned char *enc_a, int a_len,
    const unsigned char *enc_b, int b_len,
    int total_len, int check, int index
) {
#ifndef ALLOW_DEBUG
    if (_run_tamper_checks(env)) {
        return (*env)->NewStringUTF(env, "");
    }
#endif

    char buf[1024];
    int valid = 0;
    _verify_resource_integrity(enc_a, a_len, enc_b, b_len, total_len, check, index, buf, &valid);

    jstring result;
    if (valid) {
        result = (*env)->NewStringUTF(env, buf);
    } else {
        result = (*env)->NewStringUTF(env, "");
    }
    secure_zero(buf, sizeof(buf));
    return result;
}

/* ---- JNI exports ---- */

JNIEXPORT jstring JNICALL
Java_com_pocketpass_app_data_NativeKeys_getSupabaseUrl(JNIEnv *env, jobject thiz) {
    return _get_decrypted(env,
        enc_r0_a, ENC_URL_A_LEN, enc_r0_b, ENC_URL_B_LEN,
        ENC_URL_TOTAL_LEN, ENC_URL_CHECK, 0);
}

JNIEXPORT jstring JNICALL
Java_com_pocketpass_app_data_NativeKeys_getSupabaseAnonKey(JNIEnv *env, jobject thiz) {
    return _get_decrypted(env,
        enc_r1_a, ENC_KEY_A_LEN, enc_r1_b, ENC_KEY_B_LEN,
        ENC_KEY_TOTAL_LEN, ENC_KEY_CHECK, 1);
}

JNIEXPORT jstring JNICALL
Java_com_pocketpass_app_data_NativeKeys_getSignupSecret(JNIEnv *env, jobject thiz) {
    return _get_decrypted(env,
        enc_r2_a, ENC_SECRET_A_LEN, enc_r2_b, ENC_SECRET_B_LEN,
        ENC_SECRET_TOTAL_LEN, ENC_SECRET_CHECK, 2);
}
