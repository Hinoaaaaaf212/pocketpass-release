cmake_minimum_required(VERSION 3.22.1)
project("pocketpass-keys")

add_library(pocketpass-keys SHARED native-keys.c)
target_link_libraries(pocketpass-keys log)

# Encrypted byte arrays and their lengths are passed from Gradle at build time.
# Each ENC_*_value is a comma-separated list of XOR'd hex bytes (e.g. "0x3a,0xa1,0x1f").
# The C code stores these as static arrays and decrypts at runtime.

# Defaults to empty if not provided
foreach(VAR ENC_SUPABASE_URL ENC_SUPABASE_URL_LEN
            ENC_SUPABASE_ANON_KEY ENC_SUPABASE_ANON_KEY_LEN)
    if(NOT DEFINED ${VAR})
        set(${VAR} "")
    endif()
endforeach()

target_compile_definitions(pocketpass-keys PRIVATE
    "ENC_SUPABASE_URL_BYTES=${ENC_SUPABASE_URL}"
    "ENC_SUPABASE_URL_LEN=${ENC_SUPABASE_URL_LEN}"
    "ENC_SUPABASE_ANON_KEY_BYTES=${ENC_SUPABASE_ANON_KEY}"
    "ENC_SUPABASE_ANON_KEY_LEN=${ENC_SUPABASE_ANON_KEY_LEN}"
)
