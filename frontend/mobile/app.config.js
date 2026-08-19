const fs = require('node:fs');
const path = require('node:path');

const base = require('./app.json').expo;

function configuredFile(environmentName, fallbackName) {
  const configured = process.env[environmentName]?.trim();
  const candidate = configured || fallbackName;
  const absolute = path.isAbsolute(candidate) ? candidate : path.resolve(__dirname, candidate);
  return fs.existsSync(absolute) ? candidate : null;
}

module.exports = () => {
  const androidFirebase = configuredFile('GOOGLE_SERVICES_JSON', 'google-services.json');
  const iosFirebase = configuredFile('GOOGLE_SERVICE_INFO_PLIST', 'GoogleService-Info.plist');
  const firebaseConfigured = Boolean(androidFirebase || iosFirebase);
  const plugins = [...base.plugins, '@sentry/react-native/expo'];
  if (firebaseConfigured) {
    plugins.push('@react-native-firebase/app', '@react-native-firebase/crashlytics');
  }

  return {
    ...base,
    plugins,
    ios: {
      ...base.ios,
      bundleIdentifier: 'com.unispeaking.mobile',
      ...(iosFirebase ? { googleServicesFile: iosFirebase } : {}),
    },
    android: {
      ...base.android,
      ...(androidFirebase ? { googleServicesFile: androidFirebase } : {}),
    },
    extra: {
      ...base.extra,
      firebaseCrashlyticsAndroidConfigured: Boolean(androidFirebase),
      firebaseCrashlyticsIosConfigured: Boolean(iosFirebase),
    },
  };
};
