[ ![Download](https://api.bintray.com/packages/dstukalov/VideoConverter/VideoConverter/images/download.svg) ](https://bintray.com/dstukalov/VideoConverter/VideoConverter/_latestVersion)

# VideoConverter
Video file conversion library based on <a href="https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java">ExtractDecodeEditEncodeMuxTest.java</a> CTS test

## Installation
VideoConverter is installed by adding the following dependency to your build.gradle file:

    dependencies {
        implementation 'com.dstukalov:videoconverter:1.4'
    }

## Usage
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
