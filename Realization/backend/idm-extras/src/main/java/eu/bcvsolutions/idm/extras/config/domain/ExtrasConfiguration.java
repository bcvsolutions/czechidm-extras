package eu.bcvsolutions.idm.extras.config.domain;

import java.util.ArrayList;
import java.util.List;

import eu.bcvsolutions.idm.core.api.service.Configurable;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;

/**
 * Extras configuration - interface
 * 
 * @author peter.sourek@bcvsolutions.eu
 */
public interface ExtrasConfiguration extends Configurable {

	String EXTRAS_TITLES_AFTER = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".configuration.titlesAfter";
	String EXTRAS_TITLES_BEFORE = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".configuration.titlesBefore";

	// cross AD
	String EXTRAS_CROSS_AD_CODE_LIST = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".configuration.cross.codeList";

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

	/**
	 * return code of codeList where we store uuid of cross ad systems
	 *
	 * @return
	 */
	String getCrossAdCodeList();
}
