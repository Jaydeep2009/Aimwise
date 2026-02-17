package com.jaydeep.aimwise.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * Represents a single task within a day's plan.
 */
data class Task(
    val description: String,
    val isCompleted: Boolean = false
)

/**
 * Represents a daily plan within a goal's roadmap.
 * 
 * @property day Day number (1-indexed)
 * @property tasks List of tasks for this day
 * @property status Status: "pending", "in_progress", "completed", or "skipped"
 */
data class DayPlan(
    val day: Int = 0,
    val tasks: List<Task> = emptyList(),
    val status: String = "pending"
)

class DayPlanDeserializer : JsonDeserializer<DayPlan> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): DayPlan {
        val jsonObject = json.asJsonObject
        val day = jsonObject.get("day")?.asInt ?: 0
        val status = jsonObject.get("status")?.asString ?: "pending"
        
        // Handle tasks as either strings or Task objects
        val tasksArray = jsonObject.getAsJsonArray("tasks")
        val tasks = tasksArray.map { element ->
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                // If it's a string, create a Task object
                Task(description = element.asString, isCompleted = false)
            } else {
                // If it's already a Task object, deserialize it
                context.deserialize<Task>(element, Task::class.java)
            }
        }
        
        return DayPlan(day = day, tasks = tasks, status = status)
    }
}