// @ts-check
const { AndroidConfig, withAndroidManifest } = require('expo/config-plugins');

const SERVICE = 'sg.edu.sutd.attend.tagemulator.nfc.TagApduService';
const HCE_FEATURE = 'android.hardware.nfc.hce';

/** @typedef {import('expo/config-plugins').AndroidConfig.Manifest.ManifestApplication} ManifestApplication */
/** @typedef {import('expo/config-plugins').AndroidConfig.Manifest.ManifestMetaData} ManifestMetaData */
// Expo's service type has no meta-data field, though Android accepts it on <service>.
/** @typedef {NonNullable<ManifestApplication['service']>[number] & { 'meta-data': ManifestMetaData[] }} HceService */

/** @type {HceService} */
const hceService = {
  $: {
    'android:name': SERVICE,
    'android:exported': 'true',
    'android:permission': 'android.permission.BIND_NFC_SERVICE',
  },
  'intent-filter': [{ action: [{ $: { 'android:name': 'android.nfc.cardemulation.action.HOST_APDU_SERVICE' } }] }],
  'meta-data': [
    { $: { 'android:name': 'android.nfc.cardemulation.host_apdu_service', 'android:resource': '@xml/apduservice' } },
  ],
};

/**
 * Registers TagApduService as a Host Card Emulation service, with the NFC permission and HCE feature.
 * The AID group lives in the module's res/xml/apduservice.xml, merged into the app at build time.
 * @type {import('expo/config-plugins').ConfigPlugin}
 */
const withTagEmulator = (config) => {
  const withPermission = AndroidConfig.Permissions.withPermissions(config, ['android.permission.NFC']);
  return withAndroidManifest(withPermission, (mod) => {
    const manifest = mod.modResults.manifest;
    // Not required, so the app still installs on phones without HCE and can say so on screen.
    manifest['uses-feature'] = [
      ...(manifest['uses-feature'] ?? []).filter((f) => f.$['android:name'] !== HCE_FEATURE),
      { $: { 'android:name': HCE_FEATURE, 'android:required': 'false' } },
    ];

    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(mod.modResults);
    app.service = [
      ...(app.service ?? []).filter((s) => s.$['android:name'] !== SERVICE),
      hceService,
    ];
    return mod;
  });
};

module.exports = withTagEmulator;
