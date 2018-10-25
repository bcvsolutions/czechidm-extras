package eu.bcvsolutions.idm.extras;

import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Initialize extras module
 * 
 * @author peter.sourek@bcvsolutions.eu
 *
 */
@Component
@DependsOn("initApplicationData")
public class ExtrasModuleInitializer implements ApplicationListener<ContextRefreshedEvent> {
 
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExtrasModuleInitializer.class);
	
	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		LOG.info("Module [{}] initialization", ExtrasModuleDescriptor.MODULE_ID);
	}
}