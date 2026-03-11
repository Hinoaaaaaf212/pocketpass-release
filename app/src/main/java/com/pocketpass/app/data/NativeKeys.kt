package com.pocketpass.app.data

object NativeKeys {
    init {
        System.loadLibrary("pocketpass-keys")
    }

    external fun getSupabaseUrl(): String
    external fun getSupabaseAnonKey(): String
}
