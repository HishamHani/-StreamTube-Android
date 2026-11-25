package com.streamtube.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DeviceLogin(val deviceCode:String,val userCode:String,val url:String,val interval:Long,val expires:Long)
data class YouTubeAccount(val name:String,val handle:String?,val avatar:String?)

object YouTubeAuth{
 private const val CLIENT_ID="861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
 private const val CLIENT_SECRET="SboVhoG9s0rNafixCSGGKXAT"
 private const val SCOPE="https://www.googleapis.com/auth/youtube.readonly"
 private const val TV_VERSION="7.20251120.13.00"
 private const val TV_UA="Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"
 private val http=OkHttpClient.Builder().callTimeout(30,TimeUnit.SECONDS).build()
 private val jsonType="application/json; charset=utf-8".toMediaType()

 private fun prefs(context:Context)=EncryptedSharedPreferences.create(
  context,"youtube_auth",
  MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
  EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
  EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
 )
 fun isSignedIn(context:Context)=prefs(context).getString("refresh_token",null).orEmpty().isNotBlank()
 fun account(context:Context)=YouTubeAccount(
  prefs(context).getString("account_name","YouTube account")?:"YouTube account",
  prefs(context).getString("account_handle",null),
  prefs(context).getString("account_avatar",null)
 )
 fun signOut(context:Context)=prefs(context).edit().clear().apply()
 fun openVerification(context:Context,login:DeviceLogin){
  context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(login.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
 }

 private fun postJson(url:String,payload:JSONObject,headers:Map<String,String> = emptyMap()):JSONObject{
  val request=Request.Builder().url(url).header("User-Agent",TV_UA).header("Accept","application/json")
   .apply{headers.forEach{(k,v)->header(k,v)}}
   .post(payload.toString().toRequestBody(jsonType)).build()
  http.newCall(request).execute().use{response->
   val raw=response.body?.string().orEmpty()
   val json=if(raw.isBlank())JSONObject() else JSONObject(raw)
   if(!response.isSuccessful&&!json.has("error"))error("YouTube request HTTP ${response.code}")
   return json
  }
 }

 suspend fun begin():DeviceLogin=withContext(Dispatchers.IO){
  val payload=JSONObject().put("client_id",CLIENT_ID).put("scope",SCOPE)
   .put("device_id",UUID.randomUUID().toString().replace("-","")).put("device_model","ytlr::")
  val json=postJson("https://www.youtube.com/o/oauth2/device/code",payload)
  if(json.has("error"))error(json.optString("error_description",json.optString("error")))
  DeviceLogin(json.getString("device_code"),json.getString("user_code"),
   json.optString("verification_url","https://www.google.com/device"),
   json.optLong("interval",5),json.optLong("expires_in",1800))
 }

 suspend fun approve(context:Context,login:DeviceLogin):String=withContext(Dispatchers.IO){
  var interval=login.interval.coerceAtLeast(5)
  val endAt=System.currentTimeMillis()+login.expires*1000
  while(System.currentTimeMillis()<endAt){
   delay(interval*1000)
   val json=postJson("https://www.youtube.com/o/oauth2/token",JSONObject()
    .put("client_id",CLIENT_ID).put("client_secret",CLIENT_SECRET).put("code",login.deviceCode)
    .put("grant_type","http://oauth.net/grant_type/device/1.0"))
   when(json.optString("error")){
    "authorization_pending"->Unit
    "slow_down"->interval+=5
    "expired_token"->error("The code expired. Try again.")
    "access_denied"->error("Sign-in was cancelled.")
    else->{
     val access=json.optString("access_token")
     if(access.isNotBlank()){
      val refresh=json.optString("refresh_token")
      prefs(context).edit().putString("access_token",access).putString("refresh_token",refresh)
       .putLong("expires_at",System.currentTimeMillis()+json.optLong("expires_in",3600)*1000-60000).apply()
      val account=runCatching{fetchAccount(context)}.getOrNull()
      if(account!=null)prefs(context).edit().putString("account_name",account.name)
       .putString("account_handle",account.handle).putString("account_avatar",account.avatar).apply()
      return@withContext "Connected: ${account?.name ?: "YouTube account"}"
     }
     error(json.optString("error_description","Sign-in failed"))
    }
   }
  }
  error("The sign-in code expired. Please try again.")
 }

 private fun accessToken(context:Context):String?{
  val p=prefs(context)
  val access=p.getString("access_token",null)
  val refresh=p.getString("refresh_token",null).orEmpty()
  if(!access.isNullOrBlank()&&System.currentTimeMillis()<p.getLong("expires_at",0))return access
  if(refresh.isBlank())return access
  val json=postJson("https://www.youtube.com/o/oauth2/token",JSONObject()
   .put("client_id",CLIENT_ID).put("client_secret",CLIENT_SECRET)
   .put("refresh_token",refresh).put("grant_type","refresh_token"))
  val renewed=json.optString("access_token")
  if(renewed.isBlank()){
   if(json.optString("error") in listOf("invalid_grant","unauthorized_client"))signOut(context)
   return null
  }
  p.edit().putString("access_token",renewed)
   .putLong("expires_at",System.currentTimeMillis()+json.optLong("expires_in",3600)*1000-60000).apply()
  return renewed
 }

 suspend fun tvCall(context:Context,endpoint:String,body:JSONObject):JSONObject=withContext(Dispatchers.IO){
  val token=accessToken(context)?:error("Not signed in. Please connect your YouTube account.")
  val payload=JSONObject(body.toString()).put("context",JSONObject().put("client",JSONObject()
   .put("clientName","TVHTML5").put("clientVersion",TV_VERSION).put("hl","en").put("gl","US")))
  postJson("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false",payload,
   mapOf("Authorization" to "Bearer $token","X-YouTube-Client-Name" to "7",
    "X-YouTube-Client-Version" to TV_VERSION,"Origin" to "https://www.youtube.com"))
 }

 suspend fun googleApiGet(context:Context,url:String):JSONObject=withContext(Dispatchers.IO){
  val token=accessToken(context)?:error("Not signed in. Please reconnect your YouTube account.")
  val request=Request.Builder().url(url).header("Authorization","Bearer $token")
   .header("Accept","application/json").header("User-Agent",TV_UA).get().build()
  http.newCall(request).execute().use{response->
   val raw=response.body?.string().orEmpty()
   val json=if(raw.isBlank())JSONObject() else JSONObject(raw)
   if(!response.isSuccessful){
    val message=json.optJSONObject("error")?.optString("message").orEmpty()
    error("YouTube account API HTTP ${response.code}: ${message.ifBlank{"Request failed"}}")
   }
   json
  }
 }

 private suspend fun fetchAccount(context:Context):YouTubeAccount{
  val response=googleApiGet(context,"https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true")
  val item=response.optJSONArray("items")?.optJSONObject(0)?:return YouTubeAccount("YouTube account",null,null)
  val snippet=item.optJSONObject("snippet")
  val name=snippet?.optString("title").orEmpty().ifBlank{"YouTube account"}
  val handle=snippet?.optString("customUrl")?.takeIf{it.isNotBlank()}
  val thumbs=snippet?.optJSONObject("thumbnails")
  val avatar=listOf("maxres","high","medium","default").firstNotNullOfOrNull{thumbs?.optJSONObject(it)?.optString("url")?.takeIf{url->url.startsWith("http")}}
  return YouTubeAccount(name,handle,avatar)
 }

 private fun findObject(root:JSONObject,key:String):JSONObject?{
  val keys=root.keys()
  while(keys.hasNext()){
   val value=root.opt(keys.next())
   if(value is JSONObject){
    if(value.has(key))return value.optJSONObject(key)
    findObject(value,key)?.let{return it}
   }else if(value is org.json.JSONArray){
    for(i in 0 until value.length())value.optJSONObject(i)?.let{findObject(it,key)?.let{return it}}
   }
  }
  return null
 }
 private fun readText(value:Any?):String{
  if(value !is JSONObject)return value?.toString().orEmpty()
  value.optString("simpleText").takeIf{it.isNotBlank()}?.let{return it}
  val runs=value.optJSONArray("runs")?:return ""
  return buildString{for(i in 0 until runs.length())append(runs.optJSONObject(i)?.optString("text").orEmpty())}
 }
 private fun bestThumbnail(obj:JSONObject?):String?{
  val arr=obj?.optJSONArray("thumbnails")?:return null
  return arr.optJSONObject((arr.length()-1).coerceAtLeast(0))?.optString("url")?.takeIf{it.startsWith("http")}
 }
}
