# Release checklist

- [ ] GitHub Actions is green for tests, lint, and `assembleDebug`.
- [ ] The APK was installed on each supported Android version and phone model.
- [ ] Jio, Airtel, dual-SIM, Wi-Fi-off, offline, permission-denied, and cancellation cases were exercised.
- [ ] Real measured values were checked against a second trusted speed test; score weights were reviewed.
- [ ] Privacy copy, data-budget copy, and endpoint ownership have been reviewed for the intended launch market.
- [ ] A release keystore is stored outside the repository; signing is configured only in protected GitHub secrets.
- [ ] A release workflow is added only after the debug workflow is stable; never publish the unsigned debug APK as production.
- [ ] Store listing explains that results are point-in-time room diagnostics, not a carrier guarantee.
