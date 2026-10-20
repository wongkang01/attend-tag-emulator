package sg.edu.sutd.attend.tagemulator.nfc

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import sg.edu.sutd.attend.tagemulator.nfc.protocol.DoorTag
import sg.edu.sutd.attend.tagemulator.nfc.protocol.DemoTag
import sg.edu.sutd.attend.tagemulator.nfc.protocol.TagState

/** JS bridge: tag status, replay mode, counter reset, and an onRead event per served reading. */
class TagEmulatorModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  private val door: DoorTag
    get() = AndroidDoorTag.get(context)

  override fun definition() = ModuleDefinition {
    Name("TagEmulator")

    Events("onRead")

    OnStartObserving("onRead") {
      door.listener = { served ->
        sendEvent(
          "onRead",
          mapOf(
            "counter" to served.reading.counter,
            "url" to served.reading.url,
            "replayed" to served.replayed,
            "at" to served.atMillis.toDouble(),
            "status" to status(served.state),
          ),
        )
      }
    }

    OnStopObserving("onRead") {
      door.listener = null
    }

    Function("getStatus") { status(door.state()) }

    Function("setReplayMode") { enabled: Boolean -> status(door.setReplay(enabled)) }

    Function("resetCounter") { status(door.reset()) }
  }

  private fun status(state: TagState): Map<String, Any?> = mapOf(
    "uid" to DemoTag.UID,
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
