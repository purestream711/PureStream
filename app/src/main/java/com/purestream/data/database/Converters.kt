package com.purestream.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.purestream.data.model.DashboardCollection

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
}