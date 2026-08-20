const fs = require('node:fs');
const path = require('node:path');

const {
  withDangerousMod,
  withMainApplication,
} = require('@expo/config-plugins');

const IMPORT_ANCHOR = 'import expo.modules.ExpoReactHostFactory';
const IMPORTS = `${IMPORT_ANCHOR}
import com.unispeaking.mobile.audio.WebRtcPcmTap
import com.unispeaking.mobile.audio.WebRtcPcmTapPackage`;
const PACKAGE_ANCHOR = '// Packages that cannot be autolinked yet can be added manually here, for example:';
const PACKAGE_REGISTRATION = `${PACKAGE_ANCHOR}
          add(WebRtcPcmTapPackage())`;
const ON_CREATE_ANCHOR = `override fun onCreate() {
    super.onCreate()`;
const INSTALLATION = `${ON_CREATE_ANCHOR}
    WebRtcPcmTap.install(this)`;

function addMainApplicationSetup(contents) {
  let next = contents;

  if (!next.includes('import com.unispeaking.mobile.audio.WebRtcPcmTap')) {
    if (!next.includes(IMPORT_ANCHOR)) {
      throw new Error('Could not locate the ExpoReactHostFactory import in MainApplication.kt');
    }
    next = next.replace(IMPORT_ANCHOR, IMPORTS);
  }

  if (!next.includes('add(WebRtcPcmTapPackage())')) {
    if (!next.includes(PACKAGE_ANCHOR)) {
      throw new Error('Could not locate the manual package anchor in MainApplication.kt');
    }
    next = next.replace(PACKAGE_ANCHOR, PACKAGE_REGISTRATION);
  }

  if (!next.includes('WebRtcPcmTap.install(this)')) {
    if (!next.includes(ON_CREATE_ANCHOR)) {
      throw new Error('Could not locate onCreate in MainApplication.kt');
    }
    next = next.replace(ON_CREATE_ANCHOR, INSTALLATION);
  }

  return next;
}

module.exports = function withWebRtcPcmTap(config) {
  const withApplication = withMainApplication(config, (nextConfig) => {
    if (nextConfig.modResults.language !== 'kt') {
      throw new Error('WebRtcPcmTap currently requires a Kotlin MainApplication');
    }
    nextConfig.modResults.contents = addMainApplicationSetup(
      nextConfig.modResults.contents,
    );
    return nextConfig;
  });

  return withDangerousMod(withApplication, [
    'android',
    async (nextConfig) => {
      const packageName = nextConfig.android?.package;
      if (!packageName) {
        throw new Error('android.package is required to install WebRtcPcmTap');
      }
      const destination = path.join(
        nextConfig.modRequest.platformProjectRoot,
        'app',
        'src',
        'main',
        'java',
        ...packageName.split('.'),
        'audio',
      );
      const sources = path.join(__dirname, 'android', 'webrtc-pcm-tap');
      fs.mkdirSync(destination, { recursive: true });
      for (const file of [
        'WebRtcPcmTap.kt',
        'WebRtcPcmTapModule.kt',
        'WebRtcPcmTapPackage.kt',
      ]) {
        fs.copyFileSync(path.join(sources, file), path.join(destination, file));
      }
      return nextConfig;
    },
  ]);
};

module.exports.addMainApplicationSetup = addMainApplicationSetup;
