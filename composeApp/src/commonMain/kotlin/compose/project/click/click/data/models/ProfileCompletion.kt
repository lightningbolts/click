package compose.project.click.click.data.models

/**
 * True when the signed-in row in [public.users] still needs manual / OAuth follow-up data
 * before we allow primary navigation (map, connections, …).
 */
fun isPublicUserProfileIncomplete(user: User): Boolean {
    val birthdayMissing = user.birthday.isNullOrBlank()
    val firstNameMissing = user.firstName.isNullOrBlank()
    return birthdayMissing || firstNameMissing
}
