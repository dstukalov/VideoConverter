package com.dstukalov.videoconverter;

public class Preconditions {

    public static void checkState(final Object errorMessage, final boolean expression) {
        if (!expression) {
            throw new IllegalStateException(String.valueOf(errorMessage));
        }
    }
}
