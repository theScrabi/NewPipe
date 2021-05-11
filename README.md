<p align="center"><a href="https://newpipe.net"><img src="assets/new_pipe_icon_5.png" width="150"></a></p> 
<h2 align="center"><b>NewPipe</b></h2>
<h4 align="center">A libre lightweight streaming frontend for Android.</h4>

<p align="center"><a href="https://f-droid.org/packages/org.schabi.newpipe/"><img src="https://f-droid.org/wiki/images/0/06/F-Droid-button_get-it-on.png"></a></p>

<p align="center">
<a href="https://github.com/TeamNewPipe/NewPipe/releases" alt="GitHub release"><img src="https://img.shields.io/github/release/TeamNewPipe/NewPipe.svg" ></a>
<a href="https://www.gnu.org/licenses/gpl-3.0" alt="License: GPLv3"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg"></a>
<a href="https://github.com/TeamNewPipe/NewPipe/actions" alt="Build Status"><img src="https://github.com/TeamNewPipe/NewPipe/workflows/CI/badge.svg?branch=dev&event=push"></a>
<a href="https://hosted.weblate.org/engage/newpipe/" alt="Translation Status"><img src="https://hosted.weblate.org/widgets/newpipe/-/svg-badge.svg"></a>
<a href="https://webchat.freenode.net/#newpipe" alt="IRC channel: #newpipe"><img src="https://img.shields.io/badge/IRC%20chat-%23newpipe-brightgreen.svg"></a>
<a href="https://www.bountysource.com/teams/newpipe" alt="Bountysource bounties"><img src="https://img.shields.io/bountysource/team/newpipe/activity.svg?colorB=cd201f"></a>
</p>
<hr>
<p align="center"><a href="#screenshots">Screenshots</a> &bull; <a href="#description">Description</a> &bull; <a href="#features">Features</a> &bull; <a href="#installation-and-updates">Installation and updates</a> &bull; <a href="#contribution">Contribution</a> &bull; <a href="#donate">Donate</a> &bull; <a href="#license">License</a></p>
<p align="center"><a href="https://newpipe.net">Website</a> &bull; <a href="https://newpipe.net/blog/">Blog</a> &bull; <a href="https://newpipe.net/FAQ/">FAQ</a> &bull; <a href="https://newpipe.net/press/">Press</a></p>
<hr>

*Read this in other languages: [English](README.md), [Español](README.es.md), [한국어](README.ko.md), [Soomaali](README.so.md), [Português Brasil](README.pt_BR.md), [日本語](README.ja.md), [Română](README.ro.md), [Türkçe](README.tr.md).*

<b>WARNING: THIS IS A BETA VERSION, THEREFORE YOU MAY ENCOUNTER BUGS. IF YOU DO, OPEN AN ISSUE VIA OUR GITHUB REPOSITORY.</b>

<b>PUTTING NEWPIPE OR ANY FORK OF IT INTO THE GOOGLE PLAY STORE VIOLATES THEIR TERMS AND CONDITIONS.</b>

## Screenshots

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot_01.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/shot_01.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot_02.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/shot_02.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot_03.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/shot_03.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot_04.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/shot_04.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot_05.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/shot_05.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot_06.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/shot_06.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot_07.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/shot_07.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot_08.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/shot_08.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot_09.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/shot_09.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot_10.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/shot_10.png)
[<img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/shot_11.png" width=405>](fastlane/metadata/android/en-US/images/tenInchScreenshots/shot_11.png)
[<img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/shot_12.png" width=405>](fastlane/metadata/android/en-US/images/tenInchScreenshots/shot_12.png)

## Description

NewPipe does not use any Google framework libraries, nor the YouTube API. Websites are only parsed to fetch required info, so this app can be used on devices without Google services installed. Also, you don't need a YouTube account to use NewPipe, which is copylefted libre software.

### Features

* Search videos
* No Login Required
* Display general info about videos
* Watch YouTube videos
* Listen to YouTube videos
* Popup mode (floating player)
* Select streaming player to watch video with
* Download videos
* Download audio only
* Open a video in Kodi
* Show next/related videos
* Search YouTube in a specific language
* Watch/Block age restricted material
* Display general info about channels
* Search channels
* Watch videos from a channel
* Orbot/Tor support (not yet directly)
* 1080p/2K/4K support
* View history
* Subscribe to channels
* Search history
* Search/watch playlists
* Watch as enqueued playlists
* Enqueue videos
* Local playlists
* Subtitles
* Livestream support
* Show comments

### Supported Services

NewPipe supports multiple services. Our [docs](https://teamnewpipe.github.io/documentation/) provide more info on how a new service can be added to the app and the extractor. Please get in touch with us if you intend to add a new one. Currently supported services are:

* YouTube
* SoundCloud \[beta\]
* media.ccc.de \[beta\]
* PeerTube instances \[beta\]
* Bandcamp \[beta\]

<!-- Hidden span to keep old links compatible. -->
<span id="updates"></span>

## Installation and updates
You can install NewPipe using one of the following methods:
 1. Add our custom repo to F-Droid and install it from there. The instructions are here: https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Download the APK from [Github Releases](https://github.com/TeamNewPipe/NewPipe/releases) and install it.
 3. Update via F-Droid. This is the slowest method of getting updates, as F-Droid must recognize changes, build the APK itself, sign it, then push the update to users.
 4. Build a debug APK yourself. This is the fastest way to get new features on your device, but is much more complicated, so we recommend using one of the other methods.

We recommend method 1 for most users. APKs installed using method 1 or 2 are compatible with each other, but not with those installed using method 3. This is due to the same signing key (ours) being used for 1 and 2, but a different signing key (F-Droid's) being used for 3. Building a debug APK using method 4 excludes a key entirely. Signing keys help ensure that a user isn't tricked into installing a malicious update to an app.

In the meanwhile, if you want to switch sources for some reason (e.g. NewPipe's core functionality was broken and F-Droid doesn't have the update yet), we recommend following this procedure:
1. Back up your data via Settings > Content > Export Database so you keep your history, subscriptions, and playlists
2. Uninstall NewPipe
3. Download the APK from the new source and install it
4. Import the data from step 1 via Settings > Content > Import Database

## Contribution
Whether you have ideas, translations, design changes, code cleaning, or real heavy code changes, help is always welcome.
The more is done the better it gets!

If you'd like to get involved, check our [contribution notes](.github/CONTRIBUTING.md).

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Donate
If you like NewPipe we'd be happy about a donation. You can either send bitcoin or donate via Bountysource or Liberapay. For further info on donating to NewPipe, please visit our [website](https://newpipe.net/donate).

<table>
  <tr>
    <td><img src="https://bitcoin.org/img/icons/logotop.svg" alt="Bitcoin"></td>
    <td><img src="assets/bitcoin_qr_code.png" alt="Bitcoin QR code" width="100px"></td>
    <td><samp>16A9J59ahMRqkLSZjhYj33n9j3fMztFxnh</samp></td>
  </tr>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
  <tr>
    <td><a href="https://www.bountysource.com/teams/newpipe"><img src="https://upload.wikimedia.org/wikipedia/commons/thumb/2/22/Bountysource.png/320px-Bountysource.png" alt="Bountysource" width="190px"></a></td>
    <td><a href="https://www.bountysource.com/teams/newpipe"><img src="assets/bountysource_qr_code.png" alt="Visit NewPipe at bountysource.com" width="100px"></a></td>
    <td><a href="https://www.bountysource.com/teams/newpipe/issues"><img src="https://img.shields.io/bountysource/team/newpipe/activity.svg?colorB=cd201f" height="30px" alt="Check out how many bounties you can earn."></a></td>
  </tr>
</table>

## Privacy Policy

The NewPipe project aims to provide a private, anonymous experience for using media web services.
Therefore, the app does not collect any data without your consent. NewPipe's privacy policy explains in detail what data is sent and stored when you send a crash report, or comment in our blog. You can find the document [here](https://newpipe.net/legal/privacy/).

## License
[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)  

NewPipe is Free Software: You can use, study share and improve it at your
will. Specifically you can redistribute and/or modify it under the terms of the
[GNU General Public License](https://www.gnu.org/licenses/gpl.html) as
published by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.  
