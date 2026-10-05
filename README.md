# Now Playing Overlay

Native Now Playing overlay for Kiosk Satellite screensavers and Fotoo, with stable Home Assistant artwork and Music Assistant metadata enrichment.

Install in **Kiosk Satellite → Plugin Manager → Add plugin** using this repository URL:

`https://github.com/mortalone/kiosk-satellite-now-playing-overlay`

The plugin supports Kiosk Satellite's own screensavers and Fotoo. On Kiosk Satellite's Home Assistant Media screensaver it uses the in-Activity overlay path, so **Legacy WebView renderer can stay Off**.

## Main features

- Stable cover art from the selected Home Assistant media player.
- Optional Music Assistant metadata enrichment.
- Top / Center / Bottom placement.
- Width, opacity, progress bar, time labels, playlist/source and next-track display.
- Works with the same overlay architecture tested on Raspberry Pi.

## Migration

Do not run this standalone Now Playing plugin at the same time as the Now Playing part of the old combined Screensaver Overlay, or duplicate cards may appear.


## 0.2.1: Party queue presentation

Actions:

- **Party Mode: compact overlay (saved)** switches the Now Playing card to a compact queue stack. It previews for two minutes, then follows the existing screensaver/visibility settings. The layout choice survives app restarts.
- **Party Mode: open full screen in Kiosk** opens a dedicated native view in Kiosk, with an opaque softly blurred cover background, readable current track and neighboring queue items. It has a close button, keeps the screen on, and does not require an active screensaver. Leaving Kiosk closes this temporary full-screen mode.
- **Party Mode: use standard Now Playing** exits full screen and restores the standard card layout.
- **Hide Now Playing** also closes the Party presentation.

Choose the **Music Assistant** HA media_player for the intended speaker/group
in **Now Playing entity**. Its `active_queue` identifies the exact queue to
read, including grouped playback. Configure Music Assistant's server and token
in Kiosk Satellite's Music Assistant connection settings. Keep Kiosk's Media
Player source as **This device / Sendspin Player** if using digital visualizer:
Party presentation does not change the playback source.

If queue access is unavailable, available HA title/artwork can still show the
current track; earlier/upcoming items are not invented. Artwork and queue
requests are bounded. Queue requests are read only; `host.control` is used
only to stop/postpone the screensaver and hide stock page/player presentation.

Update **Spectrum Visualizer to 0.2.9** and **Quick Actions to 0.2.4** so their
spectrum/debug/quick-action overlays hide during full-screen Party Mode.
Their prior choices return after closing. Compact mode does not suppress them.

This is the queue display inspired by Music Assistant Party. It does not
enable guest access or implement song requests/QR codes. Hardware verification
is required for native view composition and the kiosk's local MA connection.
