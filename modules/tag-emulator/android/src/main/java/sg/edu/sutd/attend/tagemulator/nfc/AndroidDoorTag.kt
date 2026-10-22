package sg.edu.sutd.attend.tagemulator.nfc

import android.content.Context
import sg.edu.sutd.attend.tagemulator.nfc.protocol.DemoDoors
import sg.edu.sutd.attend.tagemulator.nfc.protocol.Door
import sg.edu.sutd.attend.tagemulator.nfc.protocol.DoorPicker
import sg.edu.sutd.attend.tagemulator.nfc.protocol.SelectionStore
import sg.edu.sutd.attend.tagemulator.nfc.protocol.TagState
import sg.edu.sutd.attend.tagemulator.nfc.protocol.TagStore

/** The one DoorPicker per app process, shared by the HCE service and the JS module. */
object AndroidDoorTag {
  @Volatile private var instance: DoorPicker? = null

  fun get(context: Context): DoorPicker =
    instance ?: synchronized(this) {
      val app = context.applicationContext
      instance ?: DoorPicker({ PrefsTagStore(app, it) }, PrefsSelectionStore(app)).also { instance = it }
    }
}

private class PrefsSelectionStore(context: Context) : SelectionStore {
  private val prefs = context.getSharedPreferences("attend_tag_door", Context.MODE_PRIVATE)

  override fun load(): String? = prefs.getString("doorId", null)

  override fun save(doorId: String) {
    prefs.edit().putString("doorId", doorId).commit()
  }
}

/** LT2 Door A keeps the prefs file of the one-door app, so its counter carries over. */
private fun prefsName(door: Door): String =
  if (door == DemoDoors.LT2_DOOR_A) "attend_tag_state" else "attend_tag_state_${door.id}"

private class PrefsTagStore(context: Context, door: Door) : TagStore {
  private val prefs = context.getSharedPreferences(prefsName(door), Context.MODE_PRIVATE)

  override fun load(): TagState {
    val defaults = TagState()
    return TagState(
      nextCounter = prefs.getInt("nextCounter", defaults.nextCounter),
      lastServedCounter = if (prefs.contains("lastServedCounter")) prefs.getInt("lastServedCounter", 0) else null,
      readsServed = prefs.getInt("readsServed", 0),
      lastReadAtMillis = if (prefs.contains("lastReadAtMillis")) prefs.getLong("lastReadAtMillis", 0) else null,
      replayArmed = prefs.getBoolean("replayArmed", false),
    )
  }

  // commit() rather than apply(): the counter must be on disk before the reading leaves the phone.
  override fun save(state: TagState) {
    prefs.edit().apply {
      putInt("nextCounter", state.nextCounter)
      state.lastServedCounter?.let { putInt("lastServedCounter", it) } ?: remove("lastServedCounter")
      putInt("readsServed", state.readsServed)
      state.lastReadAtMillis?.let { putLong("lastReadAtMillis", it) } ?: remove("lastReadAtMillis")
      putBoolean("replayArmed", state.replayArmed)
    }.commit()
  }
}
