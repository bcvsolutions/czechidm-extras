package eu.bcvsolutions.idm.extras.config.domain;

import eu.bcvsolutions.idm.core.api.service.Configurable;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Extras configuration - interface
 * s
 * @author peter.sourek@bcvsolutions.eu
 */
public interface ExtrasConfiguration extends Configurable {

	String EXTRAS_TITLES_AFTER = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".configuration.titlesAfter";
	String EXTRAS_TITLES_BEFORE = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".configuration.titlesBefore";
	String EXTRAS_TITLES_SOURCE_SEPARATOR = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".configuration.titlesSourceSeparator";
	String EXTRAS_TITLES_AFTER_SEPARATOR = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".configuration.titlesAfterSeparator";
	String EXTRAS_TITLES_BEFORE_SEPARATOR = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".configuration.titlesBeforeSeparator";
	String EXTRAS_SYSTEM_EXCHANGE_ID =
			IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID +
					".configuration.systemId";
	
	String EXTRAS_APPROVAL_WF_GUARANTEE_TYPE_A = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".wf.approval.guaranteeTypeA";
	String EXTRAS_APPROVAL_WF_GUARANTEE_TYPE_B = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".wf.approval.guaranteeTypeB";
	String EXTRAS_APPROVAL_WF_CUSTOM_SCRIPT = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".wf.approval.customScript";
	String EXTRAS_APPROVAL_WF_APPROVER_STATES = IdmConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + ExtrasModuleDescriptor.MODULE_ID + ".wf.approval.approver.states";

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
	 * Return titles source separator
	 *
	 * @return
	 */
	String getTitlesSourceSeparator();
	
	/**
	 * Return titles after separator used in idm
	 *
	 * @return
	 */
	String getTitlesAfterSeparator();
	
	/**
	 * Return titles before separator used in idm
	 *
	 * @return
	 */
	String getTitlesBeforeSeparator();

	UUID getSystemId();
	
	/**
	 * Return property with code of role guarantees for A type
	 *
	 * @return
	 */
	String getRoleGuaranteeTypeA();
	
	/**
	 * Return property with code of role guarantees for B type
	 *
	 * @return
	 */
	String getRoleGuaranteeTypeB();
	
	/**
	 * Return property with code of custom script for role approvers
	 *
	 * @return
	 */
	String getCustomApprovalScriptCode();

	/**
	 * Return property valid states of role assign approver
	 *
	 * @return
	 */
	List<String> getValidApproverStates();
}
