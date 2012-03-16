package org.juxtasoftware.util;

import java.text.SimpleDateFormat;
import java.util.concurrent.ConcurrentHashMap;

import org.juxtasoftware.util.BackgroundTaskStatus.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Singleton manager of outstanding tasks for the web service
 * 
 * @author loufoster
 *
 */
@Component
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class TaskManager {

    @Autowired @Qualifier("executor") private TaskExecutor taskExecutor;
    private ConcurrentHashMap<String, BackgroundTask> taskMap = new ConcurrentHashMap<String, BackgroundTask>(50);
    private final SimpleDateFormat dateFormater = new SimpleDateFormat("MM/dd H:mm:ss:SSS");
    private static final Logger LOG = LoggerFactory.getLogger( TaskManager.class.getName());
  
    
    /**
     * Submit a named task to be executed in a new thread and have its status monitored
     * Tasks are named based upon operation and id of operand. If a task already exists
     * with this name, don't create another unless the existing task is done.
     * 
     * @param newTask
     */
    public void submit( BackgroundTask newTask ) {
        LOG.info("Task "+newTask.getName()+" submitted to manager");
        BackgroundTask task = this.taskMap.get(newTask);
        boolean createNewTask = false;
        if ( task == null ) {
           createNewTask = true;
        } else {
            if ( task.getStatus().equals(Status.CANCELED) || 
                 task.getStatus().equals(Status.COMPLETE) ||
                 task.getStatus().equals(Status.FAILED) ) {
                this.taskMap.remove( task.getName() );
                createNewTask = true;
            } else {
                LOG.info("Task "+task.getName()+" already exists; not creating another");
            }
        }
        
        if ( createNewTask ) {
            this.taskMap.put( newTask.getName(), newTask );
            this.taskExecutor.execute(newTask);
        }
    }
    
    public void cancel( final String taskName  ) {
        BackgroundTask task = this.taskMap.get( taskName );
        if ( task != null ) {
            task.cancel();
        }
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
