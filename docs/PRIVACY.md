# Privacy and data use

Room Mapper is designed to be useful without an account or cloud sync.

## Stored locally

- Room name and approximate width/height.
- Marked spot positions and the result summary for each spot.
- The selected carrier, use profile, scan mode, and data budget.
- The most recent 50 history rows.

This information is kept in the app's private storage and is removed when the user clears history or uninstalls the app. The app does not collect precise location trails or upload room maps.

## Network requests

During a verification, the app contacts a small latency endpoint and a bounded download/upload endpoint over HTTPS. The requests are used to calculate the on-device result and are not tied to an account by this app. The exact carrier route is shown only when Android exposes an active data subscription.

## Permissions

Phone state and location access are optional for map testing but help Android expose cell signal metrics. If the user declines them, the app keeps working with route and performance measurements and labels unavailable fields instead of guessing.

## User controls

The data budget is visible before every run, scan cancellation is safe, and history can be deleted from the History tab. Treat the results as diagnostics rather than a guarantee of service quality.
