package com.fenrir.her.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import com.fenrir.her.auth.CoupleAuthGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.fenrir.her.LocalPlayerAwareWindowInsets
import com.fenrir.her.R
import com.fenrir.her.constants.AccountChannelHandleKey
import com.fenrir.her.constants.AccountEmailKey
import com.fenrir.her.constants.AccountNameKey
import com.fenrir.her.constants.DataSyncIdKey
import com.fenrir.her.constants.InnerTubeCookieKey
import com.fenrir.her.constants.SavedAccountsKey
import com.fenrir.her.constants.VisitorDataKey
import com.fenrir.her.models.AccountData
import com.fenrir.her.ui.component.IconButton
import com.fenrir.her.ui.utils.backToMain
import com.fenrir.her.utils.rememberPreference
import com.fenrir.her.utils.reportException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun LoginScreen(
  navController: NavController,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var visitorData by rememberPreference(VisitorDataKey, "")
  var dataSyncId by rememberPreference(DataSyncIdKey, "")
  var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
  var accountName by rememberPreference(AccountNameKey, "")
  var accountEmail by rememberPreference(AccountEmailKey, "")
  var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
  var savedAccountsJson by rememberPreference(SavedAccountsKey, "[]")
  var customMyName by rememberPreference(com.fenrir.her.constants.CoupleMyNameKey, "")
  var hasCompletedLogin by remember { mutableStateOf(false) }

  var webView: WebView? = null

  Column(modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current).fillMaxSize()) {
    TopAppBar(
      title = { Text(stringResource(R.string.login)) },
      navigationIcon = {
        IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
          Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
        }
      }
    )


    androidx.compose.material3.Surface(
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
      shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
      androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          painter = painterResource(R.drawable.favorite),
          contentDescription = null,
          tint = androidx.compose.ui.graphics.Color(0xFFE91E63),
          modifier = Modifier.size(24.dp)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column {
          Text(
            text = "YouTube Music & Couple Sync",
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
          )
          Text(
            text = "Sign in with your Google account to sync your playlists and connect with your partner.",
            style = MaterialTheme.typography.bodySmall
          )
        }
      }
    }

    AndroidView(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      factory = { webViewContext ->
        WebView(webViewContext).apply {
          webViewClient =
            object : WebViewClient() {
              override fun onPageFinished(view: WebView, url: String?) {
                loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")

                if (url?.startsWith("https://music.youtube.com") == true && !hasCompletedLogin) {
                  innerTubeCookie = CookieManager.getInstance().getCookie(url)
                  hasCompletedLogin = true

                  coroutineScope.launch {
                    delay(500)

                    YouTube.cookie = innerTubeCookie
                    YouTube.dataSyncId = dataSyncId
                    YouTube.visitorData = visitorData

                    YouTube.accountInfo()
                      .onSuccess {


                        accountName = it.name
                        accountEmail = it.email.orEmpty().ifBlank { it.channelHandle.orEmpty() }
                        accountChannelHandle = it.channelHandle.orEmpty()
                        if (customMyName.isBlank()) {
                          customMyName = it.name
                        }

                        val newAccount =
                          AccountData(
                            name = it.name,
                            email = accountEmail,
                            channelHandle = it.channelHandle.orEmpty(),
                            cookie = innerTubeCookie,
                            visitorData = visitorData,
                            dataSyncId = dataSyncId,
                            avatarUrl = it.thumbnailUrl.orEmpty()
                          )
                        val accounts =
                          try {
                              Json.decodeFromString<List<AccountData>>(savedAccountsJson)
                            } catch (e: Exception) {
                              emptyList()
                            }
                            .toMutableList()
                        accounts.removeAll { acc -> acc.name == newAccount.name }
                        accounts.add(newAccount)
                        savedAccountsJson = Json.encodeToString(accounts)

                        Timber.d("Login: Successfully logged in as ${it.name}")

                        withContext(Dispatchers.Main) {
                          webView?.apply {
                            stopLoading()
                            clearHistory()
                            clearCache(true)
                            clearFormData()
                          }
                          Toast.makeText(context, "Signed in as ${it.name}!", Toast.LENGTH_SHORT).show()
                          val popped = navController.popBackStack()
                          if (!popped) {
                            navController.navigate("couple_space") {
                              popUpTo(Screens.Home.route) { inclusive = false }
                            }
                          }
                        }
                      }
                      .onFailure {
                        Timber.e(it, "Login: Authentication validation failed")
                        hasCompletedLogin = false
                        reportException(it)
                      }
                  }
                }
              }
            }
          settings.apply {
            javaScriptEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
          }
          addJavascriptInterface(
            object {
              @JavascriptInterface
              fun onRetrieveVisitorData(newVisitorData: String?) {
                if (newVisitorData != null) {
                  visitorData = newVisitorData
                }
              }

              @JavascriptInterface
              fun onRetrieveDataSyncId(newDataSyncId: String?) {
                if (newDataSyncId != null) {
                  dataSyncId = newDataSyncId.substringBefore("||")
                }
              }
            },
            "Android"
          )
          webView = this

          CookieManager.getInstance().removeAllCookies(null)
          CookieManager.getInstance().flush()
          loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
        }
      }
    )
  }

  BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }
}
