# Now Playing Overlay for Kiosk Satellite

Display a current-song card or a compact Music Assistant queue over Fotoo or Kiosk Satellite screensavers. Choose the HA media_player for the intended speaker group. Now Playing follows that entity's playback state and MA `active_queue`; it does not change the audio output.

## Settings and actions

Configure speaker, card appearance, cover art, progress, pause behavior, overlay target and the usual three visibility fields. **Queue overlay: compact** shows a short current/upcoming list with cover art; **standard Now Playing** returns to the normal card. The compact view retains Now Playing's own screensaver and visibility rules.

The next song is read automatically from MA's selected queue when available, with HA metadata as fallback. There is no **Next track entity** or **Show next track** setting. Connect MA in Kiosk for direct queue access; Now Playing reuses that URL/token without following an unrelated Kiosk audio source.

## Standalone Party split

Full-screen Party, visualizations, guest QR and their actions belong to the separate [Party Mode project](plugins/party-mode/README.md). Party has its own plugin ID, speaker and visibility. Now Playing yields while standalone Party is active and returns to its own visibility afterward.

The split is staged on `party-mode-split` until the dedicated Party repository is available. The latest stable Now Playing remains 0.2.3 during this transition. Party's prerelease in this repository is a build preview, not a Kiosk repository installation URL.

## Build

```sh
python3 tools/build.py --android-platform 35
```

CI builds both projects and verifies MA queues, real QR decoding, guest-access matching, audio frame bounds and Party activation/visibility. On-device layout and live server access need kiosk verification.
