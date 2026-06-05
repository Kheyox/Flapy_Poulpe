package com.example.game

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val tagName: String, val changelog: String, val downloadUrl: String, val size: Long) : UpdateState()
    object NoUpdate : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    object ReadyToInstall : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class UpdateManager(private val context: Context) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Configurable owner and repo (defaults point to this project's actual GitHub repo)
    var githubOwner = "Kheyox"
    var githubRepo = "Flapy_Poulpe"

    /** The versionName baked into the currently installed APK (e.g. "1.0.42"). */
    val currentVersionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    fun checkUpdates() {
        _updateState.value = UpdateState.Checking
        scope.launch {
            try {
                val currentVersion = currentVersionName

                val url = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errMsg = if (response.code == 404) {
                            "Aucun 'Release' publié sur GitHub (404). Créez une Release publique avec votre fichier APK sur GitHub pour activer les mises à jour !"
                        } else {
                            "Impossible de se connecter à GitHub (${response.code})"
                        }
                        _updateState.value = UpdateState.Error(errMsg)
                        return@launch
                    }

                    val responseBody = response.body?.string() ?: ""
                    if (responseBody.isEmpty()) {
                        _updateState.value = UpdateState.Error("Réponse GitHub vide")
                        return@launch
                    }

                    val json = JSONObject(responseBody)
                    val tagName = json.optString("tag_name", "1.0").replace("v", "")
                    val changelog = json.optString("body", "Nouvelle mise à jour disponible !")
                    
                    // Parse assets to find release APK
                    val assetsArray = json.optJSONArray("assets")
                    var downloadUrl = ""
                    var sizeBytes = 0L

                    if (assetsArray != null) {
                        for (i in 0 until assetsArray.length()) {
                            val asset = assetsArray.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                sizeBytes = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }

                    if (downloadUrl.isEmpty()) {
                        // fallback to html_url release page if no APK built as asset
                        downloadUrl = json.optString("html_url", "https://github.com/$githubOwner/$githubRepo/releases")
                    }

                    // Compare tag names (e.g. 1.1 vs 1.0)
                    if (isNewerVersion(currentVersion, tagName)) {
                        _updateState.value = UpdateState.UpdateAvailable(
                            tagName = tagName,
                            changelog = changelog,
                            downloadUrl = downloadUrl,
                            size = sizeBytes
                        )
                    } else {
                        _updateState.value = UpdateState.NoUpdate
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = UpdateState.Error("Erreur de vérification : ${e.localizedMessage}")
            }
        }
    }

    private fun isNewerVersion(current: String, incoming: String): Boolean {
        try {
            val curParts = current.split(".").mapNotNull { it.toIntOrNull() }
            val incParts = incoming.split(".").mapNotNull { it.toIntOrNull() }
            
            val minLen = minOf(curParts.size, incParts.size)
            for (i in 0 until minLen) {
                if (incParts[i] > curParts[i]) return true
                if (incParts[i] < curParts[i]) return false
            }
            return incParts.size > curParts.size
        } catch (e: Exception) {
            // fallback: direct string compare
            return current != incoming
        }
    }

    fun downloadAndInstall(downloadUrl: String) {
        if (downloadUrl.contains("github.com") && !downloadUrl.contains("/download/")) {
            // It is general web release url, not a direct APK download
            // Open web explorer
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                _updateState.value = UpdateState.Idle
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Impossible d'ouvrir le navigateur : ${e.localizedMessage}")
            }
            return
        }

        _updateState.value = UpdateState.Downloading(0)
        scope.launch {
            try {
                val request = Request.Builder().url(downloadUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateState.Error("Échec du téléchargement (${response.code})")
                        return@launch
                    }

                    val body = response.body ?: throw Exception("Corps du message vide")
                    val contentLength = body.contentLength()
                    
                    val apkFile = File(context.cacheDir, "poulpe_update.apk")
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }

                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(apkFile)
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            _updateState.value = UpdateState.Downloading(progress)
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    _updateState.value = UpdateState.ReadyToInstall
                    triggerInstall(apkFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = UpdateState.Error("Erreur pendant le téléchargement : ${e.localizedMessage}")
            }
        }
    }

    fun triggerInstall(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(uri, "application/vnd.android.package-archive")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = UpdateState.Error("Échec du lancement de l'installation : ${e.localizedMessage}")
        }
    }
    
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
