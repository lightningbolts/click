package compose.project.click.click.data

/**
 * Supabase Storage bucket for chat images and audio. Create this bucket in the dashboard
 * (public read recommended for signed/public URLs) and add RLS policies for authenticated uploads.
 */
const val CHAT_MEDIA_BUCKET = "chat-media"

/** Supabase Storage bucket for user profile photos (`public.users.image`). */
const val AVATARS_BUCKET = "avatars"
