package compose.project.click.click

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LoginObject(val jwt: String, val refresh: String, val user: User)

class Api(initialToken: String, initialRefresh: String) {
    var token: String = initialToken
    var refresh: String = initialRefresh

    private val baseUrl = "https://click.com/api/"
    private val client = HttpClient()

    suspend fun login(googleToken: String, email: String): User? {
        val response = client.post(baseUrl + "google") {
            parameter("token", googleToken)
            parameter("email", email)
        }
        if (response.status.value == 200) {
            val logindetails = Json.decodeFromString<LoginObject>(response.bodyAsText())
            token = logindetails.jwt
            refresh = logindetails.refresh
            return logindetails.user
        }
        return null
    }

    suspend fun register(googleToken: String, email: String, name: String, imageSrc: String): User? {
        val response = client.post(baseUrl + "google") {
            parameter("token", googleToken)
            parameter("email", email)
        }

        // Assuming 205 means "Account not found, please create one"
        if (response.status.value == 205) {
            val loginDetails = Json.decodeFromString<LoginObject>(response.bodyAsText())
            token = loginDetails.jwt
            refresh = loginDetails.refresh
            val secondResponse = client.post(baseUrl + "create_account") {
                headers.append("Authorization", "Bearer ${loginDetails.jwt}")
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(mapOf("name" to name, "email" to email, "imageSrc" to imageSrc)))
            }
            if (secondResponse.status.value == 200) {
                return Json.decodeFromString<User>(secondResponse.bodyAsText())
            }
        }
        return null
    }

    private suspend fun refreshToken(): Boolean {
        val response = client.post(baseUrl + "refresh") {
            // Assuming the refresh token is sent in the Authorization header
            headers.append("Authorization", "Bearer $refresh")
        }
        if (response.status.value == 200) {
            // Assuming the response body is the new JWT token
            token = response.bodyAsText()
            return true
        }
        return false
    }

    suspend fun poll(id: String): Connection? {
        var response = client.get(baseUrl + "poll/") {
            parameter("id", id)
            headers.append("Authorization", "Bearer $token")
        }
        while (response.status.value == 401) {
            if (!refreshToken()) return null
            response = client.get(baseUrl + "poll/") {
                parameter("id", id)
                headers.append("Authorization", "Bearer $token")
            }
        }
        if (response.status.value == 200) {
            return Json.decodeFromString<Connection>(response.bodyAsText())
        }
        return null
    }

    suspend fun userByName(name: String): User? {
        var response = client.get(baseUrl + "user/$name") {
            headers.append("Authorization", "Bearer $token")
        }
        while (response.status.value == 401) {
            if (!refreshToken()) return null
            response = client.get(baseUrl + "user/$name") {
                headers.append("Authorization", "Bearer $token")
            }
        }
        if (response.status.value == 200) {
            return Json.decodeFromString<User>(response.bodyAsText())
        }
        return null
    }

    suspend fun userByEmail(email: String): User? {
        var response = client.get(baseUrl + "user_with_email/$email") {
            headers.append("Authorization", "Bearer $token")
        }
        while (response.status.value == 401) {
            if (!refreshToken()) return null
            response = client.get(baseUrl + "user_with_email/$email") {
                headers.append("Authorization", "Bearer $token")
            }
        }

        if (response.status.value == 200) {
            return Json.decodeFromString<User>(response.bodyAsText())
        }
        return null
    }

    suspend fun getConnections(ids: List<String>): List<Connection>? {
        var response = client.post(baseUrl + "connections/") {
            headers.append("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ids))
        }
        while (response.status.value == 401) {
            if (!refreshToken()) return null
            response = client.post(baseUrl + "connections/") {
                headers.append("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(ids))
            }
        }
        if (response.status.value == 200) {
            return Json.decodeFromString<List<Connection>>(response.bodyAsText())
        }
        return null
    }

    suspend fun newConnection(id1: String, id2: String, location: GeoLocation): Connection? {
        var response = client.post(baseUrl + "connections/new") {
            headers.append("Authorization", "Bearer $token")
            parameter("id1", id1)
            parameter("id2", id2)
            parameter("lat", location.lat.toString())
            parameter("lon", location.lon.toString())
        }
        while (response.status.value == 401) {
            if (!refreshToken()) return null
            response = client.post(baseUrl + "connections/new") {
                headers.append("Authorization", "Bearer $token")
                parameter("id1", id1)
                parameter("id2", id2)
                parameter("lat", location.lat.toString())
                parameter("lon", location.lon.toString())
            }
        }
        if (response.status.value == 200) {
            return Json.decodeFromString<Connection>(response.bodyAsText())
        }
        return null
    }
}