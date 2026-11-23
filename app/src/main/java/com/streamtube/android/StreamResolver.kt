package com.streamtube.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class HlsVariant(val url:String,val width:Int,val height:Int,val fps:Int,val bitrate:Long,val label:String)
data class HlsAudioTrack(val name:String,val language:String,val isDefault:Boolean)
data class DownloadFormat(val label:String,val url:String,val mimeType:String,val extension:String,val height:Int=0,val audioOnly:Boolean=false,val userAgent:String="",val visitorData:String="",val sizeBytes:Long=0L,val videoOnly:Boolean=false,val audioUrl:String?=null,val audioMimeType:String?=null,val audioUserAgent:String?=null,val audioVisitorData:String?=null,val audioSizeBytes:Long=0L)
data class ResolvedStream(
    val hlsUrl:String,
    val userAgent:String,
    val source:String,
    val variants:List<HlsVariant> = emptyList(),
    val audioTracks:List<HlsAudioTrack> = emptyList(),
    val isLive:Boolean = false,
    val mimeType:String = "application/x-mpegURL"
)

object StreamResolver {
    private const val PLAYER_URL="https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private const val VISION_VERSION="1.02"
    private const val IOS_VERSION="20.10.4"
    private const val VR_VERSION="1.65.10"
    private const val VISION_UA="Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
    private const val IOS_UA="com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
    private const val VR_UA="com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private val http=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS)
        .callTimeout(45,TimeUnit.SECONDS).followRedirects(true).build()
    private val cache=ConcurrentHashMap<String,Pair<ResolvedStream,Long>>()
    private const val CACHE_TTL=4*60*60*1000L
    @Volatile private var visitorData:String?=null
    @Volatile private var iosVisitor:String=""

    suspend fun resolve(videoId:String):ResolvedStream=withContext(Dispatchers.IO){
        cache[videoId]?.let{if(System.currentTimeMillis()-it.second<CACHE_TTL&&!it.first.isLive)return@withContext it.first else cache.remove(videoId)}
        val failures=mutableListOf<String>()
        val resolved=try {
            resolveVision(videoId)
        } catch(t:Throwable){failures+="visionOS: ${t.message}";null}
        val result=resolved ?: try {
            resolveIos(videoId)
        } catch(t:Throwable){failures+="iOS: ${t.message}";null}
        val final=result ?: try {
            resolveAndroidVr(videoId)
        } catch(t:Throwable){failures+="Android VR: ${t.message}";null}
        val stream=final ?: error("No playable stream was returned. ${failures.joinToString(" | ")}")
        if(!stream.isLive)cache[videoId]=stream to System.currentTimeMillis()
        stream
    }

    fun clearCache(){cache.clear()}

    suspend fun downloadFormats(videoId:String):List<DownloadFormat> = withContext(Dispatchers.IO){
        val visitor=fetchVisitorData().orEmpty()
        val clients=listOf(
            Triple("ANDROID_VR",VR_VERSION,"28"),
            Triple("IOS",IOS_VERSION,"5"),
            Triple("WEB","2.20251120.01.00","1")
        )
        val output=mutableListOf<DownloadFormat>()
        for((name,version,clientId) in clients){
            val ua=when(name){"IOS"->IOS_UA;"WEB"->"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";else->VR_UA}
            try{
                val client=when(name){
                    "IOS"->JSONObject().put("clientName",name).put("clientVersion",version).put("deviceMake","Apple").put("deviceModel","iPhone16,2").put("osName","iPhone").put("osVersion","18.3.2")
                    "WEB"->JSONObject().put("clientName",name).put("clientVersion",version).put("hl","en").put("gl","US")
                    else->JSONObject().put("clientName",name).put("clientVersion",version).put("deviceMake","Oculus").put("deviceModel","Quest 3").put("androidSdkVersion",32).put("osName","Android").put("osVersion","12L")
                }.put("userAgent",ua).put("visitorData",visitor)
                val json=playerRequest(videoId,client,clientId,version,ua,visitor)
                val streaming=json.optJSONObject("streamingData")?:continue
                val duration=json.optJSONObject("videoDetails")?.optLong("lengthSeconds",0L)?:0L
                data class Candidate(val url:String,val mime:String,val ext:String,val height:Int,val size:Long,val bitrate:Long,val ua:String,val visitor:String,val progressive:Boolean)
                fun parse(item:JSONObject,progressive:Boolean):Candidate?{
                    val mime=item.optString("mimeType").substringBefore(";")
                    if(!mime.startsWith("audio/")&&!mime.startsWith("video/"))return null
                    val raw=item.optString("url")
                    val url=if(raw.startsWith("https://"))raw else try{
                        val cipher=item.optString("signatureCipher").ifBlank{item.optString("cipher")}
                        val params=cipher.split("&").mapNotNull{part->val ix=part.indexOf("=");if(ix<0)null else java.net.URLDecoder.decode(part.substring(0,ix),"UTF-8") to java.net.URLDecoder.decode(part.substring(ix+1),"UTF-8")}.toMap()
                        val base=params["url"].orEmpty();val sig=params["sig"]?:params["signature"]
                        if(base.startsWith("https://")&&sig!=null)base+(if(base.contains("?"))"&" else "?")+(params["sp"]?:"signature")+"="+java.net.URLEncoder.encode(sig,"UTF-8") else ""
                    }catch(_:Throwable){""}
                    if(url.isBlank())return null
                    val audio=mime.startsWith("audio/")
                    val ext=when{
                        audio&&(mime.contains("mp4")||mime.contains("aac"))->"m4a"
                        audio->"webm"
                        mime.contains("webm")->"webm"
                        else->"mp4"
                    }
                    val size=item.optString("contentLength").toLongOrNull()?.takeIf{it>0}
                        ?: if(item.optLong("bitrate")>0&&duration>0)item.optLong("bitrate")*duration/8L else 0L
                    return Candidate(url,mime,ext,item.optInt("height",0),size,item.optLong("bitrate",0L),ua,visitor,progressive)
                }
                fun candidates(array:org.json.JSONArray?,progressive:Boolean):List<Candidate>{
                    val out=mutableListOf<Candidate>()
                    if(array!=null)for(i in 0 until array.length())parse(array.optJSONObject(i)?:continue,progressive)?.let{out+=it}
                    return out
                }
                val progressive=candidates(streaming.optJSONArray("formats"),true)
                val adaptive=candidates(streaming.optJSONArray("adaptiveFormats"),false)
                // Keep one best audio track per real container; the UI exposes only M4A and WebM.
                val audioTracks=adaptive.filter{it.mime.startsWith("audio/")}
                val bestM4a=audioTracks.filter{it.ext=="m4a"}.maxByOrNull{it.bitrate}
                val bestWebm=audioTracks.filter{it.ext=="webm"}.maxByOrNull{it.bitrate}
                // Progressive files already contain audio. Add adaptive MP4 video only when a matching M4A track exists.
                progressive.filter{it.mime.startsWith("video/")}.forEach{v->
                    output+=DownloadFormat("${v.height}p · MP4",v.url,v.mime,v.ext,v.height,false,v.ua,v.visitor,v.size,false)
                }
                val audioForMp4=bestM4a
                if(audioForMp4!=null){
                    adaptive.filter{it.mime.startsWith("video/")&&it.ext=="mp4"&&it.height>0}
                        .groupBy{it.height}.values.forEach{sameQuality->
                            val v=sameQuality.maxByOrNull{it.bitrate}?:return@forEach
                            output+=DownloadFormat("${v.height}p · MP4",v.url,v.mime,"mp4",v.height,false,v.ua,v.visitor,
                                v.size+audioForMp4.size,false,audioForMp4.url,audioForMp4.mime,audioForMp4.ua,audioForMp4.visitor,audioForMp4.size)
                        }
                }
                listOfNotNull(bestM4a,bestWebm).forEach{a->
                    output+=DownloadFormat("Audio only · ${a.ext}",a.url,a.mime,a.ext,0,true,a.ua,a.visitor,a.size,false)
                }
            }catch(_:Throwable){/* try next client */}
        }
        val videos=output.filter{!it.audioOnly}.groupBy{it.height to it.extension}.values.mapNotNull{group->group.maxByOrNull{it.sizeBytes}}
        val audio=output.filter{it.audioOnly}.groupBy{it.extension}.values.mapNotNull{group->group.maxByOrNull{it.sizeBytes}}
        (videos+audio).sortedWith(compareBy<DownloadFormat>{it.audioOnly}.thenByDescending{it.height}.thenBy{it.extension})
    }


    private fun fetchVisitorData():String?{
        visitorData?.let{return it}
        return try{
            val context=JSONObject().put("client",JSONObject().put("clientName","WEB").put("clientVersion","2.20251120.01.00").put("hl","en").put("gl","US"))
            val body=JSONObject().put("context",context).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req=Request.Builder().url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                .header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
                .post(body).build()
            http.newCall(req).execute().use{res->
                if(!res.isSuccessful)return null
                JSONObject(res.body?.string().orEmpty()).optJSONObject("responseContext")?.optString("visitorData")
                    ?.takeIf{it.isNotBlank()}?.also{visitorData=it}
            }
        }catch(_:Throwable){null}
    }

    private fun resolveVision(id:String):ResolvedStream{
        val visitor=fetchVisitorData().orEmpty()
        val client=JSONObject().put("clientName","VISIONOS").put("clientVersion",VISION_VERSION)
            .put("deviceMake","Apple").put("deviceModel","RealityDevice17,1")
            .put("osName","visionOS").put("osVersion","26.5.23O471")
            .put("userAgent",VISION_UA).put("hl","en").put("gl","US")
        if(visitor.isNotBlank())client.put("visitorData",visitor)
        val json=playerRequest(id,client,"101",VISION_VERSION,VISION_UA,visitor)
        return parseHls(json,VISION_UA,"visionOS")
    }

    private fun resolveIos(id:String):ResolvedStream{
        var lastError="iOS did not return an HLS manifest"
        repeat(5){attempt->
            val kept=iosVisitor
            val visitor=if(attempt%2==0)kept else ""
            val client=JSONObject().put("clientName","IOS").put("clientVersion",IOS_VERSION)
                .put("deviceMake","Apple").put("deviceModel","iPhone16,2")
                .put("osName","iPhone").put("osVersion","18.3.2.22D82")
                .put("userAgent",IOS_UA).put("hl","en").put("gl","US")
            if(visitor.isNotBlank())client.put("visitorData",visitor)
            try{
                val json=playerRequest(id,client,"5",IOS_VERSION,IOS_UA,visitor)
                val hls=json.optJSONObject("streamingData")?.optString("hlsManifestUrl").orEmpty()
                if(hls.isNotBlank()){
                    if(visitor.isBlank())iosVisitor=json.optJSONObject("responseContext")?.optString("visitorData").orEmpty()
                    return parseHls(json,IOS_UA,"iOS")
                }
                lastError="iOS returned no HLS manifest (attempt ${attempt+1}/5)"
                if(kept.isNotBlank()&&visitor==kept&&attempt>=2)iosVisitor=""
            }catch(t:Throwable){lastError=t.message ?: "iOS request failed"}
        }
        error(lastError)
    }

    private fun resolveAndroidVr(id:String):ResolvedStream{
        val visitor=fetchVisitorData().orEmpty()
        val client=JSONObject().put("clientName","ANDROID_VR").put("clientVersion",VR_VERSION)
            .put("deviceMake","Oculus").put("deviceModel","Quest 3").put("androidSdkVersion",32)
            .put("osName","Android").put("osVersion","12L").put("hl","en").put("gl","US")
            .put("userAgent",VR_UA).put("visitorData",visitor)
        val json=playerRequest(id,client,"28",VR_VERSION,VR_UA,visitor)
        val hls=json.optJSONObject("streamingData")?.optString("hlsManifestUrl").orEmpty()
        if(hls.isNotBlank())return parseHls(json,VR_UA,"Android VR")
        val formats=json.optJSONObject("streamingData")?.optJSONArray("formats")
        if(formats!=null){
            for(i in 0 until formats.length()){
                val url=formats.optJSONObject(i)?.optString("url").orEmpty()
                if(url.isNotBlank()&&url.contains("mime=")) {
                    // The Android VR client may return signed direct formats. HLS remains preferred.
                    val mime = formats.optJSONObject(i)?.optString("mimeType").orEmpty().substringBefore(";")
                    return ResolvedStream(url,VR_UA,"Android VR direct",mimeType=mime.ifBlank{"video/mp4"})
                }
            }
        }
        error("Android VR returned no supported playback format")
    }

    private fun playerRequest(id:String,client:JSONObject,clientId:String,version:String,ua:String,visitor:String):JSONObject{
        val body=JSONObject().put("videoId",id).put("contentCheckOk",true).put("racyCheckOk",true)
            .put("context",JSONObject().put("client",client))
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req=Request.Builder().url(PLAYER_URL).header("User-Agent",ua).header("Origin","https://www.youtube.com")
            .header("X-YouTube-Client-Name",clientId).header("X-YouTube-Client-Version",version)
            .apply{if(visitor.isNotBlank())header("X-Goog-Visitor-Id",visitor)}.post(body).build()
        return http.newCall(req).execute().use{res->
            val raw=res.body?.string().orEmpty()
            if(!res.isSuccessful)error("HTTP ${res.code}: ${raw.take(180)}")
            val json=JSONObject(raw)
            val status=json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
            if(status.isNotBlank()&&status!="OK"){
                val reason=json.optJSONObject("playabilityStatus")?.optString("reason").orEmpty()
                error(reason.ifBlank{"YouTube playback status: $status"})
            }
            json
        }
    }

    private fun parseHls(json:JSONObject,ua:String,source:String):ResolvedStream{
        val streaming=json.optJSONObject("streamingData")?:error("$source returned no streaming data")
        val master=streaming.optString("hlsManifestUrl").takeIf{it.isNotBlank()}?:error("$source returned no HLS manifest")
        val playlist=getText(master,ua)
        val variants=mutableListOf<HlsVariant>()
        val audio=mutableListOf<HlsAudioTrack>()
        val lines=playlist.lines()
        for(i in lines.indices){
            val line=lines[i]
            if(line.startsWith("#EXT-X-MEDIA")&&line.contains("TYPE=AUDIO")){
                val name=attribute(line,"NAME")?:continue
                val language=attribute(line,"LANGUAGE").orEmpty()
                if(audio.none{it.name==name})audio+=HlsAudioTrack(name,language,name.contains("original",true))
            }
            if(line.startsWith("#EXT-X-STREAM-INF")){
                val url=lines.getOrNull(i+1)?.trim().orEmpty()
                if(url.isBlank()||url.startsWith("#"))continue
                val resolution=attribute(line,"RESOLUTION")?.split("x")
                val width=resolution?.getOrNull(0)?.toIntOrNull()?:0
                val height=resolution?.getOrNull(1)?.toIntOrNull()?:0
                val fps=attribute(line,"FRAME-RATE")?.toDoubleOrNull()?.toInt()?:0
                val bitrate=attribute(line,"BANDWIDTH")?.toLongOrNull()?:0L
                val quality=when{height>=2160->2160;height>=1440->1440;height>=1080->1080;height>=720->720;height>=480->480;height>=360->360;else->height}
                val label="${quality}p"+if(fps>30)"$fps" else ""
                val existing=variants.indexOfFirst{it.height==height&&it.fps==fps}
                val v=HlsVariant(url,width,height,fps,bitrate,label)
                if(existing<0)variants+=v else if(bitrate>variants[existing].bitrate)variants[existing]=v
            }
        }
        val videoDetails=json.optJSONObject("videoDetails")
        return ResolvedStream(master,ua,source,variants.sortedWith(compareByDescending<HlsVariant>{it.height}.thenByDescending{it.fps}.thenByDescending{it.bitrate}),
            if(audio.size>=2)audio else emptyList(),videoDetails?.optBoolean("isLiveContent",false)==true)
    }

    private fun attribute(line:String,key:String):String?{
        val marker="$key="
        val start=line.indexOf(marker)
        if(start<0)return null
        val value=line.substring(start+marker.length)
        return if(value.startsWith('"'))value.substring(1).substringBefore('"') else value.substringBefore(',')
    }

    private fun getText(url:String,ua:String):String{
        val req=Request.Builder().url(url).header("User-Agent",ua).header("Origin","https://www.youtube.com").build()
        return http.newCall(req).execute().use{res->if(!res.isSuccessful)error("HLS manifest HTTP ${res.code}");res.body?.string().orEmpty()}
    }
}
