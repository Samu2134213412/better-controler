package com.bettercontroller.client.screen;

/** A screen that can show a one-line status message from the server. */
public interface StatusSink {
    void showStatus(String message, boolean error);
}
