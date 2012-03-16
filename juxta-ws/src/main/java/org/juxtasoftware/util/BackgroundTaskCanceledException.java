package org.juxtasoftware.util;

public class BackgroundTaskCanceledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BackgroundTaskCanceledException() {
        super();
    }

    public BackgroundTaskCanceledException(String message, Throwable cause) {
        super(message, cause);
    }

    public BackgroundTaskCanceledException(String message) {
        super(message);
    }

    public BackgroundTaskCanceledException(Throwable cause) {
        super(cause);
    }

}
