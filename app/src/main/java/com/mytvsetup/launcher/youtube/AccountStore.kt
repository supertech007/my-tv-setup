package com.mytvsetup.launcher.youtube

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.youtubePreferences by preferencesDataStore(name = "youtube")

data class AccountsState(val accounts: List<YtAccount> = emptyList(), val hidden: Set<String> = emptySet()) {
    val visibleAccounts: List<YtAccount> get() = accounts.filterNot { it.id in hidden }
}

/** No plaintext refresh/access tokens are written to DataStore. The app disables backup. */
class AccountStore(private val data: DataStore<Preferences>, private val cipher: TokenCipher = TokenCipher()) {
    constructor(context: Context) : this(context.applicationContext.youtubePreferences)
    private val key = stringPreferencesKey("youtube_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val snapshots: Flow<AccountSnapshot> = data.data.map { decode(it) }
    val state: Flow<AccountsState> = snapshots.map { snapshot ->
        AccountsState(snapshot.accounts.map { it.account }, snapshot.hidden)
    }
    internal val caches: Flow<Map<String, FeedCache>> = snapshots.map { it.caches }
    // Claims are serialized by DataStore; release also runs after cancellation or failed writes.
    private val refreshing = ConcurrentHashMap.newKeySet<String>()

    suspend fun add(account: YtAccount, token: Token) = saveToken(account, token, allowRetry = true)

    internal suspend fun rotateToken(account: YtAccount, token: Token) = saveToken(account, token, allowRetry = false)

    private suspend fun saveToken(account: YtAccount, token: Token, allowRetry: Boolean) = withContext(Dispatchers.IO) {
        val refresh = token.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw IOException("No refresh token. Add this account again.")
        val encrypted = cipher.encrypt(account.id, refresh)
        val stored = StoredAccount(account, encrypted)
        update { old ->
            old.copy(accounts = if (old.accounts.any { it.account.id == account.id }) {
                old.accounts.map { if (it.account.id == account.id) stored else it }
            } else old.accounts + stored,
                caches = if (!allowRetry) old.caches else old.caches.mapValues { (key, value) ->
                    if (key in setOf("latest:${account.id}", "liked:${account.id}", "playlists:${account.id}") ||
                        key.startsWith("playlist:${account.id}:")) value.copy(attemptAt = null) else value
                })
        }
    }

    suspend fun refreshToken(accountId: String): String = withContext(Dispatchers.IO) {
        val encrypted = credentialVersion(accountId)
        try { cipher.decrypt(accountId, encrypted) }
        catch (_: Exception) { throw IOException("YouTube credentials are unavailable. Add the account again.") }
    }

    internal suspend fun credentialVersion(accountId: String): String =
        snapshots.first().accounts.firstOrNull { it.account.id == accountId }?.encryptedToken
            ?: throw IOException("Add this YouTube account again.")

    suspend fun setHidden(accountId: String, hidden: Boolean) = update { old ->
        old.copy(hidden = if (hidden) old.hidden + accountId else old.hidden - accountId)
    }

    suspend fun isVisible(accountId: String): Boolean = state.first().visibleAccounts.any { it.id == accountId }

    /** Successful populated caches wait two hours; failed loads wait 15 minutes. Empty caches can retry. */
    internal suspend fun claim(cacheKey: String, accountId: String, now: Long): Boolean {
        var claimed = false
        try {
            update { old ->
                val cache = old.caches[cacheKey] ?: FeedCache()
                val populated = cache.videos.isNotEmpty() || cache.playlists.isNotEmpty()
                val backoff = if (cache.failed) 900_000L else 7_200_000L
                if (accountId in old.hidden || old.accounts.none { it.account.id == accountId } ||
                    cacheKey in refreshing ||
                    (populated && cache.attemptAt != null && now - cache.attemptAt < backoff)) old
                else {
                    claimed = true
                    refreshing += cacheKey
                    // Reserve as failed until a successful save, including an interrupted process.
                    old.copy(caches = old.caches + (cacheKey to cache.copy(attemptAt = now, failed = true)))
                }
            }
        } catch (failure: Throwable) {
            if (claimed) release(cacheKey)
            throw failure
        }
        return claimed
    }

    internal suspend fun saveCache(cacheKey: String, value: FeedCache) = update { old ->
        old.copy(caches = old.caches + (cacheKey to value.copy(attemptAt = old.caches[cacheKey]?.attemptAt)))
    }

    internal fun release(cacheKey: String) {
        refreshing -= cacheKey
    }

    private suspend fun update(change: (AccountSnapshot) -> AccountSnapshot) {
        data.edit { values -> values[key] = json.encodeToString(change(decode(values))) }
    }
    private fun decode(values: Preferences): AccountSnapshot = try {
        values[key]?.let { json.decodeFromString<AccountSnapshot>(it) } ?: AccountSnapshot()
    } catch (_: SerializationException) { AccountSnapshot() }
      catch (_: IllegalArgumentException) { AccountSnapshot() }
}

@Serializable private data class StoredAccount(val account: YtAccount, val encryptedToken: String)
@Serializable private data class AccountSnapshot(
    val accounts: List<StoredAccount> = emptyList(), val hidden: Set<String> = emptySet(),
    val caches: Map<String, FeedCache> = emptyMap()
)
@Serializable internal data class FeedCache(
    val videos: List<YtVideo> = emptyList(), val playlists: List<YtPlaylist> = emptyList(),
    val attemptAt: Long? = null, val failed: Boolean = false
)

/** AES/GCM with a fresh IV and account-bound authentication; the key never leaves Android Keystore. */
class TokenCipher(private val keyProvider: () -> SecretKey = ::androidTokenKey) {
    fun encrypt(accountId: String, token: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD(accountId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + encrypted)
    }
    fun decrypt(accountId: String, encrypted: String): String {
        val bytes = Base64.getDecoder().decode(encrypted)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(accountId.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}

@Synchronized private fun androidTokenKey(): SecretKey {
    val alias = "my_tv_youtube_refresh_v1"
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
    }.generateKey()
}
