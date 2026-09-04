package compose.project.click.click.crypto

/** RFC 7748 X25519 over Curve25519, implemented with 16-bit limbs for KMP portability. */
internal object X25519 {
    private const val LIMBS = 16
    private const val LIMB_MASK = 0xffffL
    private val modulus = LongArray(LIMBS).apply {
        this[0] = 65517
        for (i in 1 until 15) this[i] = 65535
        this[15] = 32767
    }

    fun generatePrivateKey(): ByteArray = PlatformCrypto.secureRandomBytes(32).also { clamp(it) }

    fun publicKey(privateKey: ByteArray): ByteArray = scalarMult(privateKey, ByteArray(32).also { it[0] = 9 })

    fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(privateKey.size == 32 && peerPublicKey.size == 32)
        return scalarMult(privateKey, peerPublicKey)
    }

    private fun scalarMult(privateKey: ByteArray, uCoordinate: ByteArray): ByteArray {
        val scalar = privateKey.copyOf().also { clamp(it) }
        val x1 = Field.fromBytes(uCoordinate)
        var x2 = Field.one()
        var z2 = Field.zero()
        var x3 = x1
        var z3 = Field.one()
        var swap = 0
        for (t in 254 downTo 0) {
            val bit = (scalar[t / 8].toInt() ushr (t and 7)) and 1
            swap = swap xor bit
            if (swap != 0) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = bit
            val a = x2 + z2
            val aa = a.square()
            val b = x2 - z2
            val bb = b.square()
            val e = aa - bb
            val c = x3 + z3
            val d = x3 - z3
            val da = d * a
            val cb = c * b
            x3 = (da + cb).square()
            z3 = x1 * (da - cb).square()
            x2 = aa * bb
            z2 = e * (aa + Field.constant(121665) * e)
        }
        if (swap != 0) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }
        return (x2 * z2.powPMinus2()).toBytes()
    }

    private fun clamp(scalar: ByteArray) {
        scalar[0] = (scalar[0].toInt() and 248).toByte()
        scalar[31] = (scalar[31].toInt() and 127 or 64).toByte()
    }

    private class Field private constructor(private val limbs: LongArray) {
        operator fun plus(other: Field): Field = Field(reduce(LongArray(LIMBS) { limbs[it] + other.limbs[it] }))
        operator fun minus(other: Field): Field = Field(reduce(LongArray(LIMBS) { limbs[it] - other.limbs[it] + modulus[it] }))
        operator fun times(other: Field): Field {
            val product = LongArray(31)
            for (i in 0 until LIMBS) for (j in 0 until LIMBS) product[i + j] += limbs[i] * other.limbs[j]
            for (i in 30 downTo LIMBS) product[i - LIMBS] += product[i] * 38
            return Field(reduce(product.copyOfRange(0, LIMBS)))
        }
        fun square(): Field = this * this

        fun powPMinus2(): Field {
            var result = one()
            var base = this
            var exponent = ByteArray(32).also {
                it.fill(0xff.toByte())
                it[0] = 0xeb.toByte()
                it[31] = 0x7f
            }
            for (bit in 254 downTo 0) {
                result = result.square()
                if (((exponent[bit / 8].toInt() ushr (bit and 7)) and 1) != 0) result = result * base
            }
            return result
        }

        fun toBytes(): ByteArray {
            val normalized = reduce(limbs.copyOf())
            val bytes = ByteArray(32)
            for (i in 0 until LIMBS) {
                bytes[i * 2] = normalized[i].toByte()
                bytes[i * 2 + 1] = (normalized[i] ushr 8).toByte()
            }
            bytes[31] = (bytes[31].toInt() and 0x7f).toByte()
            return bytes
        }

        companion object {
            fun zero() = Field(LongArray(LIMBS))
            fun one() = constant(1)
            fun constant(value: Long) = Field(LongArray(LIMBS).also { it[0] = value })
            fun fromBytes(bytes: ByteArray): Field {
                require(bytes.size == 32)
                val limbs = LongArray(LIMBS)
                for (i in 0 until LIMBS) limbs[i] = ((bytes[i * 2].toInt() and 0xff) or ((bytes[i * 2 + 1].toInt() and 0xff) shl 8)).toLong()
                limbs[15] = limbs[15] and 0x7fff
                return Field(reduce(limbs))
            }

            private fun reduce(input: LongArray): LongArray {
                val result = input.copyOf()
                repeat(4) {
                    for (i in 0 until 15) {
                        val carry = result[i] shr 16
                        result[i] = result[i] and LIMB_MASK
                        result[i + 1] += carry
                    }
                    val topCarry = result[15] shr 15
                    result[15] = result[15] and 32767
                    result[0] += topCarry * 19
                }
                for (i in 0 until LIMBS) result[i] = result[i] and LIMB_MASK
                while (geq(result, modulus)) subtract(result, modulus)
                return result
            }

            private fun geq(left: LongArray, right: LongArray): Boolean {
                for (i in 15 downTo 0) if (left[i] != right[i]) return left[i] > right[i]
                return true
            }

            private fun subtract(left: LongArray, right: LongArray) {
                var borrow = 0L
                for (i in 0 until LIMBS) {
                    var value = left[i] - right[i] - borrow
                    if (value < 0) { value += 65536; borrow = 1 } else borrow = 0
                    left[i] = value
                }
            }
        }
    }
}
