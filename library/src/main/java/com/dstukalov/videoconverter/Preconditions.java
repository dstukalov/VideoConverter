package com.dstukalov.videoconverter;

import androidx.annotation.NonNull;

public class Preconditions {

    public static @NonNull <T extends Object> T checkNotNull(T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }

    public static void checkState(final Object errorMessage, final boolean expression) {
        if (!expression) {
            throw new IllegalStateException(String.valueOf(errorMessage));
        }
    }
}
