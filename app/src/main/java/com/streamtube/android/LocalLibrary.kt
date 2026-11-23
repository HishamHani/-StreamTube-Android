package com.streamtube.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class StreamTubeDatabase private constructor(context:Context):SQLiteOpenHelper(context,"streamtube.db",null,1){
 override fun onCreate(db:SQLiteDatabase){
  db.execSQL("CREATE TABLE history(video_id TEXT PRIMARY KEY,title TEXT,channel TEXT,thumbnail TEXT,duration TEXT,watched_at INTEGER)")
  db.execSQL("CREATE TABLE subscriptions(channel_id TEXT PRIMARY KEY,name TEXT,avatar TEXT)")
  db.execSQL("CREATE TABLE watch_later(video_id TEXT PRIMARY KEY,title TEXT,channel TEXT,thumbnail TEXT,duration TEXT,added_at INTEGER)")
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){}
 companion object{
  @Volatile private var instance:StreamTubeDatabase?=null
  fun get(context:Context)=instance?:synchronized(this){instance?:StreamTubeDatabase(context.applicationContext).also{instance=it}}
 }
}

object LocalLibrary{
 private fun db(c:Context)=StreamTubeDatabase.get(c).writableDatabase
 private fun videoValues(v:Video)=ContentValues().apply{
  put("video_id",v.id);put("title",v.title);put("channel",v.channel);put("thumbnail",v.thumbnail);put("duration",v.duration)
 }
 fun addHistory(c:Context,v:Video){
  val values=videoValues(v).apply{put("watched_at",System.currentTimeMillis())}
  db(c).insertWithOnConflict("history",null,values,SQLiteDatabase.CONFLICT_REPLACE)
 }
 fun history(c:Context,limit:Int=30):List<Video> = readVideos(c,"history","watched_at",limit)
 fun removeHistory(c:Context,id:String){db(c).delete("history","video_id=?",arrayOf(id))}
 fun clearHistory(c:Context){db(c).delete("history",null,null)}
 fun addWatchLater(c:Context,v:Video){
  val values=videoValues(v).apply{put("added_at",System.currentTimeMillis())}
  db(c).insertWithOnConflict("watch_later",null,values,SQLiteDatabase.CONFLICT_REPLACE)
 }
 fun removeWatchLater(c:Context,id:String){db(c).delete("watch_later","video_id=?",arrayOf(id))}
 fun isWatchLater(c:Context,id:String):Boolean=db(c).rawQuery("SELECT 1 FROM watch_later WHERE video_id=?",arrayOf(id)).use{it.moveToFirst()}
 fun watchLater(c:Context):List<Video> = readVideos(c,"watch_later","added_at",100)
 fun clearWatchLater(c:Context){db(c).delete("watch_later",null,null)}
 fun subscribe(c:Context,id:String,name:String,avatar:String?=null){
  val v=ContentValues().apply{put("channel_id",id);put("name",name);put("avatar",avatar)}
  db(c).insertWithOnConflict("subscriptions",null,v,SQLiteDatabase.CONFLICT_REPLACE)
 }
 fun unsubscribe(c:Context,id:String){db(c).delete("subscriptions","channel_id=?",arrayOf(id))}
 fun isSubscribed(c:Context,id:String):Boolean=db(c).rawQuery("SELECT 1 FROM subscriptions WHERE channel_id=?",arrayOf(id)).use{it.moveToFirst()}
 fun subscriptions(c:Context):List<Triple<String,String,String?>>{
  val out=mutableListOf<Triple<String,String,String?>>()
  db(c).rawQuery("SELECT channel_id,name,avatar FROM subscriptions ORDER BY name COLLATE NOCASE",null).use{cur->
   while(cur.moveToNext())out+=Triple(cur.getString(0),cur.getString(1),cur.getString(2))
  }
  return out
 }
 private fun readVideos(c:Context,table:String,order:String,limit:Int):List<Video>{
  val out=mutableListOf<Video>()
  db(c).rawQuery("SELECT video_id,title,channel,thumbnail,duration FROM $table ORDER BY $order DESC LIMIT ?",arrayOf(limit.toString())).use{cur->
   while(cur.moveToNext())out+=Video(cur.getString(0),cur.getString(1)?: "",cur.getString(2)?: "",cur.getString(3)?: "",cur.getString(4)?: "")
  }
  return out
 }
}
