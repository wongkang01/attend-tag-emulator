// Plays the APDUs a phone reader sends to the emulated door tag and checks every answer.
// Writes the full exchange to build/protocol-transcript.txt and exits 1 on any failed check.

import java.io.File
import kotlin.system.exitProcess
import sg.edu.sutd.attend.tagemulator.nfc.protocol.DemoDoors
import sg.edu.sutd.attend.tagemulator.nfc.protocol.Door
import sg.edu.sutd.attend.tagemulator.nfc.protocol.DoorPicker
import sg.edu.sutd.attend.tagemulator.nfc.protocol.DoorTag
import sg.edu.sutd.attend.tagemulator.nfc.protocol.SelectionStore
import sg.edu.sutd.attend.tagemulator.nfc.protocol.Hex
import sg.edu.sutd.attend.tagemulator.nfc.protocol.ServedReading
import sg.edu.sutd.attend.tagemulator.nfc.protocol.TagReading
import sg.edu.sutd.attend.tagemulator.nfc.protocol.TagState
import sg.edu.sutd.attend.tagemulator.nfc.protocol.TagStore
import sg.edu.sutd.attend.tagemulator.nfc.protocol.Type4Tag

private val transcript = StringBuilder()
private var failures = 0

private fun log(line: String) {
  println(line)
  transcript.appendLine(line)
}

private fun expect(name: String, actual: Any?, expected: Any?) {
  val ok = actual == expected
  if (!ok) failures++
  log("${if (ok) "PASS" else "FAIL"} $name${if (ok) "" else ": expected <$expected>, got <$actual>"}")
}

/** Stands in for SharedPreferences; a second DoorTag on the same store simulates an app restart. */
private class MemoryStore : TagStore {
  var saved = TagState()
  override fun load() = saved
  override fun save(state: TagState) { saved = state }
}

private class MemorySelection : SelectionStore {
  var saved: String? = null
  override fun load() = saved
  override fun save(doorId: String) { saved = doorId }
}

/** A reader in the field: sends APDUs and logs both directions. */
private class Reader(private val tag: Type4Tag) {
  fun send(hex: String): Pair<ByteArray, String> {
    val response = tag.process(Hex.decode(hex.replace(" ", "")))
    val sw = Hex.encode(response.copyOfRange(response.size - 2, response.size))
    val data = response.copyOf(response.size - 2)
    log("  >> $hex")
    log("  << ${if (data.isEmpty()) "" else Hex.encode(data) + " "}$sw")
    return data to sw
  }

  /** The sequence Android and iOS readers use, reading the message in chunks of [chunk] bytes. */
  fun readNdef(chunk: Int = 0xFF): String {
    expect("select NDEF application", send("00 A4 04 00 07 D2760000850101 00").second, "9000")
    expect("select CC file", send("00 A4 00 0C 02 E103").second, "9000")
    val (cc, ccSw) = send("00 B0 0000 0F")
    expect("read CC file", ccSw, "9000")
    expect("CC file bytes", Hex.encode(cc), "000F2000FF00FF0406E104020000FF")
    expect("select NDEF file", send("00 A4 00 0C 02 E104").second, "9000")
    val (nlenBytes, nlenSw) = send("00 B0 0000 02")
    expect("read NLEN", nlenSw, "9000")
    val nlen = ((nlenBytes[0].toInt() and 0xFF) shl 8) or (nlenBytes[1].toInt() and 0xFF)
    val message = ByteArray(0).toMutableList()
    var offset = 2
    while (offset < 2 + nlen) {
      val le = minOf(chunk, 2 + nlen - offset)
      val (part, sw) = send("00 B0 %04X %02X".format(offset, le))
      expect("read message at offset $offset", sw, "9000")
      message += part.toList()
      offset += part.size
    }
    return parseUriRecord(message.toByteArray())
  }
}

/** Independent NDEF parser: one short well-known "U" record with prefix 0x04. */
private fun parseUriRecord(msg: ByteArray): String {
  expect("record header MB|ME|SR|TNF=1", msg[0].toInt() and 0xFF, 0xD1)
  expect("type length", msg[1].toInt(), 1)
  val payloadLength = msg[2].toInt() and 0xFF
  expect("record type", msg[3].toInt().toChar(), 'U')
  expect("message length", msg.size, 4 + payloadLength)
  expect("URI prefix code", msg[4].toInt(), 0x04)
  return "https://" + String(msg, 5, payloadLength - 1, Charsets.UTF_8)
}

private fun expectedUrl(ctr: String, cmac: String, uid: String = DemoDoors.LT2_DOOR_A.uid) =
  "https://attend.example/t?uid=$uid&ctr=$ctr&cmac=$cmac"

/** What the Attend app does with the URL: parseInt(ctr, 16), uid and cmac compared case-insensitively. */
private fun readerCounter(url: String): Int =
  Integer.parseInt(url.substringAfter("ctr=").substringBefore("&"), 16)

fun main() {
  log("== MAC test vectors (the README table)")
  val vectors = listOf(
    Triple(DemoDoors.LT2_DOOR_A, 42, "806CDCC0C638BB5C"),
    Triple(DemoDoors.LT2_DOOR_A, 43, "CE8035914E933928"),
    Triple(DemoDoors.LT2_DOOR_B, 42, "51B205217C81A631"),
    Triple(DemoDoors.DOOR_2506, 42, "7E03165A333FF1D2"),
  )
  for ((d, counter, cmac) in vectors) {
    expect("${d.label} message for $counter", TagReading.macMessage(d.uid, counter), "${d.uid}:%06x".format(counter))
    expect("${d.label} MAC for $counter", TagReading.sign(d.uid, counter, d.keyHex).cmac, cmac)
  }
  expect("door ids are unique", DemoDoors.ALL.map(Door::id).toSet().size, DemoDoors.ALL.size)
  expect("door UIDs are unique", DemoDoors.ALL.map(Door::uid).toSet().size, DemoDoors.ALL.size)

  val store = MemoryStore()
  val events = mutableListOf<ServedReading>()
  var now = 1_700_000_000_000L
  var door = DoorTag(DemoDoors.LT2_DOOR_A, store, clock = { now })
  door.listener = { events += it }
  var tag = Type4Tag { door.serve().reading.ndefMessage() }
  val reader = Reader(tag)

  log("== Tap 1: fresh tag, whole message in one read")
  val first = reader.readNdef()
  expect("URL", first, expectedUrl("00002a", "806CDCC0C638BB5C"))
  expect("reader parses counter", readerCounter(first), 42)
  expect("one event", events.size, 1)
  expect("event counter", events.last().reading.counter, 42)
  expect("event time", events.last().atMillis, now)
  log("== Same tap: reader selects the NDEF file again")
  expect("re-read serves the same reading", reader.readNdef(), first)
  expect("no new event within one field session", events.size, 1)
  tag.deactivated()

  log("== Tap 2: message read in 16-byte chunks")
  now += 60_000
  val second = reader.readNdef(chunk = 16)
  expect("URL", second, expectedUrl("00002b", "CE8035914E933928"))
  expect("reader parses counter", readerCounter(second), 43)
  tag.deactivated()

  log("== Tap 3: replay mode serves the previous reading again")
  door.setReplay(true)
  now += 60_000
  expect("replayed URL", reader.readNdef(), second)
  expect("event marked replayed", events.last().replayed, true)
  expect("replay clears itself", door.state().replayArmed, false)
  tag.deactivated()

  log("== Tap 4: back to fresh readings")
  now += 60_000
  expect("URL", reader.readNdef().substringBefore("&cmac"), expectedUrl("00002c", "").substringBefore("&cmac"))
  expect("reads served", door.state().readsServed, 4)
  tag.deactivated()

  log("== App restart: counter persists")
  door = DoorTag(DemoDoors.LT2_DOOR_A, store, clock = { now })
  tag = Type4Tag { door.serve().reading.ndefMessage() }
  val afterRestart = Reader(tag).readNdef()
  expect("reader parses counter", readerCounter(afterRestart), 0x2d)
  expect(
    "MAC matches",
    afterRestart.substringAfter("cmac="),
    TagReading.sign(DemoDoors.LT2_DOOR_A.uid, 0x2d, DemoDoors.LT2_DOOR_A.keyHex).cmac,
  )
  tag.deactivated()

  log("== Reset counter")
  door.reset()
  expect("state after reset", door.state(), TagState())
  expect("URL", Reader(tag).readNdef(), expectedUrl("00002a", "806CDCC0C638BB5C"))
  tag.deactivated()

  log("== Bad commands")
  val bad = Reader(Type4Tag { door.serve().reading.ndefMessage() })
  val before = door.state().nextCounter
  expect("select file before the application", bad.send("00 A4 00 0C 02 E103").second, "6A82")
  expect("read with no file selected", bad.send("00 B0 0000 0F").second, "6986")
  expect("select unknown AID", bad.send("00 A4 04 00 07 D2760000850100 00").second, "6A82")
  expect("select NDEF application", bad.send("00 A4 04 00 07 D2760000850101 00").second, "9000")
  expect("select unknown file", bad.send("00 A4 00 0C 02 E105").second, "6A82")
  expect("unsupported instruction (UPDATE BINARY)", bad.send("00 D6 0000 01 00").second, "6D00")
  expect("unsupported instruction (GET DATA)", bad.send("00 CA 0000 00").second, "6D00")
  expect("unsupported class", bad.send("80 A4 04 00 07 D2760000850101 00").second, "6E00")
  expect("truncated command", bad.send("00 A4 04").second, "6700")
  expect("Lc longer than the data", bad.send("00 A4 04 00 07 D27600").second, "6700")
  expect("select CC file", bad.send("00 A4 00 0C 02 E103").second, "9000")
  expect("read past the CC file", bad.send("00 B0 000F 01").second, "6B00")
  expect("select NDEF file", bad.send("00 A4 00 0C 02 E104").second, "9000")
  expect("read past the NDEF file", bad.send("00 B0 0100 10").second, "6B00")
  expect("only the NDEF file selection served a reading", door.state().nextCounter, before + 1)

  checkDoorPicker()

  log(if (failures == 0) "ALL CHECKS PASSED" else "$failures CHECK(S) FAILED")
  File("protocol-transcript.txt").writeText(transcript.toString())
  if (failures != 0) exitProcess(1)
}

/** The door picker: per-door counters, replay and reset per door, and the selection surviving a restart. */
private fun checkDoorPicker() {
  val stores = DemoDoors.ALL.associate { it.id to MemoryStore() }
  val selection = MemorySelection()
  var now = 1_700_000_000_000L
  var picker = DoorPicker({ stores.getValue(it.id) }, selection, clock = { now })
  val events = mutableListOf<ServedReading>()
  picker.listener = { events += it }
  // The HCE service looks the door up on every read, as TagApduService does.
  var tag = Type4Tag { picker.selected().serve().reading.ndefMessage() }
  fun tap(): String = Reader(tag).readNdef().also { tag.deactivated(); now += 60_000 }

  log("== Door picker: default door")
  expect("no saved choice selects LT2 Door A", picker.selected().door, DemoDoors.LT2_DOOR_A)
  expect("Door A tap", tap(), expectedUrl("00002a", "806CDCC0C638BB5C"))
  expect("Door A tap", tap(), expectedUrl("00002b", "CE8035914E933928"))

  log("== Door picker: switch to LT2 Door B")
  picker.select(DemoDoors.LT2_DOOR_B.id)
  expect("choice saved", selection.saved, DemoDoors.LT2_DOOR_B.id)
  expect("Door B starts at 42", tap(), expectedUrl("00002a", "51B205217C81A631", DemoDoors.LT2_DOOR_B.uid))
  expect("event names the door", events.last().door, DemoDoors.LT2_DOOR_B)
  expect("Door A counter untouched", stores.getValue(DemoDoors.LT2_DOOR_A.id).saved.nextCounter, 44)

  log("== Door picker: replay and reset act on the selected door only")
  picker.selected().setReplay(true)
  expect("Door B replay", tap(), expectedUrl("00002a", "51B205217C81A631", DemoDoors.LT2_DOOR_B.uid))
  expect("event marked replayed", events.last().replayed, true)
  picker.select(DemoDoors.DOOR_2506.id)
  expect("2.506 Door starts at 42", tap(), expectedUrl("00002a", "7E03165A333FF1D2", DemoDoors.DOOR_2506.uid))
  picker.selected().reset()
  expect("2.506 Door reset", picker.selected().state(), TagState())
  expect("Door B kept its counter", stores.getValue(DemoDoors.LT2_DOOR_B.id).saved.nextCounter, 43)

  log("== Door picker: app restart keeps the choice and every counter")
  picker.select(DemoDoors.LT2_DOOR_B.id)
  picker = DoorPicker({ stores.getValue(it.id) }, selection, clock = { now })
  tag = Type4Tag { picker.selected().serve().reading.ndefMessage() }
  expect("LT2 Door B still selected", picker.selected().door, DemoDoors.LT2_DOOR_B)
  expect("Door B next counter", readerCounter(tap()), 43)
  picker.select(DemoDoors.LT2_DOOR_A.id)
  expect("Door A next counter", readerCounter(tap()), 44)

  log("== Door picker: unknown saved choice falls back to LT2 Door A")
  selection.saved = "gone"
  picker = DoorPicker({ stores.getValue(it.id) }, selection, clock = { now })
  expect("fallback door", picker.selected().door, DemoDoors.LT2_DOOR_A)
}
