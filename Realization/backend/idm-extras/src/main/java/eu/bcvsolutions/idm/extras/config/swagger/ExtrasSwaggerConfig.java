package eu.bcvsolutions.idm.extras.config.swagger;

import eu.bcvsolutions.idm.core.api.config.swagger.AbstractSwaggerConfig;
import eu.bcvsolutions.idm.core.api.domain.ModuleDescriptor;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * Extras module swagger configuration
 *
 * @author peter.sourek@bcvsolutions.eu
 */
@Configuration
@ConditionalOnProperty(prefix = "springfox.documentation.swagger", name = "enabled", matchIfMissing = true)
public class ExtrasSwaggerConfig extends AbstractSwaggerConfig {

	@Autowired private ExtrasModuleDescriptor moduleDescriptor;

	@Override
	protected ModuleDescriptor getModuleDescriptor() {
		return moduleDescriptor;
	}

	@Bean
	public Docket extrasApi() {
		return api("eu.bcvsolutions.idm.rest");
	}
}
