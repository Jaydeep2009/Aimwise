package com.jaydeep.aimwise.data.remote

import com.jaydeep.aimwise.data.model.RoadmapResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class RoadmapRequest(
    val goal: String,
    val days: Int
)

data class AdjustRoadmapRequest(
    val remainingDays: List<DayPlanDto>,
    val incompleteTasks: List<String>,
    val totalRemainingDays: Int
)

data class DayPlanDto(
    val day: Int,
    val tasks: List<String>
)

data class AdjustRoadmapResponse(
    val days: List<DayPlanDto>
)

interface ApiService {

    @POST("generate-roadmap")
    suspend fun generateRoadmap(
        @Body request: RoadmapRequest
    ): Response<RoadmapResponse>

    @POST("adjust-roadmap")
    suspend fun adjustRoadmap(
        @Body request: AdjustRoadmapRequest
    ): Response<AdjustRoadmapResponse>

}
