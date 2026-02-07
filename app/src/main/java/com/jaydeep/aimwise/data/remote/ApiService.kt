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

interface ApiService {

    @POST("generate-roadmap")
    suspend fun generateRoadmap(
        @Body request: RoadmapRequest
    ): Response<RoadmapResponse>


}
