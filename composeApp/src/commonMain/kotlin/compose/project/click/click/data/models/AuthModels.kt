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
    val name: String
)

@Serializable
data class GoogleAuthRequest(
    val token: String
)

@Serializable
data class UserInfo(
    val email: String,
    val name: String? = null
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

