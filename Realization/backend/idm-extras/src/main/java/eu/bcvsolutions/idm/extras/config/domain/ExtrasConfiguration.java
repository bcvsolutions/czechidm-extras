package eu.bcvsolutions.idm.extras.config.domain;

import java.util.ArrayList;
import java.util.List;

import eu.bcvsolutions.idm.core.api.service.Configurable;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;

/**
 * Extras configuration - interface
 * 
 * @author peter.sourek@bcvsolutions.eu
 */
public interface ExtrasConfiguration extends Configurable {

	String EXTRAS_TITLES_AFTER = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + "bee.configuration.titlesAfter";
	String EXTRAS_TITLES_BEFORE = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + "bee.configuration.titlesBefore";

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

	/**
	 * Return titles after dictionary
	 *
	 * @return
	 */
	List<String> getTitlesAfter();

	/**
	 * Return titles before dictionary
	 *
	 * @return
	 */
	List<String> getTitlesBefore();
}
