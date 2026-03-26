import CryptoKit
import Foundation

/// Swift port of `compose.project.click.click.crypto.MessageCrypto` for the notification service extension.
/// Keys are **derived** from connection id + participant user ids (same as Android `ClickFirebaseMessagingService`),
/// not from a Keychain-stored asymmetric private key.
enum E2EChatMessageCrypto {

    private static let e2eePrefix = "e2e:"
    private static let ivLength = 16
    private static let hmacLength = 32
    private static let salt = "click-platforms-e2ee-v1-2024"

    struct DerivedKeys {
        let encKey: Data
        let macKey: Data
    }

    static func deriveKeys(connectionId: String, userIds: [String]) -> DerivedKeys {
        let sorted = userIds.sorted()
        let input = "\(salt):\(sorted.joined(separator: ":")):\(connectionId)"
        let master = sha256(Data(input.utf8))
        let encKey = sha256(master + Data([0x01]))
        let macKey = sha256(master + Data([0x02]))
        return DerivedKeys(encKey: encKey, macKey: macKey)
    }

    /// Mirrors `MessageCrypto.decryptContent`.
    static func decryptContent(_ content: String, keys: DerivedKeys) -> String {
        guard content.hasPrefix(e2eePrefix) else { return content }

        guard let payload = Data(base64Encoded: String(content.dropFirst(e2eePrefix.count)), options: [.ignoreUnknownCharacters]) else {
            return content
        }
        guard payload.count >= ivLength + hmacLength + 1 else { return content }

        let iv = payload.subdata(in: 0..<ivLength)
        let storedHmac = payload.subdata(in: ivLength..<(ivLength + hmacLength))
        let ciphertext = payload.subdata(in: (ivLength + hmacLength)..<payload.count)

        let macKey = SymmetricKey(data: keys.macKey)
        let computed = HMAC<SHA256>.authenticationCode(for: iv + ciphertext, using: macKey)
        guard Data(computed) == storedHmac else {
            return content
        }

        let bufferSize = ciphertext.count + 16
        var decrypted = Data(count: bufferSize)
        var numBytes: size_t = 0

        let status: Int32 = decrypted.withUnsafeMutableBytes { outPtr in
            ciphertext.withUnsafeBytes { ctPtr in
                keys.encKey.withUnsafeBytes { keyPtr in
                    iv.withUnsafeBytes { ivPtr in
                        click_aes256_cbc_pkcs7_decrypt(
                            keyPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                            keys.encKey.count,
                            ivPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                            ctPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                            ciphertext.count,
                            outPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                            bufferSize,
                            &numBytes
                        )
                    }
                }
            }
        }

        guard status == 0, let text = String(data: decrypted.prefix(Int(numBytes)), encoding: .utf8) else {
            return content
        }
        return text
    }

    static func isEncrypted(_ content: String) -> Bool {
        content.hasPrefix(e2eePrefix)
    }

    private static func sha256(_ data: Data) -> Data {
        Data(SHA256.hash(data: data))
    }
}
