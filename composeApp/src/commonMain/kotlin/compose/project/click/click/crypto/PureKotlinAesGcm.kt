package compose.project.click.click.crypto

/** Small, self-contained AES-256-GCM implementation used where a native GCM API is unavailable. */
internal object PureKotlinAesGcm {
    private const val BLOCK_BYTES = 16
    private const val R = 0xe100000000000000uL

    fun encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32-byte key" }
        require(nonce.size == 12) { "GCM requires a 12-byte nonce" }
        val roundKeys = expandKey(key)
        val hashSubkey = encryptBlock(roundKeys, ByteArray(BLOCK_BYTES))
        val j0 = nonce.copyOf(16).also { it[15] = 1 }
        val ciphertext = cryptCtr(roundKeys, j0.copyOf(), plaintext)
        val tag = xor(encryptBlock(roundKeys, j0), ghash(hashSubkey, aad, ciphertext))
        return ciphertext + tag
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertextAndTag: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32-byte key" }
        require(nonce.size == 12) { "GCM requires a 12-byte nonce" }
        require(ciphertextAndTag.size >= 16) { "GCM ciphertext is missing its tag" }
        val ciphertext = ciphertextAndTag.copyOfRange(0, ciphertextAndTag.size - 16)
        val suppliedTag = ciphertextAndTag.copyOfRange(ciphertextAndTag.size - 16, ciphertextAndTag.size)
        val roundKeys = expandKey(key)
        val hashSubkey = encryptBlock(roundKeys, ByteArray(BLOCK_BYTES))
        val j0 = nonce.copyOf(16).also { it[15] = 1 }
        val expectedTag = xor(encryptBlock(roundKeys, j0), ghash(hashSubkey, aad, ciphertext))
        check(constantTimeEquals(suppliedTag, expectedTag)) { "AES-GCM authentication failed" }
        return cryptCtr(roundKeys, j0.copyOf(), ciphertext)
    }

    private fun cryptCtr(roundKeys: IntArray, counter: ByteArray, input: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        var offset = 0
        var blockCounter = 2
        while (offset < input.size) {
            counter[12] = (blockCounter ushr 24).toByte()
            counter[13] = (blockCounter ushr 16).toByte()
            counter[14] = (blockCounter ushr 8).toByte()
            counter[15] = blockCounter.toByte()
            val stream = encryptBlock(roundKeys, counter)
            val length = minOf(16, input.size - offset)
            repeat(length) { output[offset + it] = (input[offset + it].toInt() xor stream[it].toInt()).toByte() }
            offset += length
            blockCounter++
        }
        return output
    }

    private fun ghash(hashSubkey: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        var state = ByteArray(16)
        fun absorb(bytes: ByteArray) {
            var offset = 0
            while (offset < bytes.size) {
                val block = ByteArray(16)
                bytes.copyInto(block, 0, offset, minOf(offset + 16, bytes.size))
                state = multiplyGf128(xor(state, block), hashSubkey)
                offset += 16
            }
        }
        absorb(aad)
        absorb(ciphertext)
        val lengths = ByteArray(16)
        writeLongBits(lengths, 0, aad.size.toLong() * 8)
        writeLongBits(lengths, 8, ciphertext.size.toLong() * 8)
        return multiplyGf128(xor(state, lengths), hashSubkey)
    }

    private fun multiplyGf128(x: ByteArray, y: ByteArray): ByteArray {
        var z = 0uL to 0uL
        var v = readLongPair(y)
        for (bit in 0 until 128) {
            val selected = if (((x[bit / 8].toInt() ushr (7 - (bit % 8))) and 1) != 0) v else 0uL to 0uL
            z = (z.first xor selected.first) to (z.second xor selected.second)
            val lsb = (v.second and 1uL) != 0uL
            v = (v.first shr 1) to ((v.second shr 1) or ((v.first and 1uL) shl 63))
            if (lsb) v = (v.first xor R) to v.second
        }
        return writeLongPair(z)
    }

    private fun expandKey(key: ByteArray): IntArray {
        val words = IntArray(60)
        for (i in 0 until 8) words[i] = readInt(key, i * 4)
        var rcon = 1
        for (i in 8 until 60) {
            var temp = words[i - 1]
            if (i % 8 == 0) temp = subWord(rotWord(temp)) xor (rcon shl 24).also { rcon = gfMulByte(rcon, 2) }
            else if (i % 8 == 4) temp = subWord(temp)
            words[i] = words[i - 8] xor temp
        }
        return words
    }

    private fun encryptBlock(roundKeys: IntArray, input: ByteArray): ByteArray {
        val state = input.copyOf()
        addRoundKey(state, roundKeys, 0)
        for (round in 1 until 14) {
            subBytes(state)
            shiftRows(state)
            mixColumns(state)
            addRoundKey(state, roundKeys, round)
        }
        subBytes(state)
        shiftRows(state)
        addRoundKey(state, roundKeys, 14)
        return state
    }

    private fun addRoundKey(state: ByteArray, keys: IntArray, round: Int) {
        repeat(4) { word ->
            val value = keys[round * 4 + word]
            repeat(4) { byte -> state[word * 4 + byte] = (state[word * 4 + byte].toInt() xor (value ushr (24 - byte * 8))).toByte() }
        }
    }

    private fun subBytes(state: ByteArray) = repeat(16) { state[it] = sBox(state[it].toInt() and 0xff).toByte() }

    private fun shiftRows(state: ByteArray) {
        val copy = state.copyOf()
        for (row in 0 until 4) for (column in 0 until 4) state[column * 4 + row] = copy[((column + row) % 4) * 4 + row]
    }

    private fun mixColumns(state: ByteArray) {
        repeat(4) { column ->
            val i = column * 4
            val a0 = state[i].toInt() and 0xff
            val a1 = state[i + 1].toInt() and 0xff
            val a2 = state[i + 2].toInt() and 0xff
            val a3 = state[i + 3].toInt() and 0xff
            state[i] = (gfMulByte(a0, 2) xor gfMulByte(a1, 3) xor a2 xor a3).toByte()
            state[i + 1] = (a0 xor gfMulByte(a1, 2) xor gfMulByte(a2, 3) xor a3).toByte()
            state[i + 2] = (a0 xor a1 xor gfMulByte(a2, 2) xor gfMulByte(a3, 3)).toByte()
            state[i + 3] = (gfMulByte(a0, 3) xor a1 xor a2 xor gfMulByte(a3, 2)).toByte()
        }
    }

    private fun sBox(value: Int): Int {
        val inverse = if (value == 0) 0 else gfPow(value, 254)
        return inverse xor rotateLeftByte(inverse, 1) xor rotateLeftByte(inverse, 2) xor
            rotateLeftByte(inverse, 3) xor rotateLeftByte(inverse, 4) xor 0x63
    }

    private fun rotateLeftByte(value: Int, count: Int): Int =
        ((value shl count) or (value ushr (8 - count))) and 0xff

    private fun gfPow(value: Int, exponent: Int): Int {
        var result = 1
        var base = value
        var power = exponent
        while (power > 0) {
            if ((power and 1) != 0) result = gfMulByte(result, base)
            base = gfMulByte(base, base)
            power = power ushr 1
        }
        return result
    }

    private fun gfMulByte(left: Int, right: Int): Int {
        var a = left
        var b = right
        var result = 0
        repeat(8) {
            if ((b and 1) != 0) result = result xor a
            a = if ((a and 0x80) != 0) (a shl 1) xor 0x11b else a shl 1
            b = b ushr 1
        }
        return result and 0xff
    }

    private fun rotWord(value: Int): Int = (value shl 8) or (value ushr 24)

    private fun subWord(value: Int): Int {
        var result = 0
        repeat(4) { result = (result shl 8) or sBox(value ushr (24 - it * 8) and 0xff) }
        return result
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff shl 24) or
            (bytes[offset + 1].toInt() and 0xff shl 16) or
            (bytes[offset + 2].toInt() and 0xff shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun writeLongBits(target: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) target[offset + i] = (value ushr (56 - i * 8)).toByte()
    }

    private fun readLongPair(bytes: ByteArray): Pair<ULong, ULong> {
        fun read(offset: Int): ULong = (0 until 8).fold(0uL) { value, i -> (value shl 8) or (bytes[offset + i].toInt().toUByte().toULong()) }
        return read(0) to read(8)
    }

    private fun writeLongPair(value: Pair<ULong, ULong>): ByteArray {
        val output = ByteArray(16)
        fun write(offset: Int, number: ULong) { for (i in 0 until 8) output[offset + i] = (number shr (56 - i * 8)).toByte() }
        write(0, value.first)
        write(8, value.second)
        return output
    }

    private fun xor(left: ByteArray, right: ByteArray): ByteArray = ByteArray(left.size) { left[it] xor right[it] }

    private fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean {
        if (left.size != right.size) return false
        var difference = 0
        for (i in left.indices) difference = difference or (left[i].toInt() xor right[i].toInt())
        return difference == 0
    }
}

private infix fun Byte.xor(other: Byte): Byte = (toInt() xor other.toInt()).toByte()
