import { NativeModule, requireNativeModule } from 'expo';

/** "ready" when the phone can act as the tag right now. */
export type NfcStatus = 'ready' | 'off' | 'no-nfc' | 'no-hce';

/** A demo door the phone can act as. The key is a public demo key, not a secret. */
export type Door = {
  id: string;
  label: string;
  uid: string;
  /** 32 lowercase hex digits. */
  key: string;
  room: string;
};

/** The selected door's state; each door keeps its own counter. */
export type TagStatus = {
  door: Door;
  nextCounter: number;
  lastServedCounter: number | null;
  readsServed: number;
  /** Epoch milliseconds of the last served reading. */
  lastReadAt: number | null;
  /** The next read serves the previous reading again. */
  replayArmed: boolean;
  nfc: NfcStatus;
};

export type ReadEvent = {
  counter: number;
  url: string;
  replayed: boolean;
  at: number;
  status: TagStatus;
};

type TagEmulatorEvents = {
  onRead: (event: ReadEvent) => void;
};

declare class TagEmulatorModule extends NativeModule<TagEmulatorEvents> {
  getDoors(): Door[];
  getStatus(): TagStatus;
  /** Makes the door the one the phone acts as from the next tap, and remembers it across restarts. */
  selectDoor(doorId: string): TagStatus;
  /** Applies to the selected door. */
  setReplayMode(enabled: boolean): TagStatus;
  /** Back to counter 42 with no reads served, for the selected door only. */
  resetCounter(): TagStatus;
}

export default requireNativeModule<TagEmulatorModule>('TagEmulator');
