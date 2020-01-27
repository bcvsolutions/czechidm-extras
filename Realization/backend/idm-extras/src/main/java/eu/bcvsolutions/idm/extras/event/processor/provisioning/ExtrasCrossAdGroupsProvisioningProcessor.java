package eu.bcvsolutions.idm.extras.event.processor.provisioning;

import java.io.Serializable;
import java.text.MessageFormat;
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

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.ProvisioningAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.SysSchemaAttribute;
import eu.bcvsolutions.idm.acc.event.processor.provisioning.PrepareConnectorObjectProcessor;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningOperationService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.event.AbstractEntityEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.api.IcUidAttribute;
import eu.bcvsolutions.idm.ic.filter.api.IcFilter;
import eu.bcvsolutions.idm.ic.filter.impl.IcEqualsFilter;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcConnectorObjectImpl;
import eu.bcvsolutions.idm.ic.impl.IcObjectClassImpl;
import eu.bcvsolutions.idm.ic.impl.IcUidAttributeImpl;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * In case of AD system we will search all available AD systems to get all user roles from different servers
 *
 * @author Roman Kucera
 */
@Component
@Description("In case of AD system we will search all available AD systems to get all user roles from different servers.")
public class ExtrasCrossAdGroupsProvisioningProcessor extends AbstractEntityEventProcessor<SysProvisioningOperationDto> {

	public static final String PROCESSOR_NAME = "extras-cross-ad-groups-provisioning-processor";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(PrepareConnectorObjectProcessor.class);

	private final SysSystemMappingService systemMappingService;
	private final SysSystemAttributeMappingService attributeMappingService;
	private final IcConnectorFacade connectorFacade;
	private final SysSystemService systemService;
	private final SysProvisioningOperationService provisioningOperationService;
	private final SysSchemaAttributeService schemaAttributeService;
	private final SysSchemaObjectClassService schemaObjectClassService;

	@Autowired
	private ProvisioningService provisioningService;
	@Autowired
	private SysProvisioningAttributeService provisioningAttributeService;
	@Autowired
	private ExtrasConfiguration extrasConfiguration;

	@Autowired
	public ExtrasCrossAdGroupsProvisioningProcessor(
			IcConnectorFacade connectorFacade,
			SysSystemService systemService,
			SysProvisioningOperationService provisioningOperationService,
			SysSystemMappingService systemMappingService,
			SysSystemAttributeMappingService attributeMappingService,
			SysSchemaAttributeService schemaAttributeService,
			SysSchemaObjectClassService schemaObjectClassService) {
		super(ProvisioningEventType.CREATE, ProvisioningEventType.UPDATE);
		//
		Assert.notNull(systemMappingService);
		Assert.notNull(attributeMappingService);
		Assert.notNull(connectorFacade);
		Assert.notNull(systemService);
		Assert.notNull(provisioningOperationService);
		Assert.notNull(schemaAttributeService);
		Assert.notNull(schemaObjectClassService);
		//
		this.systemMappingService = systemMappingService;
		this.attributeMappingService = attributeMappingService;
		this.connectorFacade = connectorFacade;
		this.systemService = systemService;
		this.provisioningOperationService = provisioningOperationService;
		this.schemaAttributeService = schemaAttributeService;
		this.schemaObjectClassService = schemaObjectClassService;
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

		String userDn = null;

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
		IcConnectorObject existsConnectorObject;
		try {
			IcUidAttribute uidAttribute = new IcUidAttributeImpl(null, uid, null);
			existsConnectorObject = connectorFacade.readObject(system.getConnectorInstance(), connectorConfig,
					objectClass, uidAttribute);
			if (existsConnectorObject != null) {
				userDn = String.valueOf(existsConnectorObject.getAttributeByName("__NAME__").getValue());
			}
		} catch (Exception ex) {
			LOG.info("Error during getting user: ", ex);
			provisioningOperation = provisioningOperationService.handleFailed(provisioningOperation, ex);
			// set back to event content
			event.setContent(provisioningOperation);
			return new DefaultEventResult<>(event, this, true);
		}

		// load grups
		IcObjectClass groupClass = new IcObjectClassImpl("__GROUP__");

		Set<String> userGroups = new HashSet<>();

		// find all user's groups
		if (userDn != null) {
			String finalUserDn = userDn;
			try {
				adSystems.forEach(adSystem -> {
					SysSystemDto adSystemDto = systemService.get(adSystem);
					userGroups.addAll(findUserGroupsOnSystem(adSystemDto, groupClass, finalUserDn));
				});
			} catch (Exception e) {
				LOG.info("Error during getting user's groups: ", e);
				provisioningOperation = provisioningOperationService.handleFailed(provisioningOperation, e);
				// set back to event content
				event.setContent(provisioningOperation);
				return new DefaultEventResult<>(event, this, true);
			}
		}

		// check if user has more roles then in IdM = that can happen if he has roles from other servers then the normal search will not return all of them
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

		// run same code for merge attribute as in prepare connector processor
		Object resultMerged = resolveMergeValues(provisioningAttribute, idmValue,
				new ArrayList<>(userGroups), provisioningOperation);

		IcConnectorObject updateConnectorObject;

		SysSystemMappingDto mapping = getMapping(system, provisioningOperation.getEntityType());
		SysSchemaObjectClassDto schemaObjectClassDto = schemaObjectClassService.get(mapping.getObjectClass());
		List<SysSchemaAttributeDto> schemaAttributes = findSchemaAttributes(system, schemaObjectClassDto);
		Optional<SysSchemaAttributeDto> schemaAttributeOptional = schemaAttributes //
				.stream() //
				.filter(schemaAttribute -> { //
					return provisioningAttribute.getSchemaAttributeName().equals(schemaAttribute.getName());
				}) //
				.findFirst();

		if (!schemaAttributeOptional.isPresent()) {
			throw new ProvisioningException(AccResultCode.PROVISIONING_SCHEMA_ATTRIBUTE_IS_FOUND,
					ImmutableMap.of("attribute", provisioningAttribute.getSchemaAttributeName()));
		}

		SysSchemaAttributeDto schemaAttribute = schemaAttributeOptional.get();

		// if roles are not same
		if (!provisioningService.isAttributeValueEquals(resultMerged, new ArrayList<>(userGroups), schemaAttribute)) {
			List<IcAttribute> attributesWithoutGroups = getIcAttributes(provisioningOperation, userGroups, idmValue, resultMerged, schemaAttribute);
			updateConnectorObject = new IcConnectorObjectImpl(uid, objectClass, attributesWithoutGroups);
		} else {
			// remove ldapGroups from provisioning operation
			IcConnectorObject connectorObject = provisioningOperation.getProvisioningContext().getConnectorObject();
			List<IcAttribute> attributes = connectorObject.getAttributes();
			List<IcAttribute> attributesWithoutGroups = attributes.stream()
					.filter(icAttribute -> !icAttribute.getName().equals("ldapGroups"))
					.collect(Collectors.toList());
			updateConnectorObject = new IcConnectorObjectImpl(uid, objectClass, attributesWithoutGroups);
		}
		provisioningOperation.getProvisioningContext().setConnectorObject(updateConnectorObject);
		// set back to event content
		provisioningOperation = provisioningOperationService.saveOperation(provisioningOperation);
		// log attributes used in provisioning context into provisioning attributes
		provisioningAttributeService.saveAttributes(provisioningOperation);
		//
		event.setContent(provisioningOperation);
		return new DefaultEventResult<>(event, this);
	}

	private List<IcAttribute> getIcAttributes(SysProvisioningOperationDto provisioningOperation, Set<String> userGroups, Object idmValue, Object resultMerged, SysSchemaAttributeDto schemaAttribute) {
		// set correct value to ldapGroups attribute
		IcAttribute updatedAttribute = attributeMappingService.createIcAttribute(schemaAttribute, resultMerged);

		IcConnectorObject connectorObject = provisioningOperation.getProvisioningContext().getConnectorObject();
		List<IcAttribute> attributes = connectorObject.getAttributes();
		List<IcAttribute> attributesWithoutGroups = attributes.stream()
				.filter(icAttribute -> !icAttribute.getName().equals("ldapGroups"))
				.collect(Collectors.toList());
		attributesWithoutGroups.add(updatedAttribute);

		// prepare two list, roles to add and roles to remove so I dont need to search it again in connector
		List<Object> idmValues = new ArrayList<>();
		if (resultMerged instanceof List) {
			idmValues.addAll((List<?>) resultMerged);
		} else {
			if (resultMerged != null) {
				idmValues.add(String.valueOf(resultMerged));
			}
		}
		List<Object> systemValues = new ArrayList<>(userGroups);

		List<Object> toAdd = new ArrayList<>(idmValues);
		List<Object> toRemove = new ArrayList<>(systemValues);

		toAdd.removeAll(systemValues);
		toRemove.removeAll(idmValues);

		IcAttribute rolesToAdd = new IcAttributeImpl("ldapGroupsToAdd", toAdd, true);
		IcAttribute rolesToRemove = new IcAttributeImpl("ldapGroupsToRemove", toRemove, true);

		attributesWithoutGroups.add(rolesToAdd);
		attributesWithoutGroups.add(rolesToRemove);
		return attributesWithoutGroups;
	}

	private Set<String> findUserGroupsOnSystem(SysSystemDto system, IcObjectClass objectClass, String dn) {
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
		IcAttribute membersAttribute = new IcAttributeImpl("member", dn);
		IcFilter filter = new IcEqualsFilter(membersAttribute);
		connectorFacade.search(system.getConnectorInstance(), connectorConfig, objectClass, filter, connectorObject -> {
			IcAttribute groupDn = connectorObject.getAttributeByName("__NAME__");
			if (groupDn != null && !StringUtils.isEmpty(groupDn.getValue())) {
				result.add(String.valueOf(groupDn.getValue()));
			}
			return true;
		});
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

		if (connectorValue instanceof List) {
			resultValues.addAll((List<?>) connectorValue);
			connectorValues.addAll((List<?>) connectorValue);
		} else {
			if (connectorValue != null) {
				resultValues.add(connectorValue);
				connectorValues.add(connectorValue);
			}
		}
		if (idmValues instanceof List) {
			idmValues.addAll((List<?>) idmValue);
		} else {
			if (idmValue != null) {
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
			if (controlledValue instanceof Collection) {
				controlledValuesFlat.addAll((Collection<? extends Serializable>) controlledValue);
			} else {
				controlledValuesFlat.add(controlledValue);
			}
		});

		// Merge IdM values with connector values
		idmValues.forEach(value -> {
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
						return !idmValues.contains(controlledValue);
					}).collect(Collectors.toList());
			// Remove all deleted values (managed by IdM)
			resultValues.removeAll(deletedValues);
		}

		return resultValues;
	}

	private SysSystemMappingDto getMapping(SysSystemDto system, SystemEntityType entityType) {
		List<SysSystemMappingDto> systemMappings = systemMappingService.findBySystem(system,
				SystemOperationType.PROVISIONING, entityType);
		if (systemMappings == null || systemMappings.isEmpty()) {
			throw new IllegalStateException(MessageFormat.format(
					"System [{0}] does not have mapping, provisioning will not be executed. Add some mapping for entity type [{1}]",
					system.getName(), entityType));
		}
		if (systemMappings.size() != 1) {
			throw new IllegalStateException(MessageFormat.format(
					"System [{0}] is wrong configured! Remove duplicit mapping for entity type [{1}]", system.getName(),
					entityType));
		}
		return systemMappings.get(0);
	}

	/**
	 * Find list of {@link SysSchemaAttribute} by system and objectClass
	 *
	 * @param objectClass
	 * @param system
	 * @return
	 */
	private List<SysSchemaAttributeDto> findSchemaAttributes(SysSystemDto system, SysSchemaObjectClassDto objectClass) {

		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		schemaAttributeFilter.setObjectClassId(objectClass.getId());
		return schemaAttributeService.find(schemaAttributeFilter, null).getContent();
	}

	@Override
	public boolean isDefaultDisabled() {
		return true;
	}
}
