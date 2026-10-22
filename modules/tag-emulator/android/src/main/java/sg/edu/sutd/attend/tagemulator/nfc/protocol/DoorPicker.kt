package sg.edu.sutd.attend.tagemulator.nfc.protocol

/** Where the selected door id is kept: SharedPreferences on Android, memory in the JVM check. */
interface SelectionStore {
  fun load(): String?
  fun save(doorId: String)
}

/**
 * The demo doors, each with its own [DoorTag] and counter, and which one the phone acts as.
 * The APDU layer serves [selected] on every read, so a new selection applies from the next tap.
 */
class DoorPicker(
  storeFor: (Door) -> TagStore,
  private val selection: SelectionStore,
  doors: List<Door> = DemoDoors.ALL,
  clock: () -> Long = System::currentTimeMillis,
) {
  /** Called after every served reading, whichever door served it. */
  @Volatile var listener: ((ServedReading) -> Unit)? = null

  private val tags: Map<String, DoorTag> = doors.associate { door ->
    door.id to DoorTag(door, storeFor(door), clock).also { tag -> tag.listener = { listener?.invoke(it) } }
  }

  @Volatile private var selectedId: String =
    selection.load()?.takeIf { it in tags } ?: (DemoDoors.DEFAULT.id.takeIf { it in tags } ?: doors.first().id)

  val doors: List<Door> = doors

  fun selected(): DoorTag = tags.getValue(selectedId)

  /** Makes [doorId] the door the phone acts as and remembers it across restarts. */
  @Synchronized fun select(doorId: String): DoorTag {
    require(doorId in tags) { "unknown door $doorId" }
    selectedId = doorId
    selection.save(doorId)
    return selected()
  }
}
