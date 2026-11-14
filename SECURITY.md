# Security Policy

PLANR is an offline planner that can lock your phone (Off-Grid Mode, Lockdown)
and holds your alarms, reminders, notes and screen-time history on-device.
Security and privacy are the whole trust story, so reports are taken seriously.

## Supported versions

Only the [newest release](https://github.com/PATILYASHH/PLANR/releases)
receives security fixes. From **5 December 2026** that also means the
Google Play and F-Droid builds.

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Use GitHub's private vulnerability reporting instead:

1. Go to the repository's **Security** tab
2. Click **Report a vulnerability**
3. Describe the issue, steps to reproduce, impact, and your PLANR and Android versions

You should receive a response within a few days. Once fixed, the report will be
credited (unless you prefer otherwise) in the release notes.

## Scope notes

- PLANR has **no server and no `INTERNET` permission**. There is no backend,
  API or cloud component to test.
- In scope:
  - getting out of **Off-Grid Mode** or **Lockdown** without the unlock
    challenge (other than the documented limits: Safe Mode, quick-settings
    toggles, a pre-set accessibility shortcut)
  - another app driving PLANR's exported components (accessibility service,
    receivers, the planned ExoSynk Link provider) to read data or change rules
  - anything that leaks alarms, reminders, notes, contacts or usage data off the device
  - backup files that could be crafted to crash PLANR or inject unsafe data on restore
- The APKs published on GitHub Releases (and, after launch, Google Play and
  F-Droid) are the only official builds. Verify downloads against
  `SHA256SUMS.txt` attached to each release.
