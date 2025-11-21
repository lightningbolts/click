package compose.project.click.click.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime


object SupabaseConfig {
    private const val SUPABASE_URL = "https://lrgcwnmcscimkmslihxp.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxyZ2N3bm1jc2NpbWttc2xpaHhwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjA1MTgwNDksImV4cCI6MjA3NjA5NDA0OX0.-_LAhv-gUeCvViwTt8QZwM13U7jMIgTbiMZDkFf-oXk"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                scheme = "click"
                host = "login"
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}

