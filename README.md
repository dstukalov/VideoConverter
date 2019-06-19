[ ![Download](https://api.bintray.com/packages/dstukalov/VideoConverter/VideoConverter/images/download.svg) ](https://bintray.com/dstukalov/VideoConverter/VideoConverter/_latestVersion)

# VideoConverter
Video file conversion library based on <a href="https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java">ExtractDecodeEditEncodeMuxTest.java</a> CTS test

## Installation
VideoConverter is installed by adding the following dependency to your build.gradle file:

    dependencies {
        implementation 'com.dstukalov:videoconverter:1.1'
    }

## Usage
    mConverter = new Converter();
    mConverter.setInput(context, uri);
    mConverter.setOutput(outputStream);
    mConverter.setTimeRange(timeFrom, timeTo);
    mConverter.setVideoResolution(360);
    mConverter.setVideoBitrate(2000000);
    mConverter.setAudioBitrate(128000);

    mConverter.setListener(percent -> {
        publishProgress(percent);
        return isCancelled();
    });

    mConverter.convert();
