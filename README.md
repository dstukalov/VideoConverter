[![Maven Central](https://maven-badges.sml.io/sonatype-central/com.dstukalov.videoconverter/videoconverter/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.dstukalov.videoconverter/videoconverter)
[![GitHub license](https://img.shields.io/badge/license-Apache%202-brightgreen.svg)](https://raw.githubusercontent.com/dstukalov/VideoConverter/master/LICENSE)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/f4904c4356fd432c8e9edd85a5468c5c)](https://app.codacy.com/app/dstukalov/VideoConverter?utm_source=github.com&utm_medium=referral&utm_content=dstukalov/VideoConverter&utm_campaign=Badge_Grade_Dashboard)

# VideoConverter
Video file conversion library based on <a href="https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java">ExtractDecodeEditEncodeMuxTest.java</a> CTS test

## Installation
VideoConverter is installed by adding the following dependency to your `app/build.gradle` file:
```groovy
dependencies {
    implementation 'com.dstukalov.videoconverter:videoconverter:1.12'
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
