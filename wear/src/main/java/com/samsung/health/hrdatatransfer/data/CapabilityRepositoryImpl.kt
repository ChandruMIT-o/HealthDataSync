package com.samsung.health.hrdatatransfer.data

import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CapabilitiesRepoImpl"

/**
 * Implements the repository for discovering capabilities of connected nodes (e.g., the phone).
 * It uses the Google Wearable API's CapabilityClient to find all reachable nodes.
 */
@Singleton
class CapabilityRepositoryImpl @Inject constructor(
    private val capabilityClient: CapabilityClient
) : CapabilityRepository {
    /**
     * Fetches all capabilities for all currently reachable nodes.
     * @return A map where each key is a reachable Node and the value is a set of strings
     * representing the capabilities that the node has advertised. Returns an empty map on failure.
     */
    override suspend fun getCapabilitiesForReachableNodes(): Map<Node, Set<String>> {
        Log.i(TAG, "Fetching capabilities for reachable nodes...")
        return try {
            // Get all capabilities advertised by any node that is currently reachable.
            val allCapabilities =
                capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE).await()

            // The result from the API groups by capability. We need to reverse this to group by node.
            // We transform the data structure from Map<String, CapabilityInfo> to Map<Node, Set<String>>.
            allCapabilities.flatMap { (capability, capabilityInfo) ->
                // For each capability, map its nodes to the capability string.
                capabilityInfo.nodes.map { node ->
                    node to capability
                }
            }
                .groupBy(
                    keySelector = { it.first }, // Group by the Node object.
                    valueTransform = { it.second } // The values will be the capability strings.
                )
                .mapValues { it.value.toSet() } // Convert the list of strings to a set to remove duplicates.
        } catch (e: Exception) {
            Log.e(TAG, "getCapabilitiesForReachableNodes failed with an exception.", e)
            emptyMap() // Return an empty map if the API call fails.
        }
    }
}

