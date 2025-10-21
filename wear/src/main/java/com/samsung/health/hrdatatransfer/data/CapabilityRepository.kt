package com.samsung.health.hrdatatransfer.data

import com.google.android.gms.wearable.Node

interface CapabilityRepository {
    suspend fun getCapabilitiesForReachableNodes(): Map<Node, Set<String>>
}
