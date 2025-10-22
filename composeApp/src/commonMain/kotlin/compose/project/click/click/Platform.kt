package compose.project.click.click

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform