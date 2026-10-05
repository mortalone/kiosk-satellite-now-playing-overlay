# Now Playing Overlay 0.2.2

- Add six native full-screen Party effects plus None: Neon Spectrum, Mirror Spectrum, Radial Pulse, Waveform, Star Particles and Neon Tunnel.
- Add saved actions and an in-screen menu for effects, full queue/current-song layout and guest QR visibility.
- Read bounded audio frames from Spectrum Visualizer 0.2.10 using its current source, gain and refresh rate. No second audio capture. Animated source is explicitly marked as demo.
- Follow MA Party guest access using the server's actual join link. Validate the Party queue matches the selected MA entity; hide mismatched or unavailable guest QR.
- Generate the QR locally with bundled MIT Nayuki library; no external QR service, persisted join link, logged token or administrator token in the QR.
- Support local/remote links and correct legacy MA localhost advertisement to the configured server host.
- Add independent QR decoding tests, queue matching/link validation and bounded audio-frame tests.

Choose the same MA group in Now Playing entity and MA Party Player. Enable Guest Access in MA. The QR opens MA's guest request interface; it does not modify Spotify's own live Connect queue. This is a native Party presentation, not a MilkDrop preset engine or a full lyrics/karaoke dashboard.

All builds/tests are automated; Android rendering, local server connectivity, guest scanning and audio capture still need kiosk verification. Update all three companions: Now Playing 0.2.2, Visualizer 0.2.10, Quick Actions 0.2.5.

MA guest access can also be enabled/disabled from the Party menu or actions.
This writes only `enable_guest_access` on the single enabled Party instance
whose explicit Party Player matches the selected MA queue. Auto/ambiguous or
unmatched instances are not changed. Your MA token needs permission to read
and update that provider configuration. Disabling access revokes guest access
as defined by MA; hiding the QR alone does not disable guest access.
