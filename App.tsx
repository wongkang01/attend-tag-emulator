import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import {
  Alert,
  AppState,
  Pressable,
  ScrollView,
  StatusBar as RNStatusBar,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';

import TagEmulator, { type NfcStatus, type TagStatus } from './modules/tag-emulator';

const DOOR = 'LT2 Door A';

const UNAVAILABLE: Partial<Record<NfcStatus, string>> = {
  'no-nfc': 'This phone has no NFC, so it cannot act as the door tag.',
  'no-hce': 'This phone has NFC but no card emulation (HCE), so it cannot act as the door tag.',
};

const ctrHex = (counter: number) => counter.toString(16).padStart(6, '0');

export default function App() {
  const [status, setStatus] = useState<TagStatus>(() => TagEmulator.getStatus());
  const [lastReplayed, setLastReplayed] = useState(false);

  useEffect(() => {
    const read = TagEmulator.addListener('onRead', (event) => {
      setStatus(event.status);
      setLastReplayed(event.replayed);
    });
    // NFC may have been switched on or off in Settings while the app was away.
    const app = AppState.addEventListener('change', (state) => {
      if (state === 'active') setStatus(TagEmulator.getStatus());
    });
    return () => {
      read.remove();
      app.remove();
    };
  }, []);

  const unavailable = UNAVAILABLE[status.nfc];

  const confirmReset = () =>
    Alert.alert('Reset counter?', 'The next read serves counter 42 again.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Reset',
        style: 'destructive',
        onPress: () => {
          setStatus(TagEmulator.resetCounter());
          setLastReplayed(false);
        },
      },
    ]);

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <StatusBar style="light" />
      <Text style={styles.app}>Attend Tag</Text>
      <Text style={styles.door}>{DOOR}</Text>

      {unavailable ? (
        <Text style={styles.warning}>{unavailable}</Text>
      ) : (
        <>
          {status.nfc === 'off' && <Text style={styles.warning}>NFC is off. Turn it on in Settings.</Text>}

          <Row label="UID" value={status.uid} />
          <Row label="Next counter" value={`${status.nextCounter} (${ctrHex(status.nextCounter)})`} />
          <Row
            label="Last served"
            value={
              status.lastServedCounter === null
                ? 'none'
                : `${status.lastServedCounter} (${ctrHex(status.lastServedCounter)})${lastReplayed ? ' replay' : ''}`
            }
          />
          <Row label="Reads served" value={String(status.readsServed)} />
          <Row
            label="Last read"
            value={status.lastReadAt === null ? 'none' : new Date(status.lastReadAt).toLocaleTimeString()}
          />

          <View style={[styles.row, styles.control]}>
            <Text style={styles.label}>Replay last reading</Text>
            <Switch
              value={status.replayArmed}
              onValueChange={(enabled) => setStatus(TagEmulator.setReplayMode(enabled))}
              disabled={status.lastServedCounter === null}
            />
          </View>
          <Pressable onPress={confirmReset} style={({ pressed }) => [styles.button, pressed && styles.pressed]}>
            <Text style={styles.buttonText}>Reset counter</Text>
          </Pressable>

          <Text style={styles.note}>Keep NFC on and this screen on and unlocked, then hold the backs of the phones together.</Text>
        </>
      )}
      <Text style={styles.note}>The tag key in this app is a public demo key, not a secret.</Text>
    </ScrollView>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.value}>{value}</Text>
    </View>
  );
}

const colors = {
  bg: '#0d0d0d',
  text: '#ececec',
  muted: '#8c8c8c',
  line: '#262626',
  warning: '#f2b84b',
};

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { paddingHorizontal: 16, paddingTop: (RNStatusBar.currentHeight ?? 0) + 16, paddingBottom: 32 },
  app: { color: colors.muted, fontSize: 13 },
  door: { color: colors.text, fontSize: 28, fontWeight: '600', marginTop: 2, marginBottom: 12 },
  warning: { color: colors.warning, fontSize: 15, marginVertical: 8 },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.line,
  },
  control: { paddingVertical: 4 },
  label: { color: colors.muted, fontSize: 15 },
  value: { color: colors.text, fontSize: 15, fontFamily: 'monospace' },
  button: {
    marginTop: 16,
    paddingVertical: 12,
    alignItems: 'center',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.muted,
  },
  pressed: { backgroundColor: colors.line },
  buttonText: { color: colors.text, fontSize: 15 },
  note: { color: colors.muted, fontSize: 13, marginTop: 16 },
});
