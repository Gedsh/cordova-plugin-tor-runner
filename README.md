# Cordova plugin - Tor runner

*Android Cordova (Capacitor) plugin for running Tor. Provides local SOCKS5 and HTTP proxies for secure and anonymous traffic tunneling*

Plugin embeds a fully functional Tor runtime into an Android application built with the Cordova framework. It allows applications to route traffic through the Tor network using local proxy interfaces and supports flexible traffic routing strategies, including selective tunneling for censored or blocked resources.

This plugin is designed for developers who need fine-grained control over Tor lifecycle, bridge management, and traffic routing on Android without relying on external applications.

### Tor
* Provides anonymous network routing
* Hides user IP address and location
* Protects against traffic analysis
* Circumvents censorship and network blocking
* Routes traffic through multiple Tor relays
* Open-source

## Traffic Routing Modes

### Automatic mode
* Routes only blocked or restricted resources through Tor
* Automatically starts Tor when required
* Automatically stops Tor when no longer needed
* Reduces performance overhead and battery usage

### Always mode
* Routes all application traffic through Tor
* Tor runs as soon as the app is launched
* Maximum anonymity and privacy

### None mode
* Tor is fully disabled
* All traffic is routed directly

## Bridges

* Bundled bridges for all supported Tor bridge types
* Automatic request of fresh bridges from the official Tor Project infrastructure
* Support for using Tor relays as vanilla bridges
* Automatic filtering of non-working bridges
* Periodic verification of bridges that become unavailable over time

This allows the plugin to remain functional in highly censored network environments without manual bridge configuration.

## Technology Stack

Android native code:
* Java
* Kotlin
* Dagger (dependency injection)
* Kotlin Coroutines (concurrency and background processing)
* Clean architecture

Hybrid layer:
* JavaScript
* Cordova framework

The project demonstrates integration between JavaScript and native Android code, lifecycle-safe background processing, and long-running network services.

## Use Cases

* Selective circumvention of network censorship
* Privacy-preserving mobile applications
* Secure networking for hybrid Android apps
* Research and development of Tor-based Android solutions
* Applications that require automated Tor bridge management

## Compatibility

Android platform only
Cordova-based applications

Root access is not required.

## Support

Bug reports and feature requests should be submitted via GitHub issues.

There is no support for unrelated topics.

## Contributing

Contributions are welcome.

Developers are expected to be able to build and debug the project independently.

## Donations
**Patreon**: https://www.patreon.com/inviziblepro

**BTC**: 1GfJwiHG6xKCQCpHeW6fELzFfgsvcSxVUR

**LTC**: MGgumSzrC91E8DMMSnH4nRBMepeMjQtxGe

**BCH**: qzl4w4ahh7na2z23056qawwdyuclkgty5gc4q8tw88

**USDT**: 0xdA1Dd53FE6501140E3Dcd5134323dfccF20aD536

**XLM**: GBID6I3VYR4NIFLZWI3MEQH3M2H72COC3HQDI5WMYYQGAC3TE55TSKAX

**XMR** 82WFzofvGUdY52w9zCfrZWaHVqEDcJH7y1FujzvXdGPeU9UpuFNeCvtCKhtpC6pZmMYuCNgFjcw5mHAgEJQ4RTwV9XRhobX

## License

[GNU General Public License version 3](https://www.gnu.org/licenses/gpl-3.0.txt)

Copyright (c) 2025-2026 Garmatin Oleksandr invizible.soft@gmail.com

All rights reserved

This file is part of **Cordova Plugin Tor Runner**.

**Cordova Plugin Tor Runner** is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your discretion) any later version.

**Cordova Plugin Tor Runner** is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with **Cordova Plugin Tor Runner**. If not, see [http://www.gnu.org/licenses/](https://www.gnu.org/licenses/)

This product is produced independently from the **Tor®** software and carries no guarantee from The Tor Project about quality, suitability or anything else.


