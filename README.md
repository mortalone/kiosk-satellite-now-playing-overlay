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

Update **Spectrum Visualizer to 0.2.10** and **Quick Actions to 0.2.5** so their
spectrum/debug/quick-action overlays hide during full-screen Party Mode.
Their prior choices return after closing. Compact mode does not suppress them.

## 0.2.3: Party guest QR and audio effects

In full screen, tap **⋯** to choose Neon Spectrum, Mirror Spectrum, Radial Pulse,
Waveform, Star Particles, Neon Tunnel or no effect. The same choices are actions,
and the selected effect survives restarts. **Show whole queue** can be switched
off for a small current-song card and more room for the visualization.

Update Spectrum Visualizer to **0.2.10**: it supplies bounded audio frames using
its existing source, gain and 10/20/30 FPS choices. Party does not create a second
capture source. Keep digital / Sendspin selected for music-reactive effects.
Animated source and microphone failure fallback are explicitly marked as demo.
Party effects fade when audio frames are missing or playback is paused. These are
native Canvas effects, not a MilkDrop preset runtime.

Guest QR follows **Music Assistant → Settings → Plugins → Party → Enable Guest
Access**. Select the same explicit group in MA's **Party Player** and this
plugin's **Now Playing entity**. The native view uses MA's actual `party/url`
join link, including Remote Access links, and confirms `party/player` matches
the selected entity's `active_queue`. Mismatches do not display a QR. The URL is
never written to preferences or logs, and no administrator token is in the QR.
QR display can be hidden by action or the menu. If MA changes/disables guest
access the QR disappears on the next check. Song requests and rate limits are
handled by MA's own guest interface; they target the MA queue, not Spotify's
separate live Connect queue. The screenshot is a presentation reference, not an
implementation of lyrics, karaoke or every MA Party dashboard feature.

Queue/link/audio normalization and independently decoded QR round-trip tests
run in CI. Native rendering, scan distance, the local MA connection and Android
audio behavior still need testing on your kiosk.

MA guest access can also be enabled/disabled from the Party menu or actions.
This writes only `enable_guest_access` on the single enabled Party instance
whose explicit Party Player matches the selected MA queue. Auto/ambiguous or
unmatched instances are not changed. Your MA token needs permission to read
and update that provider configuration. Disabling access revokes guest access
as defined by MA; hiding the QR alone does not disable guest access.
