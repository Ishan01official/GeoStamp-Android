# Address completeness investigation and verification

## Root cause

Compared the pre-fix baseline `761e00a`, regression commit `2e5910e`, and its parent.
Before `2e5910e`, `AddressResolver` preferred `getAddressLine(0)`, then fell back
to locality, state and country. The regression replaced that with a restricted
component parser, discarding all provider address lines and `featureName`.
It also hid house numbers by default and required agreeing lookups when enabled.
The cache stored the already reduced string, so formatting settings could not
recover lost information from it.

The connected phone's live responses confirm that Nav Shakti Dham exists only
in the provider line: `thoroughfare` and `subThoroughfare` are null. The previous
parser therefore could not retain the colony or the numeric feature value.
Both the short and full addresses identify the same locality correctly.

GeoStamp uses Android's platform `Geocoder`, not Nominatim, for addresses.
OpenStreetMap supplies the optional map thumbnail separately. Provider and locale
configuration did not change in the regression. No external geocoder fallback is
present. GPS policy and a 4.1 m coordinate difference are not the cause of the
missing components demonstrated here.

## Live provider comparison

Device: Nothing A142, Android 16 / API 36. Locale: English.
Queried both coordinates on 2026-10-10 using the actual device geocoder, initially
with one result, then with up to five. Both queries returned the same first result
and the same five-result list in the recorded comparison.

| Requested coordinate | First address line |
| --- | --- |
| 29.007923, 77.767677 | 98, Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India |
| 29.007960, 77.767680 | 98, Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India |

Raw fields of the first result, identical for both queries:

```text
addressLines[0] = 98, Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India
featureName = 98
subThoroughfare = null
thoroughfare = null
subLocality = Ganga Nagar
locality = Meerut
subAdminArea = Meerut Division
adminArea = Uttar Pradesh
postalCode = 250001
countryCode = IN
countryName = India
latitude = 29.007994900000003
longitude = 77.7676488
phone = null
url = null
extras = null
```

Additional raw results, in provider order, also identical for both queries:

| Address line | Provider latitude | Provider longitude |
| --- | --- | --- |
| 89, Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India | 29.007883000000003 | 77.767603 |
| 107, Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India | 29.008031799999998 | 77.76750849999999 |
| 2Q59+65C, Ganga Nagar, Meerut, Uttar Pradesh 250001, India | 29.0080533 | 77.76788789999999 |
| Global City Rd, Ganga Nagar, Meerut, Uttar Pradesh 250001, India | 29.007633333784153 | 77.76666852341256 |

The numeric provider value is currently 98, not the historical example's 87.
This confirms current availability of additional detail; it does not establish
the physical house number. No example values are hardcoded in production code.
Different candidate house numbers refer to different provider positions and must
not be combined or selected just because their strings are longer.

## Resulting behavior

Settings → Location → Address detail offers:

| Level | Output from the recorded first result |
| --- | --- |
| Detailed (default) | 98, Nav Shakti Dham, Ganga Nagar, Meerut, Meerut Division, Uttar Pradesh 250001, India |
| Standard | Nav Shakti Dham, Ganga Nagar, Meerut, Uttar Pradesh 250001, India |
| Short | Ganga Nagar, Meerut, Uttar Pradesh, India |

All provider lines and structured components are retained in memory. Detailed
formatting keeps supplied text and adds missing distinct components in geographic
order. Standard removes known house/building/street components while preserving
remaining provider line segments, including the colony in this response. Short
uses named structured locality, city, state and country fields. Unclassified
provider text is not used to invent missing structured geographical levels.

The resolver requests up to five results. A richer result can replace the
provider's primary result only when it describes the same returned position and
retains the primary address components without conflicting with them. Different
nearby buildings and streets are not merged. Cancellation callbacks are guarded.

Compatible detail upgrades are accepted immediately. A reduced compatible response
does not replace a more complete address while stationary. Conflicting results
still require repeated confirmation and the existing hold window, or meaningful
movement. A held address keeps its original spatial anchor.

The cache stores components rather than formatted strings. Detail changes reformat
them without a network request, including detected-address editing, preview, photo,
video and gallery batch paths. Cache reuse and address capture validity are limited
to 10 m instead of the old 60 m cache radius / 40 m capture radius, because the
cache now contains building-level information. GPS accuracy, GPS refresh policy,
stamp renderer, map renderer and manual-override semantics are unchanged.

The old house-number preference no longer gates completeness; missing or unknown
new detail settings default to Detailed. A subsequent user-requested change makes
address lookup and map thumbnails default on when no stored choice exists;
weather stays opt-in. Existing saved off choices remain off. Location permission
is still optional, and neither default changes GPS accuracy or map rendering.
Manual addresses still win verbatim and retain their existing metadata marker.

## Validation

```sh
./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.liveGeocoding=true --offline
```

Coverage includes provider-line-only details; all structured levels; three detail
formats; duplicate suppression; both requested coordinates in both lookup orders;
compatible upgrades; conflicting raw and structured addresses; movement; cache
reformatting and expiry; persisted settings and legacy preference handling; manual
overrides; existing stamp, GPS, camera and video unit regressions; on-device Android
Address parsing, candidate selection, live responses and photo pipeline tests.
The existing video pipeline test uses `/tmp/GeoStamp_video_test.mp4`, a 3.68 s
GeoStamp H.264/AAC recording. Android blocked app access to a directory created
by the shell in external storage. An optional instrumentation argument now allows
the same test to use app-private fixtures while retaining its original default.
After installing the APKs, the recording was copied into app-private `files/verify`
and the full suite was run directly:

```sh
adb shell am instrument -w -r -e liveGeocoding true \
  -e videoFixtureDirectory /data/user/0/com.geostamp.camera/files/verify \
  com.geostamp.camera.test/androidx.test.runner.AndroidJUnitRunner
```

Final results: 113 unit tests passed; all eight device tests passed with no skips.
Build succeeded. Lint has zero errors and 20 dependency-version warnings from the
unchanged dependency declarations. `git diff --check` passed.

Subsequent default-service verification: 115 unit tests passed after adding
default-on / persisted-opt-out coverage. `testDebugUnitTest assembleDebug lintDebug`
passed with zero lint errors and the same 20 dependency-version warnings. English
and Hindi settings descriptions now match the defaults. The updated production APK
was installed on the connected phone with `adb install -r`; the eight-device-test
result above is from the preceding address verification, not a new device suite.

Video input and output both measured 3676 ms, 1080 × 1920 upright, rotation 90°,
with video and audio tracks present. Stamping completed in 4.0 s. Photo pipeline
tests checked JPEG brightness, rotation, stamp placement and the saved scene.

Raw coordinates are logged only by the explicitly enabled instrumentation test,
not by production address lookup. Replaying live tests can yield different house
numbers or missing data as the device provider changes.

Android's address field definitions are documented in the
[Android Address API](https://developer.android.com/reference/android/location/Address).
