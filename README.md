
# Notes Specific To This Repo
I am not an actual app developer, so my fix and additions will be limited to what can be done.

I will primarily work on demo app, adding feature, changing layout or theme.

I have already added extra options to resolution, bitrate and removed useless lower resolution to make room for extra variety in useful options.

~~Next i will try to add features like saving video with original name and original modified time and saving them to a user accessible location instead of /sdcard/Android/data .~~  **Done**

~~As of v1.9 most of the layout changes are finished and now i intend to work on how app handles video file, with this change app will move on to v2 with a new package name, new app name and probably a new icon as well.~~  **Done**

With v2.2 onwards app now directly accesses file from internal storage without caching, and writes compressed videos directly to /sdcard/Movies/Compressed-Videos

## NEW APP
![app-new](https://github.com/user-attachments/assets/1c202117-4ba3-4d95-b85f-49a891b0f705)


## OLD APP
![app-old](https://github.com/user-attachments/assets/146e901b-c8cc-4374-8c96-0231aacc1ae7)


## Important things to consider
• Big files have issues in compression (Something i cannot fix).

• Files with same name will be overwritten.

• Files are now directly read and written to internal storage without caching.

• App is only tested on samsung devices running oneui 7 and oneui 6.

• Thoroughly play compressed video before deleting original file.

## Affiliations
Got App Icon from flaticons [Video icons created by vectaicon - Flaticon](https://www.flaticon.com/free-icons/video)

***
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.dstukalov/videoconverter/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.dstukalov/videoconverter)
[![GitHub license](https://img.shields.io/badge/license-Apache%202-brightgreen.svg)](https://raw.githubusercontent.com/dstukalov/VideoConverter/master/LICENSE)

# VideoConverter
Video file conversion library based on <a href="https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java">ExtractDecodeEditEncodeMuxTest.java</a> CTS test

## Installation
VideoConverter is installed by adding the following dependency to your `app/build.gradle` file:
```groovy
dependencies {
    implementation 'com.dstukalov:videoconverter:1.10'
}
```
You may also need to add the following to your `project/build.gradle` file:
```groovy
repositories {
    ...
    mavenCentral()
}
```

## Usage
```java
MediaConverter converter = new MediaConverter();
converter.setInput(context, uri);
converter.setOutput(outputStream);
converter.setTimeRange(timeFrom, timeTo);
converter.setVideoResolution(360);
converter.setVideoBitrate(2000000);
converter.setAudioBitrate(128000);

converter.setListener(percent -> {
    publishProgress(percent);
    return isCancelled();
});

converter.convert();
```

## Demo
<a href="https://play.google.com/store/apps/details?id=com.dstukalov.videoconverter">
  <img alt="Android app on Google Play" src="https://developer.android.com/images/brand/en_app_rgb_wo_45.png" />
</a>