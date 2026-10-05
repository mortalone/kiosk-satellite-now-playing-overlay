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
