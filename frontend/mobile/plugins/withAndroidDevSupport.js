const { withMainApplication } = require("@expo/config-plugins");

const GENERATED_DEV_SUPPORT =
  "useDevSupport = ReactNativeFeatureFlags.useReactNativeLibraryBuildConfig()";
const DEBUG_DEV_SUPPORT = "useDevSupport = BuildConfig.DEBUG";

module.exports = function withAndroidDevSupport(config) {
  return withMainApplication(config, (nextConfig) => {
    if (nextConfig.modResults.language !== "kt") {
      return nextConfig;
    }

    const contents = nextConfig.modResults.contents;

    if (contents.includes(GENERATED_DEV_SUPPORT)) {
      nextConfig.modResults.contents = contents.replace(
        GENERATED_DEV_SUPPORT,
        DEBUG_DEV_SUPPORT,
      );
    }

    return nextConfig;
  });
};
