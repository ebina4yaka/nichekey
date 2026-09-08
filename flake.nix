{
  description = "Dev environment for the Android Japanese keyboard app";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forAll = nixpkgs.lib.genAttrs systems;
    in {
      devShells = forAll (system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config.allowUnfree = true;
            config.android_sdk.accept_license = true;
          };
          android = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ "35" ];
            buildToolsVersions = [ "35.0.0" "34.0.0" ];
            includeEmulator = true;
            includeSystemImages = true;
            systemImageTypes = [ "google_apis" ];
            abiVersions = [ "x86_64" ];
            includeCmake = false;
            includeNDK = false;
          };
        in {
          default = pkgs.mkShell {
            packages = [ pkgs.jdk17 pkgs.gradle android.androidsdk ];
            JAVA_HOME = "${pkgs.jdk17.home}";
            ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
            shellHook = ''
              # NixOS: AGP が Maven から落とす glibc 版 aapt2 は動かないため
              # nix SDK 同梱の aapt2 を Gradle プロパティで強制する
              export GRADLE_USER_HOME="$PWD/.gradle-home"
              mkdir -p "$GRADLE_USER_HOME"
              printf 'android.aapt2FromMavenOverride=%s/build-tools/35.0.0/aapt2\n' "$ANDROID_HOME" > "$GRADLE_USER_HOME/gradle.properties"
            '';
          };
        });
    };
}
