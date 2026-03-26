#include "AES256CBCDecrypt.h"
#include <CommonCrypto/CommonCryptor.h>

int click_aes256_cbc_pkcs7_decrypt(
    const uint8_t *key,
    size_t key_len,
    const uint8_t *iv,
    const uint8_t *ciphertext,
    size_t ciphertext_len,
    uint8_t *out,
    size_t out_capacity,
    size_t *out_length
) {
    if (out_length == NULL) {
        return -1;
    }
    size_t numBytes = 0;
    CCCryptorStatus status = CCCrypt(
        kCCDecrypt,
        kCCAlgorithmAES,
        kCCOptionPKCS7Padding,
        key,
        key_len,
        iv,
        ciphertext,
        ciphertext_len,
        out,
        out_capacity,
        &numBytes
    );
    if (status != kCCSuccess) {
        return (int)status;
    }
    *out_length = numBytes;
    return 0;
}
