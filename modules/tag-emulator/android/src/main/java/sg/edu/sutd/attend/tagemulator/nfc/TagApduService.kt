package sg.edu.sutd.attend.tagemulator.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import sg.edu.sutd.attend.tagemulator.nfc.protocol.Type4Tag

/** Android entry point for the emulated tag; all protocol logic lives in Type4Tag and DoorTag. */
class TagApduService : HostApduService() {
  private lateinit var tag: Type4Tag

  override fun onCreate() {
    super.onCreate()
    val door = AndroidDoorTag.get(this)
    tag = Type4Tag { door.serve().reading.ndefMessage() }
  }

  override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray = tag.process(commandApdu)

  override fun onDeactivated(reason: Int) = tag.deactivated()
}
