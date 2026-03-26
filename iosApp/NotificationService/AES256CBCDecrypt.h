#ifndef AES256_CBC_DECRYPT_H
#define AES256_CBC_DECRYPT_H

#include <stddef.h>
#include <stdint.h>

/// AES-256-CBC decrypt with PKCS7 padding (CommonCrypto). Matches Kotlin `PlatformCrypto.aesCbcDecrypt`.
/// Returns 0 on success; non-zero CCCryptorStatus on failure.
int click_aes256_cbc_pkcs7_decrypt(
    const uint8_t *key,
    size_t key_len,
    const uint8_t *iv,
    const uint8_t *ciphertext,
    size_t ciphertext_len,
    uint8_t *out,
    size_t out_capacity,
    size_t *out_length
);

#endif
