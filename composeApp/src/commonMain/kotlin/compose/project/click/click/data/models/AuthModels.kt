package compose.project.click.click.data.models

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val first_name: String,
    val last_name: String,
    /** ISO-8601 calendar date (yyyy-MM-dd). */
    val birthday: String
)

@Serializable
data class GoogleAuthRequest(
    val token: String
)

@Serializable
data class UserInfo(
    val email: String,
    val name: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val birthday: String? = null
)

@Serializable
data class AuthResponse(
    val jwt: String,
    val refresh: String,
    val user: UserInfo? = null
)

@Serializable
data class RefreshResponse(
    val jwt: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

