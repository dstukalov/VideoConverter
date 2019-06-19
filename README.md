# VideoConverter
Video file conversion library based on <a href="https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java">ExtractDecodeEditEncodeMuxTest.java</a> CTS test

## Installation
VideoConverter is installed by adding the following dependency to your build.gradle file:

    dependencies {
        implementation 'com.dstukalov:videoconverter:1.0'
    }

## Usage
    mConverter = new VideoConverter();
    mConverter.setInput(context, uri);
    mConverter.setOutput(outputStream);
    mConverter.setTimeRange(timeFrom, timeTo);
    mConverter.setVideoResolution(360);
    mConverter.setVideoBitrate(2000000);
    mConverter.setAudioBitrate(128000);
    mConverter.setStreamable(true);

    mConverter.setListener(percent -> {
        publishProgress(percent);
        return isCancelled();
    });

    mConverter.convert();
