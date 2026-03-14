package com.pocketpass.app.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = NativeKeys.getSupabaseUrl(),
        supabaseKey = NativeKeys.getSupabaseAnonKey()
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Functions)
    }
}
