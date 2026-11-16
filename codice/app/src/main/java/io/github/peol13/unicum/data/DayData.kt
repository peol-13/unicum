package io.github.peol13.unicum.data

data class DayData(
    val meals: MutableList<Meal> = mutableListOf(),
    val activities: MutableList<Activity> = mutableListOf()
) {

    // ==============================
    // NUTRIZIONE
    // ==============================

    val caloriesConsumed: Int
        get() = meals.sumOf { it.calories }

    val mealCount: Int
        get() = meals.size


    // ==============================
    // ATTIVITÀ
    // ==============================

    val activityMinutes: Int
        get() = activities.sumOf { it.durationMinutes }

    val activityCalories: Int
        get() = activities.sumOf { it.calories }

    val activityCount: Int
        get() = activities.size
}