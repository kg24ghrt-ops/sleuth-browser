# Security policy

## Supported versions

| Version | Supported |
| --- | --- |
| 1.0.x | yes |

## Reporting a vulnerability

Please do not open a public issue for a security problem. Use GitHub's
[private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)
on this repository, or contact the maintainer directly.

You can expect an acknowledgement within 72 hours and a fix or mitigation plan within 14 days.

## What Sleuth does with data

- Captures, case files, evidence and history are stored in the app's private directory.
- Nothing is uploaded, and the app contains no analytics or telemetry.
- The only way data leaves the app is an explicit export or share action taken by the user.
- Incognito mode skips history and case recording and clears cookies when toggled.
- The tracker denylist is a static list compiled into the app; no lookup ever touches a network.

## Hardening notes

- `javascript:`, `data:`, `file:`, `content:` and `intent:` URLs are rejected by the URL parser.
- File access and content access are disabled on the WebView.
- Mixed content is configurable and defaults to the compatibility mode.
- Geolocation is denied unless explicitly enabled in settings.
- SSL errors are not ignored.
