package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.data.local.ClonedAppEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ClonixCloudRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google sebelum mengakses Firestore.")
    }

    suspend fun ensureUserProfile(displayName: String, defaultRegionCode: String, strictAntiDetect: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.ifBlank { "Clonix User" }.take(120),
                    "defaultRegionCode" to defaultRegionCode.take(10),
                    "strictAntiDetect" to strictAntiDetect,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to displayName.ifBlank { "Clonix User" }.take(120),
                        "defaultRegionCode" to defaultRegionCode.take(10),
                        "strictAntiDetect" to strictAntiDetect,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeCloudClones(userId: String): Flow<List<CloudCloneRecord>> = flow {
        val path = "users/$userId/clones"
        emitAll(
            db.collection("users").document(userId).collection("clones")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudCloneRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncCloneToCloud(entity: ClonedAppEntity): Result<String> {
        val uid = requireUserId()
        val sanitizedId = entity.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val docRef = db.collection("users").document(uid).collection("clones").document(sanitizedId)
        val proxySummary = if (entity.proxyEnabled && entity.proxyHost.isNotBlank()) {
            "${entity.proxyType} ${entity.proxyHost}:${entity.proxyPort}"
        } else {
            "Direct"
        }

        val payload = mapOf(
            "userId" to uid,
            "packageName" to entity.packageName.take(200),
            "originalAppName" to entity.originalAppName.take(120),
            "cloneLabel" to entity.cloneLabel.take(160),
            "instanceNumber" to entity.instanceNumber.coerceIn(1, 999),
            "devicePlatform" to entity.devicePlatform,
            "regionCode" to entity.regionCode.take(10),
            "regionName" to entity.regionName.take(80),
            "city" to entity.city.take(80),
            "timezoneId" to entity.timezoneId.take(80),
            "localeTag" to entity.localeTag.take(20),
            "carrierName" to entity.carrierName.take(80),
            "mccMnc" to entity.mccMnc.take(20),
            "virtualModel" to entity.virtualModel.take(100),
            "virtualManufacturer" to entity.virtualManufacturer.take(80),
            "virtualAndroidId" to entity.virtualAndroidId.take(64),
            "virtualFingerprintHash" to entity.virtualFingerprintHash.take(64),
            "launchMode" to entity.launchMode,
            "targetWebUrl" to entity.targetWebUrl.take(500),
            "proxyEnabled" to entity.proxyEnabled,
            "proxySummary" to proxySummary.take(200),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updateMap = mapOf(
                    "cloneLabel" to entity.cloneLabel.take(160),
                    "devicePlatform" to entity.devicePlatform,
                    "regionCode" to entity.regionCode.take(10),
                    "regionName" to entity.regionName.take(80),
                    "city" to entity.city.take(80),
                    "timezoneId" to entity.timezoneId.take(80),
                    "localeTag" to entity.localeTag.take(20),
                    "carrierName" to entity.carrierName.take(80),
                    "mccMnc" to entity.mccMnc.take(20),
                    "virtualModel" to entity.virtualModel.take(100),
                    "virtualManufacturer" to entity.virtualManufacturer.take(80),
                    "virtualAndroidId" to entity.virtualAndroidId.take(64),
                    "virtualFingerprintHash" to entity.virtualFingerprintHash.take(64),
                    "launchMode" to entity.launchMode,
                    "targetWebUrl" to entity.targetWebUrl.take(500),
                    "proxyEnabled" to entity.proxyEnabled,
                    "proxySummary" to proxySummary.take(200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            } else {
                docRef.set(payload).await()
            }
            Result.success(sanitizedId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudClone(cloneId: String): Result<Unit> {
        val uid = requireUserId()
        val sanitizedId = cloneId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val docRef = db.collection("users").document(uid).collection("clones").document(sanitizedId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun pushAuditLog(cloneId: String, eventType: String, title: String, detail: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("auditLogs").document()
        val payload = mapOf(
            "userId" to uid,
            "cloneId" to cloneId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128),
            "eventType" to eventType.take(40),
            "title" to title.take(160),
            "detail" to detail.take(400),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
