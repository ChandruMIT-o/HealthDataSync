package com.samsung.health.hrdatatransfer.data

import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.tasks.await
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MessageRepositoryImpl"

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageClient: MessageClient,
) : MessageRepository {
    override suspend fun sendMessage(message: String, node: Node, path: String): Boolean {
        return try {
            messageClient.sendMessage(
                node.id,
                path,
                message.toByteArray(StandardCharsets.UTF_8)
            ).await()
            Log.i(TAG, "Message sent successfully to ${node.displayName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to ${node.displayName}", e)
            false
        }
    }
}
