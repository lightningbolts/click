package compose.project.click.click

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val url = "https://click.com/api/"
val client = HttpClient()

suspend fun poll(id: String): Connection?{
    val response = client.get(url + "poll/"){
        url.parameters.append(
            "id", id
        )
        headers.append("Authorization", "application/json")
    }
    if(response.status.value == 200){
        return Json.decodeFromString<Connection>(response.bodyAsText())
    }
    return null

}

suspend fun login(token: String, email: String): LoginObject? {
    val response = client.post(url + "google"){
        url.parameters.append(
            "token", token
        )
        url.parameters.append(
            "email", email
        )
    }
    if(response.status.value == 200){
        return Json.decodeFromString<LoginObject>(response.bodyAsText())
    }
    return null
}

suspend fun refresh(refresh: String): String? {
    val response = client.post(url + "refresh") {}
    if(response.status.value == 200){
        return response.bodyAsText()
    }
    return null
}

suspend fun register(token: String, email: String, name: String, imageSrc: String): LoginObject?{
    val response = client.post(url + "google"){
        url.parameters.append(
            "token", token
        )
        url.parameters.append(
            "email", email
        )
    }
    if(response.status.value == 205){
        val loginDetails =  Json.decodeFromString<LoginObject>(response.bodyAsText())
        val secondResponse = client.post(url + "create_account"){
            url.parameters.append("name", name)
            url.parameters.append("email", email)
            url.parameters.append("imageSrc", imageSrc)
            headers.append("Authorization", loginDetails.jwt)
        }
        if(secondResponse.status.value == 200) {
            return LoginObject(loginDetails.jwt, loginDetails.refresh, Json.decodeFromString<User>(secondResponse.bodyAsText()))
        }
    }
    return null
}

suspend fun user_by_name(name: String): User?{
    val response = client.get(url + "user/" + name) {
        headers.append("Authorization", "application/json")
    }
    if(response.status.value == 200){
        return Json.decodeFromString<User>(response.bodyAsText())
    }
    return null
}

suspend fun user_by_email(email: String): User?{
    val response = client.get(url + "user_with_email/" + email){
        headers.append("Authorization", "application/json")
    }
    if(response.status.value == 200){
        return Json.decodeFromString<User>(response.bodyAsText())
    }
    return null
}

suspend fun connections(ids: List<String>): List<Connection>?{
    val response = client.get(url + "connections/"){

        headers.append("Authorization", "application/json")
        setBody(
            Json.encodeToString(ids)
        )
    }
    if(response.status.value == 200) {
        return Json.decodeFromString<List<Connection>>(response.bodyAsText())
    }
    return null
}

suspend fun newConnection(id1: String, id2: String, location: GeoLocation): Connection?{
    val request = client.post("connections/new"){
        headers.append("Authorization", "application/json")
        url.parameters.append(
            "id1", id1,
        )
        url.parameters.append(
            "id2", id2,
        )
        url.parameters.append(
            "lat", location.lat.toString(),
        )
        url.parameters.append(
            "lon", location.lon.toString(),
        )
    }
    if (request.status.value == 200){
        return Json.decodeFromString<Connection>(request.bodyAsText())
    }
    return null
}