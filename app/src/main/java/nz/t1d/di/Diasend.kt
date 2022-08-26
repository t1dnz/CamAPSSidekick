package nz.t1d.diasend

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/*
mostly documented here https://github.com/PatrikTrestik/diasend-upload/blob/master/doc/diasend-api/DiasendAPI-1.0.0.yaml

curl -L -u 'a486o3nvdu88cg0sos4cw8cccc0o0cg.api.diasend.com:8imoieg4pyos04s44okoooowkogsco4' --request POST 'https://api.diasend.com/1/oauth2/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=password' \
--data-urlencode 'username=<username>' \
--data-urlencode 'password=<password>' \
--data-urlencode 'scope=PATIENT DIASEND_MOBILE_DEVICE_DATA_RW'

Returns

{"access_token":"<token>","expires_in":"86400","token_type":"Bearer"}

curl -vv -L --request GET 'https://api.diasend.com/1/patient/data?type=combined&date_from=<from>&date_to=<to>' --data-urlencode 'type=combined' -H "Authorization: Bearer <token>"

[
  {
    "type": "insulin_basal",
    "created_at": "2022-08-10T12:42:13",
    "value": 1,
    "unit": "U/h",
    "flags": []
  },
  {
    "type": "glucose",
    "created_at": "2022-08-10T12:42:17",
    "value": 7,
    "unit": "mmol/l",
    "flags": [
      {
        "flag": 123,
        "description": "Continous reading"
      }
    ]
  },
   {
    "type": "insulin_bolus",
    "created_at": "2022-07-30T13:20:00",
    "unit": "U",
    "total_value": 0.5,
    "spike_value": 0.5,
    "suggested": 0.5,
    "suggestion_overridden": "no",
    "suggestion_based_on_bg": "no",
    "suggestion_based_on_carb": "yes",
    "programmed_meal": 0.5,
    "flags": [
      {
        "flag": 1035,
        "description": "Bolus type ezcarb"
      }
    ]
  },
  {
    "type": "carb",
    "created_at": "2022-07-30T13:21:01",
    "value": "10",
    "unit": "g",
    "flags": []
  },
]
*/

@Serializable
data class AuthToken(
    @SerializedName("expires_in")
    val expiresIn: Int,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("access_token")
    val accessToken: String,
)

@Serializable
data class DiasendDatumFlag(
    @SerializedName("flag")
    val flag: Int,
    @SerializedName("description")
    val description: String,
)

@Serializable
data class DiasendDatum(
    @SerializedName("type")
    val type: String,
    @SerializedName("created_at")
    @Contextual
    val createdAt: Date,
    @SerializedName("value")
    val value: Float,
    @SerializedName("total_value")
    val totalValue: Float,
    @SerializedName("unit")
    val unit: String,
    @SerializedName("flags")
    val flags: List<DiasendDatumFlag>,
)

interface Diasend {
    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun getToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String,
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("scope") scope: String,
    ): Response<AuthToken>

    @GET("patient/data")
    suspend fun patientData(
        @Header("Authorization") authorization: String,
        @Query("type") type: String,
        @Query("date_from") date_from: String,
        @Query("date_to") date_to: String,
    ): Response<List<DiasendDatum>>
}

@Singleton
class DiasendClient @Inject constructor(@ApplicationContext context: Context) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val BASE_URL = "https://api.diasend.com/1/"
    private val TAG = "DiasendClient"
    val APP_USER_NAME_PASS =
        "a486o3nvdu88cg0sos4cw8cccc0o0cg.api.diasend.com:8imoieg4pyos04s44okoooowkogsco4"

    private var accessToken: String = ""
    private var accessTokenExpiry: LocalDateTime = LocalDateTime.now().minusDays(10) // force reauth at first

    // Client code
    private val retrofit: Retrofit by lazy {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val gson = GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .create()

        Retrofit.Builder()
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .baseUrl(BASE_URL)
            .build()
    }

    private val diasendClient: Diasend by lazy {
        retrofit.create(Diasend::class.java)

    }

    // API code
    private suspend fun getAccessToken(): String {
        if (accessTokenExpiry > LocalDateTime.now()) {
            // If the access token has expired or on first init
            Log.d(TAG,"Returning existing Auth token")
            return this.accessToken
        }
        Log.d(TAG, "Authenticating Diasend Client")

        val authPayload = APP_USER_NAME_PASS
        val data = authPayload.toByteArray()
        val base64 = Base64.getEncoder().encodeToString(data)

        val response = diasendClient.getToken(
            authorization = "Basic $base64".trim(),
            grantType = "password",
            username = prefs.getString("diasend_username", "")!!,
            password = prefs.getString("diasend_password", "")!!,
            scope = "PATIENT DIASEND_MOBILE_DEVICE_DATA_RW"
        )

        val body = response.body() ?: throw Exception("No Access Token")

        accessToken = body.accessToken
        accessTokenExpiry = LocalDateTime.now().plusSeconds(body.expiresIn.toLong()-300) // Fetch the auth token minus 5 mins for safety
        return accessToken
    }

    suspend fun getPatientData(date_from: LocalDateTime, date_to: LocalDateTime): List<DiasendDatum>? {
        val fmtr = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val date_from_str = date_from.format(fmtr)
        val date_to_str = date_to.format(fmtr)
        return diasendClient.patientData(
            "Bearer ${getAccessToken()}",
            "combined",
            date_from = date_from_str,
            date_to = date_to_str,
            ).body()
    }
}