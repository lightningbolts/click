-- Optional: Supabase Storage for KMP chat images/audio (bucket name must match CHAT_MEDIA_BUCKET in ChatMediaConstants.kt).
-- 1. In Dashboard → Storage → New bucket → name: chat-media → Public bucket ON (simplest for publicUrl in the app).
-- 2. If the bucket is private, use signed URLs instead of publicUrl in SupabaseChatRepository.uploadChatMedia.
-- 3. Add policies so authenticated users can upload and everyone with the URL can read (tune for your security model).

-- Example policies (run after bucket exists; syntax may vary by Supabase version):
-- CREATE POLICY "chat_media_insert_authenticated" ON storage.objects FOR INSERT TO authenticated
--   WITH CHECK (bucket_id = 'chat-media');
-- CREATE POLICY "chat_media_select_public" ON storage.objects FOR SELECT TO public
--   USING (bucket_id = 'chat-media');
