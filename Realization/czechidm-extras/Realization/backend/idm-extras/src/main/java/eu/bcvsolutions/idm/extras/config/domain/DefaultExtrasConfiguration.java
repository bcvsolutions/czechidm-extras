package eu.bcvsolutions.idm.extras.config.domain;

import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.config.domain.AbstractConfiguration;

/**
 * Extras configuration - implementation
 * 
 * @author peter.sourek@bcvsolutions.eu
 *
 */
@Component("extrasConfiguration")
public class DefaultExtrasConfiguration 
		extends AbstractConfiguration
		implements ExtrasConfiguration {
}
