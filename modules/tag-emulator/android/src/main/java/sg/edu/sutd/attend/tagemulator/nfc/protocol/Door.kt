package sg.edu.sutd.attend.tagemulator.nfc.protocol

/** One door tag the phone can act as. The keys are public demo keys, not secrets: they are published in the README. */
data class Door(val id: String, val label: String, val uid: String, val keyHex: String, val room: String)

/** The demo doors the Attend seed data registers. */
object DemoDoors {
  val LT2_DOOR_A = Door("lt2-door-a", "LT2 Door A", "04A1B2C3D45E80", "6b1f0c2a9d3e4f5061728394a5b6c7d8", "LT2")
  val LT2_DOOR_B = Door("lt2-door-b", "LT2 Door B", "04A1B2C3D45E81", "7c2a1d3b0e4f5a6172839405b6c7d8e9", "LT2")
  val DOOR_2506 = Door("2506-door", "2.506 Door", "04A1B2C3D45E82", "8d3b2e4c1f5a6b7283940516c7d8e9fa", "2.506")

  val ALL = listOf(LT2_DOOR_A, LT2_DOOR_B, DOOR_2506)
  val DEFAULT = LT2_DOOR_A

  fun byId(id: String?): Door? = ALL.firstOrNull { it.id == id }

  /** 41 is used by the Attend mock's seeded data, so every door starts one above it. */
  const val FIRST_COUNTER = 42
  const val MAX_COUNTER = 0xFFFFFF
}
