package org.juxtasoftware.service;

import org.junit.After;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import eu.interedition.text.rdbms.RelationalNameRepository;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
@Transactional
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"classpath:/applicationContext-dataSource.xml", "classpath:/applicationContext-service.xml"})
public abstract class AbstractTest extends AbstractTransactionalJUnit4SpringContextTests {

    @Autowired protected RelationalNameRepository nameRepository;

    @After
    public void clearNameCache() {
        nameRepository.clearCache();
    }
}
