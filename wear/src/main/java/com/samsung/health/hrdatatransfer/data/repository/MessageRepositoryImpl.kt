package com.samsung.health.hrdatatransfer.data.repository

import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
// --- CRITICAL IMPORT FIX ---
import com.samsung.health.hrdatatransfer.domain.repository.MessageRepository
// ---------------------------
import kotlinx.coroutines.tasks.await
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MessageRepositoryImpl"

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageClient: MessageClient,
) : MessageRepository {

    // This overrides the function defined in the Interface above
    override suspend fun sendMessage(message: String, node: Node, path: String): Boolean {
        return try {
            messageClient.sendMessage(
                node.id,
                path,
                message.toByteArray(StandardCharsets.UTF_8)
            ).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending string message to ${node.displayName}", e)
            false
        }
    }

    // This overrides the byte function
    override suspend fun sendMessageBytes(data: ByteArray, node: Node, path: String): Boolean {
        return try {
            messageClient.sendMessage(
                node.id,
                path,
                data
            ).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending RAW BYTES to ${node.displayName}", e)
            false
        }
    }
}