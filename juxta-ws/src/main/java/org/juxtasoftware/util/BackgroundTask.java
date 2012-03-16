package org.juxtasoftware.util;

import java.util.Date;

import org.juxtasoftware.util.BackgroundTaskStatus.Status;

/**
 * Interface that all asynchronous tasks submitted to the task manager must implement.
 * 
 * @author loufoster
 *
 */
public interface BackgroundTask extends Runnable {
    public Status getStatus();
    public String getName();
    public void cancel();
    public Date getStartTime();
    public Date getEndTime();
    public String getMessage();
}
