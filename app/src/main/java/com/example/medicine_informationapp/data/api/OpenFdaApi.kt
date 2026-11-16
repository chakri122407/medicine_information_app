package com.example.medicine_informationapp.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class OpenFdaResponse(
    @SerializedName("results") val results: List<DrugLabelResult>?
)

data class DrugLabelResult(
    @SerializedName("openfda") val openfda: OpenFdaData?,
    @SerializedName("purpose") val purpose: List<String>?,
    @SerializedName("indications_and_usage") val indicationsAndUsage: List<String>?,
    @SerializedName("dosage_and_administration") val dosageAndAdministration: List<String>?,
    @SerializedName("warnings") val warnings: List<String>?,
    @SerializedName("do_not_use") val doNotUse: List<String>?
)

data class OpenFdaData(
    @SerializedName("brand_name") val brandName: List<String>?,
    @SerializedName("generic_name") val genericName: List<String>?,
    @SerializedName("manufacturer_name") val manufacturerName: List<String>?
)

interface OpenFdaApiService {
    @GET("drug/label.json")
    suspend fun searchDrugLabel(
        @Query("search", encoded = true) searchQuery: String,
        @Query("limit") limit: Int = 1
    ): Response<OpenFdaResponse>

    companion object {
        private const val BASE_URL = "https://api.fda.gov/"

        fun create(): OpenFdaApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenFdaApiService::class.java)
        }
    }
}
