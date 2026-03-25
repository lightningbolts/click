package compose.project.click.click.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.posix.size_tVar

@OptIn(ExperimentalForeignApi::class)
actual object PlatformCrypto {

    actual fun sha256(data: ByteArray): ByteArray {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        if (data.isEmpty()) {
            ByteArray(0).usePinned { emptyPinned ->
                digest.usePinned { digestPinned ->
                    CC_SHA256(
                        emptyPinned.addressOf(0),
                        0u,
                        digestPinned.addressOf(0).reinterpret()
                    )
                }
            }
        } else {
            data.usePinned { dataPinned ->
                digest.usePinned { digestPinned ->
                    CC_SHA256(
                        dataPinned.addressOf(0),
                        data.size.toUInt(),
                        digestPinned.addressOf(0).reinterpret()
                    )
                }
            }
        }
        return digest
    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = ByteArray(CC_SHA256_DIGEST_LENGTH)
        key.usePinned { keyPinned ->
            data.usePinned { dataPinned ->
                mac.usePinned { macPinned ->
                    CCHmac(
                        kCCHmacAlgSHA256,
                        keyPinned.addressOf(0),
                        key.size.convert(),
                        dataPinned.addressOf(0),
                        data.size.convert(),
                        macPinned.addressOf(0)
                    )
                }
            }
        }
        return mac
    }

    actual fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val bufferSize = plaintext.size + kCCBlockSizeAES128.toInt()
        val output = ByteArray(bufferSize)

        val written = memScoped {
            val numBytes = alloc<size_tVar>()
            key.usePinned { keyPinned ->
                iv.usePinned { ivPinned ->
                    plaintext.usePinned { inputPinned ->
                        output.usePinned { outputPinned ->
                            val status = CCCrypt(
                                kCCEncrypt.toUInt(),
                                kCCAlgorithmAES.toUInt(),
                                kCCOptionPKCS7Padding.toUInt(),
                                keyPinned.addressOf(0),
                                key.size.convert(),
                                ivPinned.addressOf(0),
                                inputPinned.addressOf(0),
                                plaintext.size.convert(),
                                outputPinned.addressOf(0),
                                bufferSize.convert(),
                                numBytes.ptr
                            )
                            check(status == kCCSuccess) { "AES-CBC encrypt failed with status $status" }
                        }
                    }
                }
            }
            numBytes.value.toInt()
        }

        return output.copyOfRange(0, written)
    }

    actual fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val bufferSize = ciphertext.size + kCCBlockSizeAES128.toInt()
        val output = ByteArray(bufferSize)

        val written = memScoped {
            val numBytes = alloc<size_tVar>()
            key.usePinned { keyPinned ->
                iv.usePinned { ivPinned ->
                    ciphertext.usePinned { inputPinned ->
                        output.usePinned { outputPinned ->
                            val status = CCCrypt(
                                kCCDecrypt.toUInt(),
                                kCCAlgorithmAES.toUInt(),
                                kCCOptionPKCS7Padding.toUInt(),
                                keyPinned.addressOf(0),
                                key.size.convert(),
                                ivPinned.addressOf(0),
                                inputPinned.addressOf(0),
                                ciphertext.size.convert(),
                                outputPinned.addressOf(0),
                                bufferSize.convert(),
                                numBytes.ptr
                            )
                            check(status == kCCSuccess) { "AES-CBC decrypt failed with status $status" }
                        }
                    }
                }
            }
            numBytes.value.toInt()
        }

        return output.copyOfRange(0, written)
    }

    actual fun secureRandomBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, count.convert(), pinned.addressOf(0))
        }
        return bytes
    }
}
