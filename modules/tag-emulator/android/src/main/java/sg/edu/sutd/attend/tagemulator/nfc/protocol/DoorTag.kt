package sg.edu.sutd.attend.tagemulator.nfc.protocol

/** Tag state that survives app restarts. */
data class TagState(
  val nextCounter: Int = DemoTag.FIRST_COUNTER,
  val lastServedCounter: Int? = null,
  val readsServed: Int = 0,
  val lastReadAtMillis: Long? = null,
  /** When set, the next read serves the previous reading again, then this clears itself. */
  val replayArmed: Boolean = false,
)

/** Where the tag state is kept: SharedPreferences on Android, memory in the JVM check. */
interface TagStore {
  fun load(): TagState
  fun save(state: TagState)
}

/** What one read was served, reported to the listener. */
data class ServedReading(val reading: TagReading, val replayed: Boolean, val atMillis: Long, val state: TagState)

/**
 * The door tag's counter and signing.
 * The APDU layer calls [serve] once per read; the app screen calls the other methods.
 */
class DoorTag(
  private val store: TagStore,
  private val uid: String = DemoTag.UID,
  private val keyHex: String = DemoTag.DEMO_KEY_HEX,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  @Volatile var listener: ((ServedReading) -> Unit)? = null

  private var state = store.load()

  @Synchronized fun state(): TagState = state

  /** Signs the next reading (or the previous one again in replay mode) and records it. */
  fun serve(): ServedReading {
    val served = synchronized(this) {
      val previous = state.lastServedCounter
      val replay = state.replayArmed && previous != null
      val counter = if (replay && previous != null) previous else state.nextCounter
      val now = clock()
      state = state.copy(
        nextCounter = if (replay) state.nextCounter else counter + 1,
        lastServedCounter = counter,
        readsServed = state.readsServed + 1,
        lastReadAtMillis = now,
        replayArmed = false,
      )
      store.save(state)
      ServedReading(TagReading.sign(uid, counter, keyHex), replay, now, state)
    }
    listener?.invoke(served)
    return served
  }

  @Synchronized fun setReplay(enabled: Boolean): TagState = update(state.copy(replayArmed = enabled))

  /** Back to counter 42 with no reads served. */
  @Synchronized fun reset(): TagState = update(TagState())

  private fun update(next: TagState): TagState {
    state = next
    store.save(next)
    return next
  }
}
