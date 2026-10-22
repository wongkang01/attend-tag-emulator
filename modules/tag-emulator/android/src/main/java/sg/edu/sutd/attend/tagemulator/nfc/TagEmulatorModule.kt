package sg.edu.sutd.attend.tagemulator.nfc

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import sg.edu.sutd.attend.tagemulator.nfc.protocol.Door
import sg.edu.sutd.attend.tagemulator.nfc.protocol.DoorPicker
import sg.edu.sutd.attend.tagemulator.nfc.protocol.TagState

/** JS bridge: door choice, tag status, replay mode, counter reset, and an onRead event per served reading. */
class TagEmulatorModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  private val picker: DoorPicker
    get() = AndroidDoorTag.get(context)

  override fun definition() = ModuleDefinition {
    Name("TagEmulator")

    Events("onRead")

    OnStartObserving("onRead") {
      picker.listener = { served ->
        sendEvent(
          "onRead",
          mapOf(
            "counter" to served.reading.counter,
            "url" to served.reading.url,
            "replayed" to served.replayed,
            "at" to served.atMillis.toDouble(),
            "status" to status(served.door, served.state),
          ),
        )
      }
    }

    OnStopObserving("onRead") {
      picker.listener = null
    }

    Function("getDoors") { picker.doors.map(::doorMap) }

    Function("getStatus") { selectedStatus() }

    Function("selectDoor") { doorId: String ->
      picker.select(doorId)
      selectedStatus()
    }

    Function("setReplayMode") { enabled: Boolean -> picker.selected().let { status(it.door, it.setReplay(enabled)) } }

    Function("resetCounter") { picker.selected().let { status(it.door, it.reset()) } }
  }

  private fun selectedStatus(): Map<String, Any?> = picker.selected().let { status(it.door, it.state()) }

  private fun doorMap(door: Door): Map<String, Any?> = mapOf(
    "id" to door.id,
    "label" to door.label,
    "uid" to door.uid,
    "key" to door.keyHex,
    "room" to door.room,
  )

  private fun status(door: Door, state: TagState): Map<String, Any?> = mapOf(
    "door" to doorMap(door),
    "nextCounter" to state.nextCounter,
    "lastServedCounter" to state.lastServedCounter,
    "readsServed" to state.readsServed,
    "lastReadAt" to state.lastReadAtMillis?.toDouble(),
    "replayArmed" to state.replayArmed,
    "nfc" to nfcStatus(),
  )

  private fun nfcStatus(): String {
    val pm = context.packageManager
    return when {
      !pm.hasSystemFeature(PackageManager.FEATURE_NFC) -> "no-nfc"
      !pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION) -> "no-hce"
      NfcAdapter.getDefaultAdapter(context)?.isEnabled != true -> "off"
      else -> "ready"
    }
  }
}
