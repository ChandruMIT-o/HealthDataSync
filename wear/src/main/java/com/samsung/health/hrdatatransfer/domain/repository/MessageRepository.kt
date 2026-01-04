package com.samsung.health.hrdatatransfer.domain.repository

import com.google.android.gms.wearable.Node

interface MessageRepository {
    // Both functions must be listed here for the "override" to work
    suspend fun sendMessage(message: String, node: Node, path: String): Boolean
    suspend fun sendMessageBytes(data: ByteArray, node: Node, path: String): Boolean
}