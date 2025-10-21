package com.samsung.health.hrdatatransfer.data

import com.google.android.gms.wearable.Node

interface MessageRepository {
    suspend fun sendMessage(message: String, node: Node, path: String): Boolean
}
