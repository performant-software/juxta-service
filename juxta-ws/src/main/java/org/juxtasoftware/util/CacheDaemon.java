package org.juxtasoftware.util;

import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.CacheDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class CacheDaemon {
    @Autowired private CacheDao cacheDao;
    private static final Logger DEBUG_LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME );

    @Scheduled(cron="0 0 0/1 * * *")
    public void logMetrics() {
        DEBUG_LOG.info("Clearing cache "+this.cacheDao);
        this.cacheDao.purgeExpired();
    }
}
