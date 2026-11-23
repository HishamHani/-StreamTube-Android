package com.streamtube.android

import android.content.Context

data class StreamTubePreferences(
    val sponsorBlock:Boolean=true,
    val deArrowTitles:Boolean=false,
    val returnDislike:Boolean=true,
    val autoSkipCategories:Set<String> = setOf("sponsor","selfpromo","interaction"),
    val defaultQuality:Int=1080,
    val playbackSpeed:Float=1f,
    val autoplayNext:Boolean=true,
    val rememberHistory:Boolean=true,
    val region:String="US",
    val language:String="en"
)

object AppPreferences {
    private const val FILE="streamtube_preferences"
    fun load(context:Context):StreamTubePreferences {
        val p=context.getSharedPreferences(FILE,Context.MODE_PRIVATE)
        return StreamTubePreferences(
            sponsorBlock=p.getBoolean("sponsor_block",true),
            deArrowTitles=p.getBoolean("dearrow_titles",false),
            returnDislike=p.getBoolean("return_dislike",true),
            autoSkipCategories=p.getStringSet("skip_categories",setOf("sponsor","selfpromo","interaction"))?.toSet() ?: setOf("sponsor","selfpromo","interaction"),
            defaultQuality=p.getInt("quality",1080),
            playbackSpeed=p.getFloat("speed",1f),
            autoplayNext=p.getBoolean("autoplay_next",true),
            rememberHistory=p.getBoolean("history",true),
            region=p.getString("region","US") ?: "US",
            language=p.getString("language","en") ?: "en"
        )
    }
    fun setBoolean(context:Context,key:String,value:Boolean) {
        val name=when(key){"sponsorBlock"->"sponsor_block";"deArrowTitles"->"dearrow_titles";"returnDislike"->"return_dislike";"autoplayNext"->"autoplay_next";"rememberHistory"->"history";else->key}
        context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putBoolean(name,value).apply()
    }
}
