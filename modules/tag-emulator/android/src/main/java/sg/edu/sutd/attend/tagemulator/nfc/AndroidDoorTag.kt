package sg.edu.sutd.attend.tagemulator.nfc

import android.content.Context
import sg.edu.sutd.attend.tagemulator.nfc.protocol.DoorTag
import sg.edu.sutd.attend.tagemulator.nfc.protocol.TagState
import sg.edu.sutd.attend.tagemulator.nfc.protocol.TagStore

/** The one DoorTag per app process, shared by the HCE service and the JS module. */
object AndroidDoorTag {
  @Volatile private var instance: DoorTag? = null

  fun get(context: Context): DoorTag =
    instance ?: synchronized(this) {
      instance ?: DoorTag(PrefsTagStore(context.applicationContext)).also { instance = it }
    }
}

private class PrefsTagStore(context: Context) : TagStore {
  private val prefs = context.getSharedPreferences("attend_tag_state", Context.MODE_PRIVATE)

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
