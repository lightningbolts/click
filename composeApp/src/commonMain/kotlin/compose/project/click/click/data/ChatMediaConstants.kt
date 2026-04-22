package compose.project.click.click.data

/**
 * Supabase Storage bucket for chat images and audio. Create this bucket in the dashboard
 * (public read recommended for signed/public URLs) and add RLS policies for authenticated uploads.
 */
const val CHAT_MEDIA_BUCKET = "chat-media"

/**
 * Supabase Storage bucket for arbitrary E2EE chat attachments (Phase 2 — B3). Bucket is PRIVATE,
 * with a 2 MiB `file_size_limit` and strict MIME allow-list enforced at the Storage layer. Writes
 * go through the Next.js gatekeeper (`/api/chat/attachments`); reads via signed URLs from
 * `/api/chat/attachments/sign`. Path layout: `{chat_uuid}/{uploader_uid}/{filename}`.
 */
const val CHAT_ATTACHMENTS_BUCKET = "chat-attachments"

/** Supabase Storage bucket for user profile photos (`public.users.image`). */
const val AVATARS_BUCKET = "avatars"
