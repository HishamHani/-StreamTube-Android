package com.streamtube.android

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Video(
    val id:String,val title:String,val channel:String,val thumbnail:String,val duration:String,
    val channelId:String?=null,val views:String="",val published:String="",val description:String="",
    val isLive:Boolean=false,val isShort:Boolean=false,val isUpcoming:Boolean=false
){val watchUrl:String get()="https://www.youtube.com/watch?v=$id"}

data class SearchPage(val videos:List<Video>,val continuation:String?=null)
data class VideoComment(val id:String,val author:String,val text:String,val likes:String,val published:String)
data class VideoNextPage(val related:List<Video>,val comments:List<VideoComment>,val commentContinuation:String?)

data class SearchFilter(
    val sort:Int=0,val date:Int=0,val kind:Int=0,val duration:Int=0,
    val hd:Boolean=false,val subtitles:Boolean=false,val creativeCommons:Boolean=false,
    val live:Boolean=false,val fourK:Boolean=false,val hdr:Boolean=false
){
    fun encode():String?{
        if(this==SearchFilter())return null
        val inner=mutableListOf<Byte>()
        if(date>0)field(inner,1,date)
        if(kind>0)field(inner,2,kind)
        if(duration>0)field(inner,3,duration)
        if(hd)field(inner,4,1)
        if(subtitles)field(inner,5,1)
        if(creativeCommons)field(inner,6,1)
        if(live)field(inner,8,1)
        if(fourK)field(inner,14,1)
        if(hdr)field(inner,25,1)
        val out=mutableListOf<Byte>()
        if(sort>0)field(out,1,sort)
        if(inner.isNotEmpty()){
            varint(out,(2 shl 3) or 2);varint(out,inner.size)
            out.addAll(inner)
        }
        return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
    }
    private fun field(out:MutableList<Byte>,number:Int,value:Int){varint(out,number shl 3);varint(out,value)}
    private fun varint(out:MutableList<Byte>,value:Int){
        var v=value
        while(true){val b=v and 0x7f;v=v ushr 7;if(v==0){out+=b.toByte();break}else out+=(b or 0x80).toByte()}
    }
}

object YouTubeApi{
    private const val BASE="https://www.youtube.com/youtubei/v1"
    private const val CLIENT_VERSION="2.20251120.01.00"
    private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build()

    suspend fun search(query:String):List<Video> = searchPage(query).videos

    suspend fun searchPage(query:String,filter:SearchFilter=SearchFilter()):SearchPage=withContext(Dispatchers.IO){
        val body=JSONObject().put("context",webContext()).put("query",query)
        filter.encode()?.let{body.put("params",it)}
        parsePage(call("search",body))
    }

    suspend fun continueSearch(token:String):SearchPage=withContext(Dispatchers.IO){
        parsePage(call("search",JSONObject().put("context",webContext()).put("continuation",token)))
    }

    suspend fun suggestions(query:String):List<String> = withContext(Dispatchers.IO){
        val q=URLEncoder.encode(query,"UTF-8")
        val url="https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&hl=en&gl=US&q=$q"
        val first=runCatching{
            val req=Request.Builder().url(url).header("User-Agent",browserUa()).build()
            client.newCall(req).execute().use{res->
                val raw=res.body?.string().orEmpty()
                val a=raw.substringAfter('(').substringBeforeLast(')')
                val root=JSONArray(a);val items=root.optJSONArray(1)?:JSONArray()
                (0 until items.length()).mapNotNull{i->items.optJSONArray(i)?.optString(0)?.takeIf{it.isNotBlank()}}
            }
        }.getOrDefault(emptyList())
        if(first.isNotEmpty())return@withContext first
        val fallback="https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=en&gl=US&q=$q"
        val req=Request.Builder().url(fallback).header("User-Agent",browserUa()).build()
        client.newCall(req).execute().use{res->
            val arr=JSONArray(res.body?.string().orEmpty()).optJSONArray(1)?:JSONArray()
            (0 until arr.length()).mapNotNull{arr.optString(it).takeIf{x->x.isNotBlank()}}
        }
    }



    suspend fun videoNextPage(videoId:String,continuation:String?=null):VideoNextPage=withContext(Dispatchers.IO){
        val body=JSONObject().put("context",webContext())
        if(continuation.isNullOrBlank())body.put("videoId",videoId) else body.put("continuation",continuation)
        val root=call("next",body)
        val related=parsePage(root).videos.filterNot{it.id==videoId}
        val comments=mutableListOf<VideoComment>()
        var nextToken:String?=null
        fun walk(value:Any?){
            when(value){
                is JSONObject->{
                    value.optJSONObject("commentRenderer")?.let{r->
                        val id=r.optString("commentId")
                        if(id.isNotBlank()){
                            comments+=VideoComment(id,text(r.optJSONObject("authorText")),text(r.optJSONObject("contentText")),
                                text(r.optJSONObject("voteCount")),text(r.optJSONObject("publishedTimeText")))
                        }
                    }
                    value.optJSONObject("continuationItemRenderer")?.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")?.optString("token")?.takeIf{it.isNotBlank()}?.let{nextToken=it}
                    val keys=value.keys()
                    while(keys.hasNext()){val k=keys.next();if(k!="commentRenderer")walk(value.opt(k))}
                }
                is JSONArray->for(i in 0 until value.length())walk(value.opt(i))
            }
        }
        walk(root)
        VideoNextPage(related,comments.distinctBy{it.id},nextToken)
    }

    suspend fun localSubscriptions(context:android.content.Context):List<Video> = coroutineScope {
        val channels=LocalLibrary.subscriptions(context)
        val jobs=channels.map{(id,_,_)->async(Dispatchers.IO){fetchChannelRss(id)}}
        jobs.flatMap{runCatching{it.await()}.getOrDefault(emptyList())}
            .distinctBy{it.id}.sortedByDescending{it.published}
    }

    private fun fetchChannelRss(channelId:String):List<Video>{
        val request=Request.Builder().url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            .header("User-Agent",browserUa()).build()
        return client.newCall(request).execute().use{response->
            if(!response.isSuccessful)error("RSS HTTP ${response.code}")
            val parser=org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(response.body?.charStream())
            val out=mutableListOf<Video>()
            var inEntry=false
            var title="";var id="";var author="";var published="";var thumbnail=""
            var event=parser.eventType
            while(event!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
                when(event){
                    org.xmlpull.v1.XmlPullParser.START_TAG->{
                        when(parser.name.substringAfter(':')){
                            "entry"->{inEntry=true;title="";id="";author="";published="";thumbnail=""}
                            "videoId"->if(inEntry)id=parser.nextText()
                            "title"->if(inEntry&&title.isBlank())title=parser.nextText()
                            "name"->if(inEntry&&author.isBlank())author=parser.nextText()
                            "published"->if(inEntry)published=parser.nextText()
                            "thumbnail"->if(inEntry)thumbnail=parser.getAttributeValue(null,"url").orEmpty()
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG->if(parser.name.substringAfter(':')=="entry"&&inEntry){
                        if(id.isNotBlank())out+=Video(id,title,author,thumbnail.ifBlank{"https://i.ytimg.com/vi/$id/hqdefault.jpg"},"",channelId,published=published)
                        inEntry=false
                    }
                }
                event=parser.next()
            }
            out
        }
    }

    suspend fun signedInHome(context:android.content.Context):SearchPage=withContext(Dispatchers.IO){
        parsePage(YouTubeAuth.tvCall(context,"browse",JSONObject().put("browseId","FEwhat_to_watch")))
    }

    suspend fun signedInSubscriptions(context:android.content.Context):SearchPage=withContext(Dispatchers.IO){
        parsePage(YouTubeAuth.tvCall(context,"browse",JSONObject().put("browseId","FEsubscriptions")))
    }

    suspend fun likeVideo(context:android.content.Context,videoId:String,kind:String)=withContext(Dispatchers.IO){
        YouTubeAuth.tvCall(context,"like/$kind",JSONObject().put("target",JSONObject().put("videoId",videoId)))
    }

    suspend fun subscribeChannel(context:android.content.Context,channelId:String,subscribe:Boolean)=withContext(Dispatchers.IO){
        val endpoint=if(subscribe)"subscription/subscribe" else "subscription/unsubscribe"
        YouTubeAuth.tvCall(context,endpoint,JSONObject().put("channelIds",JSONArray().put(channelId)))
    }

    private fun webContext()=JSONObject().put("client",JSONObject()
        .put("clientName","WEB").put("clientVersion",CLIENT_VERSION).put("hl","en").put("gl","US"))

    private fun call(endpoint:String,body:JSONObject):JSONObject{
        val request=Request.Builder().url("$BASE/$endpoint?prettyPrint=false")
            .header("User-Agent",browserUa()).header("Origin","https://www.youtube.com")
            .header("X-YouTube-Client-Name","1").header("X-YouTube-Client-Version",CLIENT_VERSION)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return client.newCall(request).execute().use{res->
            if(!res.isSuccessful)error("YouTube HTTP ${res.code}")
            JSONObject(res.body?.string()?:error("Empty YouTube response"))
        }
    }

    fun parsePage(root:JSONObject):SearchPage{
        val videos=mutableListOf<Video>()
        var continuation:String?=null
        fun walk(value:Any?){
            when(value){
                is JSONObject->{
                    value.optJSONObject("videoRenderer")?.let{parseVideo(it)?.let(videos::add)}
                    value.optJSONObject("compactVideoRenderer")?.let{parseVideo(it)?.let(videos::add)}
                    value.optJSONObject("gridVideoRenderer")?.let{parseVideo(it)?.let(videos::add)}
                    value.optJSONObject("reelItemRenderer")?.let{parseVideo(it)?.let(videos::add)}
                    value.optJSONObject("nextContinuationData")?.optString("continuation")?.takeIf{it.isNotBlank()}?.let{continuation=it}
                    value.optJSONObject("continuationItemRenderer")?.let{item->
                        item.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")
                            ?.optString("token")?.takeIf{it.isNotBlank()}?.let{continuation=it}
                    }
                    val keys=value.keys()
                    while(keys.hasNext()){val k=keys.next();if(k !in setOf("videoRenderer","compactVideoRenderer","gridVideoRenderer","reelItemRenderer"))walk(value.opt(k))}
                }
                is JSONArray->for(i in 0 until value.length())walk(value.opt(i))
            }
        }
        walk(root)
        return SearchPage(videos.distinctBy{it.id},continuation)
    }

    private fun parseVideo(r:JSONObject):Video?{
        val id=r.optString("videoId").takeIf{it.isNotBlank()}?:return null
        val title=text(r.optJSONObject("title")).ifBlank{"Untitled video"}
        val owner=r.optJSONObject("ownerText")?:r.optJSONObject("longBylineText")?:r.optJSONObject("shortBylineText")
        val channel=text(owner)
        val channelId=owner?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf{it.isNotBlank()}
        val thumbs=r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnail=thumbs?.optJSONObject((thumbs.length()-1).coerceAtLeast(0))?.optString("url")
            ?.takeIf{it.startsWith("http")}?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        val duration=text(r.optJSONObject("lengthText"))
        val badges=r.optJSONArray("badges")?.toString().orEmpty()
        val live=badges.contains("LIVE_NOW")||duration.equals("LIVE",true)
        val upcoming=r.has("upcomingEventData")
        val short=r.has("navigationEndpoint")&&r.optJSONObject("navigationEndpoint")?.has("reelWatchEndpoint")==true
        return Video(id,title,channel,thumbnail,duration,channelId,
            text(r.optJSONObject("shortViewCountText")?:r.optJSONObject("viewCountText")),
            text(r.optJSONObject("publishedTimeText")),
            text(r.optJSONObject("descriptionSnippet")),live,short,upcoming)
    }

    private fun text(obj:JSONObject?):String{
        if(obj==null)return ""
        obj.optString("simpleText").takeIf{it.isNotBlank()}?.let{return it}
        val runs=obj.optJSONArray("runs")?:return ""
        return buildString{for(i in 0 until runs.length())append(runs.optJSONObject(i)?.optString("text").orEmpty())}
    }

    private fun browserUa()="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
}
