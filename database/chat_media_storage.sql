-- Optional: Supabase Storage for KMP chat images/audio (bucket name must match CHAT_MEDIA_BUCKET in ChatMediaConstants.kt).
-- 1. In Dashboard → Storage → New bucket → name: chat-media → Public bucket ON (simplest for publicUrl in the app).
-- 2. If the bucket is private, use signed URLs instead of publicUrl in SupabaseChatRepository.uploadChatMedia.
-- 3. Add policies so authenticated users can upload and everyone with the URL can read (tune for your security model).

-- Production RLS: apply migration `click-web/supabase/migrations/20260417120000_chat_media_storage_bucket_and_rls.sql`
-- (mirrored under `click/supabase/migrations/`). It enforces chat participation for native paths
-- `{uploader_uid}/{chat_uuid}/…` and `web/{uploader_uid}/…` for web uploads.

-- Profile avatars bucket `avatars` (see click-web/supabase/migrations/20260410200000_avatars_storage_bucket_and_rls.sql).
-- Without storage.objects RLS policies, uploads fail with "new row violates row-level security policy".
