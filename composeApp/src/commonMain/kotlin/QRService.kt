package compose.project.click.click.qr

import compose.project.click.click.data.models.User

interface QrService {
    /**
     * ユーザー情報から QR に埋める文字列 (ペイロード) を作成する。
     * 共通ロジックのみここで扱い、Bitmap 生成などはプラットフォーム実装に委ねる。
     */
    fun createPayloadForUser(user: User): String

    /**
     * スキャンした QR ペイロード文字列を解析して QrPayload を返す (失敗時 null)。
     */
    fun parsePayload(payload: String): QrPayload?
    fun MultiFormatWriter()
}
