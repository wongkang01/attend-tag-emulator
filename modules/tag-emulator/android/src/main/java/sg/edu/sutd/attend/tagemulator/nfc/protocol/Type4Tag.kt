package sg.edu.sutd.attend.tagemulator.nfc.protocol

/**
 * An NFC Forum Type 4 Tag (mapping version 2.0) that serves one read-only NDEF file.
 * Feed it each command APDU with [process] and call [deactivated] when the reader leaves the field.
 *
 * The NDEF file is built by [readNdef] when the reader first selects it in a field session,
 * so each tap gets one fresh reading; selecting it again in the same session serves the same bytes.
 */
class Type4Tag(private val readNdef: () -> ByteArray) {
  private enum class File { CC, NDEF }

  private var appSelected = false
  private var selected: File? = null
  private var ndefFile: ByteArray? = null

  fun deactivated() {
    appSelected = false
    selected = null
    ndefFile = null
  }

  fun process(apdu: ByteArray): ByteArray {
    if (apdu.size < 4) return SW_WRONG_LENGTH
    if (apdu[0] != 0x00.toByte()) return SW_CLA_NOT_SUPPORTED
    return when (apdu[1]) {
      INS_SELECT -> select(apdu)
      INS_READ_BINARY -> readBinary(apdu)
      else -> SW_INS_NOT_SUPPORTED
    }
  }

  private fun select(apdu: ByteArray): ByteArray {
    if (apdu.size < 5) return SW_WRONG_LENGTH
    val lc = apdu.u8(4)
    if (apdu.size < 5 + lc) return SW_WRONG_LENGTH
    val data = apdu.copyOfRange(5, 5 + lc)
    val p1 = apdu.u8(2)
    val p2 = apdu.u8(3)
    return when {
      p1 == 0x04 && p2 == 0x00 -> {
        if (!data.contentEquals(NDEF_APP_AID)) return SW_NOT_FOUND
        appSelected = true
        selected = null
        SW_OK
      }
      p1 == 0x00 && (p2 == 0x0C || p2 == 0x00) -> {
        if (!appSelected || lc != 2) return SW_NOT_FOUND
        when (data.u16(0)) {
          CC_FILE_ID -> selected = File.CC
          NDEF_FILE_ID -> {
            selected = File.NDEF
            if (ndefFile == null) ndefFile = nlenPrefixed(readNdef())
          }
          else -> return SW_NOT_FOUND
        }
        SW_OK
      }
      else -> SW_WRONG_P1P2
    }
  }

  private fun readBinary(apdu: ByteArray): ByteArray {
    val file = when (selected) {
      File.CC -> CC_FILE
      File.NDEF -> ndefFile ?: return SW_NO_CURRENT_EF
      null -> return SW_NO_CURRENT_EF
    }
    if (apdu.size > 5) return SW_WRONG_LENGTH
    if (apdu.u8(2) and 0x80 != 0) return SW_WRONG_P1P2
    val offset = apdu.u16(2)
    // Le of 0x00, or no Le at all, asks for up to 256 bytes.
    val le = if (apdu.size == 5 && apdu.u8(4) != 0) apdu.u8(4) else 256
    if (offset >= file.size) return SW_WRONG_OFFSET
    return file.copyOfRange(offset, minOf(file.size, offset + le)) + SW_OK
  }

  companion object {
    private const val INS_SELECT: Byte = 0xA4.toByte()
    private const val INS_READ_BINARY: Byte = 0xB0.toByte()
    private const val CC_FILE_ID = 0xE103
    private const val NDEF_FILE_ID = 0xE104

    /** Largest NDEF file the CC file advertises; the served file is always smaller. */
    const val MAX_NDEF_FILE_SIZE = 0x0200

    val NDEF_APP_AID = Hex.decode("D2760000850101")

    val CC_FILE = Hex.decode(
      "000F" + // CCLEN: 15 bytes
        "20" + // mapping version 2.0
        "00FF" + // MLe: max bytes per READ BINARY
        "00FF" + // MLc: max bytes per UPDATE BINARY
        "0406" + // NDEF File Control TLV, 6 bytes
        "E104" + // NDEF file id
        "%04X".format(MAX_NDEF_FILE_SIZE) +
        "00" + // read access: granted
        "FF", // write access: none
    )

    val SW_OK = Hex.decode("9000")
    val SW_WRONG_LENGTH = Hex.decode("6700")
    val SW_NO_CURRENT_EF = Hex.decode("6986")
    val SW_NOT_FOUND = Hex.decode("6A82")
    val SW_WRONG_P1P2 = Hex.decode("6A86")
    val SW_WRONG_OFFSET = Hex.decode("6B00")
    val SW_INS_NOT_SUPPORTED = Hex.decode("6D00")
    val SW_CLA_NOT_SUPPORTED = Hex.decode("6E00")

    /** The NDEF file: a 2-byte big-endian length (NLEN) followed by the message. */
    fun nlenPrefixed(message: ByteArray): ByteArray {
      require(message.size + 2 <= MAX_NDEF_FILE_SIZE) { "NDEF message too large" }
      return byteArrayOf((message.size shr 8).toByte(), message.size.toByte()) + message
    }

    private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF
    private fun ByteArray.u16(i: Int): Int = (u8(i) shl 8) or u8(i + 1)
  }
}
