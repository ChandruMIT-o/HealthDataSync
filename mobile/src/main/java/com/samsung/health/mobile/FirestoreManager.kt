package com.samsung.health.mobile

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreManager @Inject constructor() {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val TAG = "FirestoreManager"

    fun uploadMinuteBatch(batch: MinuteBatch) {
        val data = hashMapOf(
            "timestamp" to batch.startTimestamp,
            "date" to Date(batch.startTimestamp),
            "avg_hr" to batch.hrValues.average(),
            "avg_spo2" to batch.spo2Values.average(),
            "avg_rr" to batch.rrValues.average(),
            "avg_temp" to batch.tempValues.average(),
            "hr_series" to batch.hrValues,
            "spo2_series" to batch.spo2Values,
            "rr_series" to batch.rrValues,
            "movement_series" to batch.movementValues,
            "temp_series" to batch.tempValues,
            "ibi_series" to batch.ibiStream
        )

        db.collection("users")
            .document("default_user")
            .collection("processed_data")
            .document(batch.startTimestamp.toString())
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Uploaded ${batch.startTimestamp}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Upload failed", e)
            }
    }

    /**
     * Real-time listener for the daily generated metrics from the ST-GAT Cloud Function.
     * Uses a snapshot listener so the UI updates instantly without aggressive polling.
     */
    fun listenToTodayMetrics(onMetricsUpdated: (List<Map<String, Any>>) -> Unit) {
        // Get start of current day in milliseconds
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDayMs = calendar.timeInMillis

        db.collection("users")
            .document("default_user")
            .collection("metrics")
            .whereGreaterThanOrEqualTo("timestamp", startOfDayMs)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Failed to listen to metrics", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val docs = snapshot.documents.mapNotNull { it.data }
                    Log.d(TAG, "📥 Received ${docs.size} metric records for today")
                    onMetricsUpdated(docs)
                }
            }
    }
}