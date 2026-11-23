package com.streamtube.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.json.JSONObject

data class SponsorSegment(val category:String,val action:String,val start:Double,val end:Double,val uuid:String)
data class DeArrowBranding(val title:String?,val thumbnailTime:Double?)
data class DislikeVotes(val likes:Long,val dislikes:Long,val views:Long,val rating:Double)

object YouTubeServices {
 private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
 private val brandingCache=ConcurrentHashMap<String,DeArrowBranding?>()
 private val votesCache=ConcurrentHashMap<String,DislikeVotes>()
 private fun shaPrefix(id:String)=MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(2).joinToString(""){"%02x".format(it)}

 suspend fun sponsorSegments(videoId:String):List<SponsorSegment> = withContext(Dispatchers.IO) {
  val cats=listOf("sponsor","selfpromo","interaction","intro","outro","preview","hook","music_offtopic","filler","poi_highlight","exclusive_access")
  val url="https://sponsor.ajay.app/api/skipSegments/${shaPrefix(videoId)}?categories=%5B"+cats.joinToString("%2C"){"%22$it%22"}+"%5D&actionTypes=%5B%22skip%22%2C%22mute%22%2C%22poi%22%2C%22full%22%5D"
  val req=Request.Builder().url(url).header("User-Agent","StreamTube-Android").build()
  http.newCall(req).execute().use { response ->
   if(response.code==404)return@withContext emptyList()
   if(!response.isSuccessful)error("SponsorBlock HTTP ${response.code}")
   val arr=org.json.JSONArray(response.body?.string().orEmpty())
   val out=mutableListOf<SponsorSegment>()
   for(i in 0 until arr.length()){
    val entry=arr.optJSONObject(i)?:continue
    if(entry.optString("videoID")!=videoId)continue
    val segments=entry.optJSONArray("segments")?:continue
    for(j in 0 until segments.length()){
     val seg=segments.optJSONObject(j)?:continue
     val range=seg.optJSONArray("segment")?:continue
     if(range.length()<2)continue
     val a=range.optDouble(0,-1.0);val b=range.optDouble(1,-1.0)
     if(a>=0&&b>a)out+=SponsorSegment(seg.optString("category"),seg.optString("actionType","skip"),a,b,seg.optString("UUID"))
    }
   }
   out.sortedBy{it.start}
  }
 }

 suspend fun deArrow(videoId:String):DeArrowBranding?=withContext(Dispatchers.IO){
  if(brandingCache.containsKey(videoId))return@withContext brandingCache[videoId]
  val req=Request.Builder().url("https://sponsor.ajay.app/api/branding/${shaPrefix(videoId)}").header("User-Agent","StreamTube-Android").build()
  val result=runCatching{http.newCall(req).execute().use{res->
   if(!res.isSuccessful)return@use null
   val item=JSONObject(res.body?.string().orEmpty()).optJSONObject(videoId)?:return@use null
   var title:String?=null
   val titles=item.optJSONArray("titles")
   if(titles!=null)for(i in 0 until titles.length()){
    val t=titles.optJSONObject(i)?:continue
    if(!t.optBoolean("original",false)&&(t.optBoolean("locked",false)||t.optInt("votes",-1)>=0)){
     title=t.optString("title").replace(">","").trim();if(!title.isNullOrBlank())break
    }
   }
   var time:Double?=null
   val thumbs=item.optJSONArray("thumbnails")
   if(thumbs!=null)for(i in 0 until thumbs.length()){
    val t=thumbs.optJSONObject(i)?:continue
    if(!t.optBoolean("original",false)){time=t.optDouble("timestamp");break}
   }
   DeArrowBranding(title,time)
  }}.getOrNull()
  if(result!=null)brandingCache[videoId]=result
  result
 }

 suspend fun returnDislike(videoId:String):DislikeVotes?=withContext(Dispatchers.IO){
  votesCache[videoId]?.let{return@withContext it}
  val req=Request.Builder().url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId").header("User-Agent","StreamTube-Android").build()
  runCatching{http.newCall(req).execute().use{res->
   if(!res.isSuccessful)return@use null
   val v=JSONObject(res.body?.string().orEmpty())
   DislikeVotes(v.optLong("likes"),v.optLong("dislikes"),v.optLong("viewCount"),v.optDouble("rating"))
  }}.getOrNull()?.also{votesCache[videoId]=it}
 }

 suspend fun reportSponsorViewed(uuid:String)=withContext(Dispatchers.IO){
  if(uuid.isBlank())return@withContext
  val req=Request.Builder().url("https://sponsor.ajay.app/api/viewedVideoSponsorTime?UUID=$uuid")
   .post(okhttp3.RequestBody.create(null,ByteArray(0))).build()
  runCatching{http.newCall(req).execute().close()}
 }
}
