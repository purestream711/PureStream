package com.purestream.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.purestream.data.model.DashboardCollection
import com.purestream.data.model.MediaItem
import com.purestream.data.model.GuidItem
import com.purestream.data.model.CollectionTag
import com.purestream.data.model.ProfanityLevel

class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromDashboardCollectionList(value: List<DashboardCollection>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toDashboardCollectionList(value: String): List<DashboardCollection> {
        val listType = object : TypeToken<List<DashboardCollection>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromMediaItemList(value: List<MediaItem>?): String {
        val gson = Gson()
        return gson.toJson(value ?: emptyList<MediaItem>())
    }

    @TypeConverter
    fun toMediaItemList(value: String?): List<MediaItem>? {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<MediaItem>>() {}.type
        val gson = Gson()
        return gson.fromJson<List<MediaItem>>(value, listType)
    }

    @TypeConverter
    fun fromGuidItemList(value: List<GuidItem>?): String {
        val gson = Gson()
        return gson.toJson(value ?: emptyList<GuidItem>())
    }

    @TypeConverter
    fun toGuidItemList(value: String?): List<GuidItem>? {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<GuidItem>>() {}.type
        val gson = Gson()
        return gson.fromJson<List<GuidItem>>(value, listType)
    }

    @TypeConverter
    fun fromCollectionTagList(value: List<CollectionTag>?): String {
        val gson = Gson()
        return gson.toJson(value ?: emptyList<CollectionTag>())
    }

    @TypeConverter
    fun toCollectionTagList(value: String?): List<CollectionTag>? {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<CollectionTag>>() {}.type
        val gson = Gson()
        return gson.fromJson<List<CollectionTag>>(value, listType)
    }

    @TypeConverter
    fun fromProfanityLevel(value: ProfanityLevel?): String {
        return value?.name ?: ProfanityLevel.UNKNOWN.name
    }

    @TypeConverter
    fun toProfanityLevel(value: String?): ProfanityLevel {
        return try {
            ProfanityLevel.valueOf(value ?: ProfanityLevel.UNKNOWN.name)
        } catch (e: Exception) {
            ProfanityLevel.UNKNOWN
        }
    }
}