# First-device validation matrix

Run the matrix on the phones and SIM routes you plan to support. Record Android version, phone model, carrier, dual-SIM state, Wi-Fi state, and whether permissions were granted.

| Area | Check | Expected behavior |
|---|---|---|
| Install | Install `app-debug.apk` and launch | App opens without a crash; Overview is reachable |
| Permissions | Deny phone/location access | App remains usable and labels radio fields as limited |
| Permissions | Grant phone/location access | Radio type/strength appears when the phone exposes it |
| Live AR | Select **Live AR scan**, grant camera access, and slowly pan around a textured room | Real camera feed opens, tracking changes to locked, feature points accumulate, and the HUD remains smooth |
| Live AR | Tap a detected floor/table/wall surface | A spatial node appears, the node count increments, and the selected spot is ready to verify |
| Live AR | Walk 3–5 m, turn around, and return near the origin | Path/origin telemetry updates; tracking loss pauses capture and re-alignment resumes it |
| Live AR fallback | Use a phone without ARCore or deny camera access | The app stays usable and guided pins remain available; no fake AR result is shown |
| Route | Turn Wi-Fi off and use Jio data | Cellular route can be verified and a scan can complete |
| Route | Repeat with Airtel data | Carrier selector and result history show Airtel |
| Dual SIM | Put Jio + Airtel in the same phone | The app never claims a route is verified unless Android exposes the active data subscription |
| Map | Tap five different positions | New spots are added, nearby taps select an existing spot, and the UI stays responsive |
| Scan | Start, wait, then stop | Stop returns to an idle state without a stuck spinner |
| Budget | Run 25 MB, 100 MB, and 300 MB caps | Data-used row stays visible and never exceeds the selected cap by design |
| Offline | Disable mobile data mid-run | Scan exits safely with a clear error and no fake score |
| Rotation | Rotate if the device policy allows it | State remains coherent; no duplicate run starts |
| Accessibility | TalkBack + 200% font | Controls have meaningful labels and content remains readable |
| Dark mode | Switch system theme | Contrast remains readable and map markers remain distinguishable |
