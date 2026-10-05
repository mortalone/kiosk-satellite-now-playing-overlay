# Now Playing Overlay 0.2.4

- Move full-screen Party to an independent `party-mode` plugin with its own visibility and actions.
- Retain the compact queue overlay and ordinary current-song card in Now Playing.
- Remove Next track entity and Show next track settings; read upcoming metadata from the selected MA speaker's active queue.
- Enrich metadata only from that selected queue, reject old queue responses and clear stale next-track metadata on queue changes.
- Yield while standalone Party is active, then restore Now Playing's own screensaver/visibility rules.

Install the separate Party plugin with https://github.com/mortalone/kiosk-satellite-party-mode. Update Spectrum Visualizer to 0.2.11 and Quick Actions to 0.2.6. Enable Party Mode, select its MA speaker and use its own Start/Stop actions. Automated builds and queue tests passed; on-device rendering and MA access require kiosk verification.
