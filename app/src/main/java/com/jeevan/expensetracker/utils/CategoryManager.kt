package com.jeevan.expensetracker.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// A simple blueprint for a Category
data class CustomCategory(val name: String, val emoji: String)

object CategoryManager {
    private const val PREFS_NAME = "ExpenseCategoryPrefs"
    private const val KEY_CATEGORIES = "saved_custom_categories"

    // 1. Fetch the categories from the device's memory
    fun getCategories(context: Context): List<CustomCategory> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CATEGORIES, null)

        // If it's the user's first time opening the app, give them the default list!
        if (jsonString == null) {
            return getDefaultCategories()
        }

        val list = mutableListOf<CustomCategory>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(CustomCategory(obj.getString("name"), obj.getString("emoji")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return getDefaultCategories() // Fallback if data gets corrupted
        }
        return list
    }

    // 2. Save a new list of categories to the device's memory
    fun saveCategories(context: Context, categories: List<CustomCategory>) {
        val array = JSONArray()
        for (cat in categories) {
            val obj = JSONObject()
            obj.put("name", cat.name)
            obj.put("emoji", cat.emoji)
            array.put(obj)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CATEGORIES, array.toString())
            .apply()
    }

    // 3. The Starter Pack (Replaces your old hardcoded XML and when-statements)
    private fun getDefaultCategories(): List<CustomCategory> {
        return listOf(
            CustomCategory("Food", "🍔"),
            CustomCategory("Transport", "🚗"),
            CustomCategory("Shopping", "🛍️"),
            CustomCategory("Entertainment", "🎬"),
            CustomCategory("Bills", "💡"),
            CustomCategory("Healthcare", "🏥"),
            CustomCategory("Salary", "💵"),
            CustomCategory("Automated", "🤖"),
            CustomCategory("Other", "📌")
        )
    }
}