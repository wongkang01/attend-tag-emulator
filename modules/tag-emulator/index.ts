import { NativeModule, requireNativeModule } from 'expo';

/** "ready" when the phone can act as the tag right now. */
export type NfcStatus = 'ready' | 'off' | 'no-nfc' | 'no-hce';

export type TagStatus = {
  uid: string;
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
  getStatus(): TagStatus;
  setReplayMode(enabled: boolean): TagStatus;
  /** Back to counter 42 with no reads served. */
  resetCounter(): TagStatus;
}

export default requireNativeModule<TagEmulatorModule>('TagEmulator');
