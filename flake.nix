{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-25.05"; # update only after gradle gets java 25 support
    flake-parts.url = "github:hercules-ci/flake-parts";
    systems.url = "github:nix-systems/default";
  };

  outputs = inputs:
    inputs.flake-parts.lib.mkFlake { inherit inputs; } {
      systems = import inputs.systems;

      perSystem = { config, self', pkgs, lib, system, ... }: let
        # java = pkgs.jetbrains.jdk-no-jcef;
        java = pkgs.jdk24;

        nativeBuildInputs = with pkgs; [
          java
          git
        ];

        buildInputs = with pkgs; [
          libGL
          glfw-wayland-minecraft # Renamed to glfw3-minecraft in 25.11. Not always needed, but in case it is, it's here.
          flite # TTS
          libpulseaudio # Required for audio
        ];
      in {
        devShells.default = pkgs.mkShell {
          inherit nativeBuildInputs buildInputs;

          env = {
            LD_LIBRARY_PATH = lib.makeLibraryPath buildInputs;
            JAVA_HOME = "${java.home}";
          };
        };
      };
    };
}
