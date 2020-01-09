package eu.bcvsolutions.idm.extras.event.processor.provisioning;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.AccModuleDescriptor;
import eu.bcvsolutions.idm.acc.config.domain.ProvisioningConfiguration;
import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.dto.ProvisioningAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.event.processor.provisioning.PrepareConnectorObjectProcessor;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningArchiveService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningOperationService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.event.AbstractEntityEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.service.IdmPasswordPolicyService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.api.IcUidAttribute;
import eu.bcvsolutions.idm.ic.filter.api.IcFilter;
import eu.bcvsolutions.idm.ic.filter.api.IcResultsHandler;
import eu.bcvsolutions.idm.ic.filter.impl.IcEqualsFilter;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcObjectClassImpl;
import eu.bcvsolutions.idm.ic.impl.IcUidAttributeImpl;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * In case of AD system we will search all available AD systems to get all user roles from different servers
 *
 * @author Roman Kucera
 */
@Component
@Enabled(AccModuleDescriptor.MODULE_ID)
@Description("In case of AD system we will search all available AD systems to get all user roles from different servers.")
public class ExtrasCrossAdGroupsProvisioningProcessor extends AbstractEntityEventProcessor<SysProvisioningOperationDto> {

	public static final String PROCESSOR_NAME = "extras-cross-ad-groups-provisioning-processor";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(PrepareConnectorObjectProcessor.class);

	private final String MEMBER_ATTRIBUTE = "member";

	private final SysSystemMappingService systemMappingService;
	private final SysSystemAttributeMappingService attributeMappingService;
	private final IcConnectorFacade connectorFacade;
	private final SysSystemService systemService;
	private final SysProvisioningOperationService provisioningOperationService;
	private final SysSchemaAttributeService schemaAttributeService;
	private final SysSchemaObjectClassService schemaObjectClassService;
	private final ProvisioningConfiguration provisioningConfiguration;

	@Autowired
	private ProvisioningService provisioningService;
	@Autowired
	private LookupService lookupService;
	@Autowired
	private IdmPasswordPolicyService passwordPolicyService;
	@Autowired
	private ConfidentialStorage confidentialStorage;
	@Autowired
	private SysProvisioningAttributeService provisioningAttributeService;
	@Autowired
	private ExtrasConfiguration extrasConfiguration;

	@Autowired
	public ExtrasCrossAdGroupsProvisioningProcessor(
			IcConnectorFacade connectorFacade,
			SysSystemService systemService,
			SysSystemEntityService systemEntityService,
			NotificationManager notificationManager, // @deprecated @since 9.2.2
			SysProvisioningOperationService provisioningOperationService,
			SysSystemMappingService systemMappingService,
			SysSystemAttributeMappingService attributeMappingService,
			SysSchemaAttributeService schemaAttributeService,
			SysProvisioningArchiveService provisioningArchiveService,
			SysSchemaObjectClassService schemaObjectClassService,
			ProvisioningConfiguration provisioningConfiguration) {
		super(ProvisioningEventType.CREATE, ProvisioningEventType.UPDATE);
		//
		Assert.notNull(systemEntityService);
		Assert.notNull(systemMappingService);
		Assert.notNull(attributeMappingService);
		Assert.notNull(connectorFacade);
		Assert.notNull(systemService);
		Assert.notNull(notificationManager);
		Assert.notNull(provisioningOperationService);
		Assert.notNull(schemaAttributeService);
		Assert.notNull(provisioningArchiveService);
		Assert.notNull(schemaObjectClassService);
		Assert.notNull(provisioningConfiguration);
		//
		this.systemMappingService = systemMappingService;
		this.attributeMappingService = attributeMappingService;
		this.connectorFacade = connectorFacade;
		this.systemService = systemService;
		this.provisioningOperationService = provisioningOperationService;
		this.schemaAttributeService = schemaAttributeService;
		this.schemaObjectClassService = schemaObjectClassService;
		this.provisioningConfiguration = provisioningConfiguration;
	}

	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	/**
	 * Prepare provisioning operation execution
	 */
	@Override
	public EventResult<SysProvisioningOperationDto> process(EntityEvent<SysProvisioningOperationDto> event) {
		SysProvisioningOperationDto provisioningOperation = event.getContent();
		SysSystemDto system = systemService.get(provisioningOperation.getSystem());

		// if system is not AD for cross domain we do nothing
		List<UUID> adSystems = extrasConfiguration.getAdSystems();
		if (!adSystems.contains(system.getId())) {
			LOG.info("It's not AD system which should be used for cross domain do nothing special");
			return new DefaultEventResult<>(event, this);
		}

		String userDn = "";

		// load old DN, in event we have the new one but we need the old one for finding users groups
		IcObjectClass objectClass = provisioningOperation.getProvisioningContext().getConnectorObject()
				.getObjectClass();
		SysSystemEntityDto systemEntity = provisioningOperationService
				.getByProvisioningOperation(provisioningOperation);
		String uid = systemEntity.getUid();
		// load connector configuration
		IcConnectorConfiguration connectorConfig = systemService.getConnectorConfiguration(system);
		if (connectorConfig == null) {
			throw new ProvisioningException(AccResultCode.CONNECTOR_CONFIGURATION_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}
		try {
			IcConnectorObject existsConnectorObject;
				IcUidAttribute uidAttribute = new IcUidAttributeImpl(null, uid, null);
				existsConnectorObject = connectorFacade.readObject(system.getConnectorInstance(), connectorConfig,
						objectClass, uidAttribute);
			if (existsConnectorObject == null) {
				return new DefaultEventResult<>(event, this);
			} else {
				userDn = String.valueOf(existsConnectorObject.getAttributeByName("__NAME__").getValue());
			}
		} catch (Exception ex) {
			LOG.info("Error during getting user, do nothing", ex);
			return new DefaultEventResult<>(event, this);
		}

		// load grups
		IcObjectClass groupClass = new IcObjectClassImpl("__GROUP__");

		Set<String> userGroups = new HashSet<>();

		// find user on system
		String finalUserDn = userDn;
		adSystems.forEach(adSystem -> {
			SysSystemDto adSystemDto = systemService.get(adSystem);
			userGroups.addAll(findUserOnSystem(adSystemDto, groupClass, finalUserDn));
		});

		// check if user has more roles then in IdM = that can happen if he has roles from other servers then the normal search will not return all
		Map<ProvisioningAttributeDto, Object> fullAccountObject = provisioningOperationService
				.getFullAccountObject(provisioningOperation);

		Optional<Map.Entry<ProvisioningAttributeDto, Object>> ldapGroups = fullAccountObject.entrySet().stream()
				.filter(fullObject -> fullObject.getKey().getSchemaAttributeName().equals("ldapGroups"))
				.findFirst();

		if (!ldapGroups.isPresent()) {
			return new DefaultEventResult<>(event, this);
		}

		ProvisioningAttributeDto provisioningAttribute = ldapGroups.get().getKey();
		Object idmValue = fullAccountObject.get(provisioningAttribute);

		// run some similar code for merge attribute as in prepere connector processor
		Object resultMerged = resolveMergeValues(provisioningAttribute, idmValue,
				new ArrayList<>(userGroups), provisioningOperation);

		// TODO set new merged value back to provisioning
//		IcConnectorObject updateConnectorObject;
//		if (provisioningContext.getAccountObject() == null) {
//			updateConnectorObject = connectorObject;
//		} else {
//			updateConnectorObject = new IcConnectorObjectImpl(systemEntityUid, objectClass, null);
		// Or this attribute must be send every time (event if was not changed)
//		return attributeMappingService.createIcAttribute(schemaAttribute, idmValue);
//		 I'll get updatedAttribute fronm the line above
//		if (updatedAttribute != null) {
//			updateConnectorObject.getAttributes().add(updatedAttribute);
//		}
//		provisioningOperation.getProvisioningContext().setConnectorObject(updateConnectorObject);

		IcConnectorObject connectorObject = provisioningOperation.getProvisioningContext().getConnectorObject();
		IcAttribute memberAttr = connectorObject.getAttributeByName(MEMBER_ATTRIBUTE);
		if (memberAttr == null) {
			memberAttr = new IcAttributeImpl(MEMBER_ATTRIBUTE, new ArrayList<>());
		}

		// set back to event content
		provisioningOperation = provisioningOperationService.saveOperation(provisioningOperation);
		// log attributes used in provisioning context into provisioning attributes
		provisioningAttributeService.saveAttributes(provisioningOperation);
		//
		event.setContent(provisioningOperation);

		return new DefaultEventResult<>(event, this);
	}

	private Set<String> findUserOnSystem(SysSystemDto system, IcObjectClass objectClass, String dn) {
		Set<String> result = new HashSet<>();
		// Find connector identification persisted in system
		if (system.getConnectorKey() == null) {
			throw new ProvisioningException(AccResultCode.CONNECTOR_KEY_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}
		// load connector configuration
		IcConnectorConfiguration connectorConfig = systemService.getConnectorConfiguration(system);
		if (connectorConfig == null) {
			throw new ProvisioningException(AccResultCode.CONNECTOR_CONFIGURATION_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}
		//
		try {
			//Collections.singletonList("CN=Roman Kuceratest,CN=Users,DC=piskoviste,DC=bcv")
			IcAttribute membersAttribute = new IcAttributeImpl(MEMBER_ATTRIBUTE, dn);
			IcFilter filter = new IcEqualsFilter(membersAttribute);
			connectorFacade.search(system.getConnectorInstance(), connectorConfig, objectClass, filter, new IcResultsHandler() {
				@Override
				public boolean handle(IcConnectorObject connectorObject) {
					IcAttribute groupDn = connectorObject.getAttributeByName("__NAME__");
					if (groupDn != null && !StringUtils.isEmpty(groupDn.getValue())) {
						result.add(String.valueOf(groupDn.getValue()));
					}
					return true;
				}
			});
		} catch (Exception ex) {
			LOG.info("Error during getting user", ex);
		}
		return result;
	}

	@Override
	public int getOrder() {
		// after prepare-connector-object-processor
		return -500;
	}

	/**
	 * Returns merged values for given attribute
	 *
	 * @param provisioningAttribute
	 * @param idmValue
	 * @param connectorValue
	 * @param provisioningOperation
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Object resolveMergeValues(ProvisioningAttributeDto provisioningAttribute, Object idmValue,
									  Object connectorValue, SysProvisioningOperationDto provisioningOperation) {

		List<Object> resultValues = Lists.newArrayList();
		List<Object> connectorValues = Lists.newArrayList();
		List<Object> idmValues = Lists.newArrayList();

		if(connectorValue instanceof List) {
			resultValues.addAll((List<?>) connectorValue);
			connectorValues.addAll((List<?>) connectorValue);
		}else {
			if(connectorValue != null) {
				resultValues.add(connectorValue);
				connectorValues.add(connectorValue);
			}
		}
		if(idmValues instanceof List) {
			idmValues.addAll((List<?>) idmValue);
		}else {
			if(idmValue != null) {
				idmValues.add(idmValue);
			}
		}

		// Load definition of all controlled values in IdM for that attribute
		List<Serializable> controlledValues = attributeMappingService.getCachedControlledAndHistoricAttributeValues(
				provisioningOperation.getSystem(), provisioningOperation.getEntityType(),
				provisioningAttribute.getSchemaAttributeName());
		List<Serializable> controlledValuesFlat = Lists.newArrayList();

		// Controlled value can be Collection, we need to create flat list for all values
		controlledValues.forEach(controlledValue -> {
			if(controlledValue instanceof Collection) {
				controlledValuesFlat.addAll((Collection<? extends Serializable>) controlledValue);
			}else {
				controlledValuesFlat.add(controlledValue);
			}
		});

		// Merge IdM values with connector values
		idmValues.stream().forEach(value -> {
			if (!connectorValues.contains(value)) {
				resultValues.add(value);
			}
		});

		// Delete missing values
		if (controlledValuesFlat != null) {
			// Search all deleted values (managed by IdM)
			List<?> deletedValues = controlledValuesFlat //
					.stream() //
					.filter(controlledValue -> { //
						if (idmValues.contains(controlledValue)) {
							return false;
						}
						return true;
					}).collect(Collectors.toList());
			// Remove all deleted values (managed by IdM)
			resultValues.removeAll(deletedValues);
		}

		return resultValues;
	}
}
