package eu.bcvsolutions.idm.extras.config.domain;

import java.util.ArrayList;
import java.util.List;

import eu.bcvsolutions.idm.core.api.service.Configurable;

/**
 * Extras configuration - interface
 * 
 * @author peter.sourek@bcvsolutions.eu
 */
public interface ExtrasConfiguration extends Configurable {

	@Override
	default String getConfigurableType() {
		// please define your own configurable type there
		return "extras";
	}
	
	@Override
	default List<String> getPropertyNames() {
		List<String> properties = new ArrayList<>(); // we are not using superclass properties - enable and order does not make a sense here
		return properties;
	}
}
