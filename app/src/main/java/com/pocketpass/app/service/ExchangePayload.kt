package com.pocketpass.app.service

import androidx.annotation.Keep

@Keep
data class ExchangePayload(
    val userId: String,
    val userName: String,
    val avatarHex: String,
    val greeting: String,
    val origin: String,
    val age: String,
    val hobbies: String
)