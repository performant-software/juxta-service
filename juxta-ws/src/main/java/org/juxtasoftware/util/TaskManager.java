package org.juxtasoftware.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.time.DateUtils;
import org.juxtasoftware.Constants;
import org.juxtasoftware.util.BackgroundTaskStatus.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Singleton manager of outstanding tasks for the web service. This manager 
 * ensures that only 1 task is executing at a time
 * 
 * @author loufoster
 *
 */
@Component
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class TaskManager {

    @Autowired @Qualifier("executor") private TaskExecutor taskExecutor;
    @Autowired @Qualifier("collate-executor") private TaskExecutor collateExecutor;
    private ConcurrentHashMap<String, BackgroundTask> taskMap = new ConcurrentHashMap<String, BackgroundTask>(50);
    private final SimpleDateFormat dateFormater = new SimpleDateFormat("MM/dd H:mm:ss:SSS");
    private static final Logger LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME );
  
    
    /**
     * Submit a named task to be executed in a new thread and have its status monitored
     * Tasks are named based upon operation and id of operand. If a task already exists
     * with this name, don't create another unless the existing task is done.
     * 
     * @param newTask
     */
    public void submit( BackgroundTask newTask ) {
        LOG.info("Task "+newTask.getName()+" submitted to manager");
        BackgroundTask task = this.taskMap.get(newTask.getName());
        boolean createNewTask = false;
        if ( task == null ) {
           LOG.info("Task "+newTask.getName()+" does not exist. Create");
           createNewTask = true;
        } else {
            if ( isDone(task) ) {
                LOG.info("Task "+newTask.getName()+" exists, but is done");
                this.taskMap.remove( task.getName() );
                createNewTask = true;
            } else {
                LOG.info("Task "+task.getName()+" already exists; not creating another");
            }
        }
        
        if ( createNewTask ) {
            LOG.info("Create NEW task "+newTask.getName());
            this.taskMap.put( newTask.getName(), newTask );
            // exec collate requests in thread pool that only allows a 
            // small number of concurrent tasks
            if ( newTask.getType().equals(BackgroundTask.Type.COLLATE) || newTask.getType().equals(BackgroundTask.Type.VISUALIZE) ) {
                this.collateExecutor.execute(newTask);
            } else {
                // All other tasks are streamed and need less bandwidth. Use thread pool that alows
                // more simultaneous tasks
                this.taskExecutor.execute(newTask);
            }
        }
    }
    
    @Scheduled(fixedRate=30000)
    public void manageQueue() {
        // every 30 secs, check for completed tasks that are more than 
        // 30 minutes old. remove them.
        List<String> killList = new ArrayList<String>();
        for ( Entry<String, BackgroundTask> entry  : this.taskMap.entrySet() ) {
            BackgroundTask task = entry.getValue();
            if ( isDone(task) ) {
                Date endPlus30 = DateUtils.addMinutes(task.getEndTime(), 30);
                Date now = new Date();
                if ( endPlus30.before( now) ) {
                    LOG.info("Expiring completed task "+entry.getKey());
                    killList.add(entry.getKey());
                }
            }
        }
        
        for ( String key : killList ) {
            this.taskMap.remove(key);
        }
        killList.clear();
    }
    
    private boolean isDone( final BackgroundTask task ) {
        return (task.getStatus().equals(Status.CANCELLED) || 
                task.getStatus().equals(Status.COMPLETE) ||
                task.getStatus().equals(Status.FAILED) );
    }
    
    public void cancel( final String taskName  ) {
        BackgroundTask task = this.taskMap.get( taskName );
        if ( task != null ) {
            task.cancel();
        }
    }
    
    public boolean exists( final String name ) {
        BackgroundTask task = this.taskMap.get( name );
        return (task != null && task.getStatus().equals(Status.PROCESSING) );
    }
    
    public String getStatus( final String name ) {
        BackgroundTask task = this.taskMap.get( name );
        if ( task != null ) {
            String start = this.dateFormater.format( task.getStartTime() );
            String end = "";
            if ( task.getEndTime() != null ) {
                end = this.dateFormater.format( task.getEndTime() );
            }
            return "{\"status\": \""+task.getStatus()+
                "\", \"note\": \"" + task.getMessage() +
                "\", \"started\": \""+start+"\", \"finished\": \""+end+"\"}";
        } else {
            return "{\"status\": \"UNAVAILABLE\"}";
        }
        
    }
}
