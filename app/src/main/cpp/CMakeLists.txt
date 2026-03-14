cmake_minimum_required(VERSION 3.22.1)
project("pocketpass-keys")

add_library(pocketpass-keys SHARED native-keys.c)
target_link_libraries(pocketpass-keys log)

# Multi-layer encrypted secrets: 6 defines per secret (A, A_LEN, B, B_LEN, TOTAL_LEN, CHECK)
# 3 secrets (URL=0, KEY=1, SECRET=2) = 18 defines total
# Passed from Gradle at build time.

# Defaults to empty if not provided
foreach(VAR ENC_URL_A ENC_URL_A_LEN ENC_URL_B ENC_URL_B_LEN ENC_URL_TOTAL_LEN ENC_URL_CHECK
            ENC_KEY_A ENC_KEY_A_LEN ENC_KEY_B ENC_KEY_B_LEN ENC_KEY_TOTAL_LEN ENC_KEY_CHECK
            ENC_SECRET_A ENC_SECRET_A_LEN ENC_SECRET_B ENC_SECRET_B_LEN ENC_SECRET_TOTAL_LEN ENC_SECRET_CHECK)
    if(NOT DEFINED ${VAR})
        set(${VAR} "")
    endif()
endforeach()

target_compile_definitions(pocketpass-keys PRIVATE
    "ENC_URL_A_BYTES=${ENC_URL_A}"
    "ENC_URL_A_LEN=${ENC_URL_A_LEN}"
    "ENC_URL_B_BYTES=${ENC_URL_B}"
    "ENC_URL_B_LEN=${ENC_URL_B_LEN}"
    "ENC_URL_TOTAL_LEN=${ENC_URL_TOTAL_LEN}"
    "ENC_URL_CHECK=${ENC_URL_CHECK}"
    "ENC_KEY_A_BYTES=${ENC_KEY_A}"
    "ENC_KEY_A_LEN=${ENC_KEY_A_LEN}"
    "ENC_KEY_B_BYTES=${ENC_KEY_B}"
    "ENC_KEY_B_LEN=${ENC_KEY_B_LEN}"
    "ENC_KEY_TOTAL_LEN=${ENC_KEY_TOTAL_LEN}"
    "ENC_KEY_CHECK=${ENC_KEY_CHECK}"
    "ENC_SECRET_A_BYTES=${ENC_SECRET_A}"
    "ENC_SECRET_A_LEN=${ENC_SECRET_A_LEN}"
    "ENC_SECRET_B_BYTES=${ENC_SECRET_B}"
    "ENC_SECRET_B_LEN=${ENC_SECRET_B_LEN}"
    "ENC_SECRET_TOTAL_LEN=${ENC_SECRET_TOTAL_LEN}"
    "ENC_SECRET_CHECK=${ENC_SECRET_CHECK}"
)

# Debug build flag — skips anti-tamper checks in native code
if(DEFINED ALLOW_DEBUG AND ALLOW_DEBUG)
    target_compile_definitions(pocketpass-keys PRIVATE ALLOW_DEBUG=1)
endif()
