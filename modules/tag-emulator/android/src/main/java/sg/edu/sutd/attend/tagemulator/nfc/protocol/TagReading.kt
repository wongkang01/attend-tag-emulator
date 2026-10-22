package sg.edu.sutd.attend.tagemulator.nfc.protocol

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * One signed tag reading, the data a reader gets from one tap.
 * Served as https://attend.example/t?uid=<UID>&ctr=<6 lowercase hex>&cmac=<16 uppercase hex>.
 */
data class TagReading(val uid: String, val counter: Int, val cmac: String) {
  val ctrHex: String get() = ctrHex(counter)
  val url: String get() = "https://$URL_REST_PREFIX$uid&ctr=$ctrHex&cmac=$cmac"

  /** An NDEF message with one short well-known URI record, using prefix code 0x04 ("https://"). */
  fun ndefMessage(): ByteArray {
    val rest = "$URL_REST_PREFIX$uid&ctr=$ctrHex&cmac=$cmac".toByteArray(Charsets.UTF_8)
    val payload = byteArrayOf(URI_PREFIX_HTTPS) + rest
    require(payload.size <= 0xFF) { "URI payload too long for a short record" }
    // MB | ME | SR | TNF well-known (0x01)
    val header = byteArrayOf(0xD1.toByte(), 0x01, payload.size.toByte(), 'U'.code.toByte())
    return header + payload
  }

  companion object {
    private const val URI_PREFIX_HTTPS: Byte = 0x04
    private const val URL_REST_PREFIX = "attend.example/t?uid="

    fun ctrHex(counter: Int): String = "%06x".format(counter)

    /** The signed message: "<UID>:<ctr as 6 lowercase hex digits>". */
    fun macMessage(uid: String, counter: Int): String = "$uid:${ctrHex(counter)}"

    /** First 8 bytes of HMAC-SHA256(key, UTF-8 of the MAC message), as uppercase hex. */
    fun sign(uid: String, counter: Int, keyHex: String): TagReading {
      require(counter in 0..DemoDoors.MAX_COUNTER) { "counter $counter does not fit in 6 hex digits" }
      val mac = Mac.getInstance("HmacSHA256")
      mac.init(SecretKeySpec(Hex.decode(keyHex), "HmacSHA256"))
      val full = mac.doFinal(macMessage(uid, counter).toByteArray(Charsets.UTF_8))
      return TagReading(uid, counter, Hex.encode(full.copyOf(8)))
    }
  }
}

object Hex {
  fun encode(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

  fun decode(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "odd-length hex" }
    return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
  }
}
