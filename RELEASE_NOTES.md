# Now Playing Overlay 0.2.1

- Add Party Mode: compact overlay (saved), Party Mode: open full screen in Kiosk, and Party Mode: use standard Now Playing actions.
- Compact presentation uses a 216 dp area with a highlighted current track, up to two earlier and two upcoming tracks, cover art, titles and a progress bar. It follows the existing position, width, opacity and visibility settings.
- Full-screen Party Mode opens Kiosk Satellite, stops the screensaver, shows an opaque cover-colored background and a queue, keeps the screen awake, and provides a close button. It exits when the kiosk leaves the foreground.
- Visualizer 0.2.9 and Quick Actions 0.2.4 cooperate to hide their overlays while full-screen Party Mode is active and restore them afterwards.
- Read the selected Music Assistant HA entity's active_queue attribute. Never substitute the kiosk Sendspin player or another active queue.
- Reuse Kiosk Satellite's configured Music Assistant server/token for read-only queue requests. Host control is used only for presentation, never for audio playback or player-source changes.
- Fetch five queue items around the current index, with current/next fallback if history is unavailable. Bound responses, artwork downloads and cover decode size. No invented queue history.
- Add queue tests for identity matching, start/end boundaries, changed windows, empty queues and artwork URLs.

Setup: choose the Music Assistant media_player for the intended group in Now Playing entity, and configure the Music Assistant server/token in Kiosk Satellite's Music Assistant connection settings. Keep Media Player source at This device / Sendspin Player for the digital visualizer. This plugin's Party Mode is a queue presentation; it does not enable guest access, QR codes or MA's guest song-request plugin.

The update adds host.control capability for screen presentation. Update the Visualizer and Quick Actions companions as well for a clean full screen. Build/tests are automated; on-device rendering and the local MA connection still need verification.
