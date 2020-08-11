package eu.bcvsolutions.idm.extras.scheduler.task.impl;


import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVRecord;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaObjectClassFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemFilter;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcAttributeInfo;
import eu.bcvsolutions.idm.ic.api.IcConfigurationProperty;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorInstance;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.api.IcObjectClassInfo;
import eu.bcvsolutions.idm.ic.api.IcUidAttribute;
import eu.bcvsolutions.idm.ic.connid.domain.ConnIdIcConvertUtil;
import eu.bcvsolutions.idm.ic.czechidm.domain.IcConnectorConfigurationCzechIdMImpl;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcConfigurationPropertiesImpl;
import eu.bcvsolutions.idm.ic.impl.IcUidAttributeImpl;
import eu.bcvsolutions.idm.ic.service.impl.DefaultIcConnectorFacade;

/**
 * This LRT imports all items from csv to IDM
 *
 * @author Marek Klement
 */
@Component
@Description("Get all items on mapping - system and import from CSV to IDM")
public class ImportFromCSVToSystemExecutor extends AbstractCsvImportTask {

	//
	static final String PARAM_CSV_FILE_PATH = "Path to file";
	static final String PARAM_SYSTEM_NAME = "Name of system";
	static final String PARAM_ATTRIBUTE_SEPARATOR = "Attribute separator";
	static final String PARAM_NAME_ATTRIBUTE = "Name attribute";
	static final String PARAM_UID_ATTRIBUTE = "Uid attribute";
	static final String PARAM_MULTIVALUED_SEPARATOR = "Separator of multivalued attributes";
	private static final Logger LOG = LoggerFactory.getLogger(ImportFromCSVToSystemExecutor.class);
	//
	private String DEFAULT_NOTIFY_PROPERTY = "requiredConfirmation";
	//
	private String systemName;
	private String nameHeaderAttribute;
	private String uidHeaderAttribute;
	private String multivaluedSeparator;

	@Autowired
	private SysSystemService sysSystemService;
	@Autowired
	private DefaultIcConnectorFacade defaultIcConnectorFacade;
	@Autowired
	private SysSchemaObjectClassService schemaObjectClassService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;

	@Override
	/**
	 * Checks for existing properties and than process system
	 *
	 * @return
	 */
	protected void processRecords(List<CSVRecord> records) {
		LOG.debug("Start process");
		//
		SysSystemFilter systemFilter = new SysSystemFilter();
		systemFilter.setCodeableIdentifier(systemName);
		List<SysSystemDto> systems = sysSystemService.find(systemFilter, null).getContent();
		//
		if (systems.isEmpty()) {
			throw new ResultCodeException(ExtrasResultCode.SYSTEM_NAME_NOT_FOUND,
					ImmutableMap.of("system", systemName));
		}
		SysSystemDto system = systems.get(0);
		List<SysSchemaAttributeDto> attributes = getSchemaAttributes(system);
		
		//
		IcConnectorInstance icConnectorInstance = system.getConnectorInstance();
		if (icConnectorInstance == null) {
			throw new ResultCodeException(ExtrasResultCode.CONNECTOR_INSTANCE_NOT_FOUND,
					ImmutableMap.of("system", systemName));
		}
		//
		IcConnectorConfiguration config = sysSystemService.getConnectorConfiguration(system);
		if (config == null) {
			throw new ResultCodeException(AccResultCode.CONNECTOR_CONFIGURATION_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", systemName));
		}
		if (config instanceof IcConnectorConfigurationCzechIdMImpl) {
			config = getUnnoticedConfiguration((IcConnectorConfigurationCzechIdMImpl) sysSystemService
					.getConnectorConfiguration(system));
		}
		//__ACCOUNT__
		IcObjectClass icObjectClass = ConnIdIcConvertUtil.convertConnIdObjectClass(ObjectClass.ACCOUNT);
		if (icObjectClass == null) {
			throw new ResultCodeException(ExtrasResultCode.CONNECTOR_OBJECT_CLASS_NOT_FOUND,
					ImmutableMap.of("system", systemName));
		}
		
		validateCSV(attributes, records);

		importToSystem(icConnectorInstance, config, icObjectClass, attributes, records);
		
	}

	@Override
	protected void processOneDynamicAttribute(String namePrefix, String name, String valuePrefix, String value,
			boolean isEav) {
		throw new UnsupportedOperationException("No dynamic attributes present");
	}

	/**
	 * Takes configuration of system and makes requiredConfirmation as false
	 *
	 * @param config configuration of system
	 * @return new configuration with requiredConfirmation to false
	 */
	private IcConnectorConfiguration getUnnoticedConfiguration(IcConnectorConfigurationCzechIdMImpl config) {
		IcConnectorConfigurationCzechIdMImpl newConfig = new IcConnectorConfigurationCzechIdMImpl();
		IcConfigurationPropertiesImpl newProperties = new IcConfigurationPropertiesImpl();
		boolean wasThereProperty = false;
		for (IcConfigurationProperty property : config.getConfigurationProperties().getProperties()) {
			if (property.getName().equals(DEFAULT_NOTIFY_PROPERTY)) {
				newProperties.addProperty(DEFAULT_NOTIFY_PROPERTY,
						Boolean.FALSE,
						property.getType(),
						property.getDisplayName(),
						property.getHelpMessage(),
						property.isRequired());
				wasThereProperty = true;
			} else {
				newProperties.addProperty(property.getName(),
						property.getValue(),
						property.getType(),
						property.getDisplayName(),
						property.getHelpMessage(),
						property.isRequired());
			}
		}
		if (!wasThereProperty) {
			newProperties.addProperty(DEFAULT_NOTIFY_PROPERTY,
					Boolean.FALSE,
					null,
					null,
					null,
					false);
		}
		newConfig.setConfigurationProperties(newProperties);
		newConfig.setSystemId(config.getSystemId());
		newConfig.setConnectorPoolConfiguration(config.getConnectorPoolConfiguration());
		newConfig.setConnectorPoolingSupported(config.isConnectorPoolingSupported());
		newConfig.setProducerBufferSize(config.getProducerBufferSize());
		return newConfig;
	}

	/**
	 * Coverts all lines from CSV into the system.
	 *
	 * @param icConnectorInstance instance of connector
	 * @param config              configuration of connector
	 * @param icObjectClass       connecting object class
	 * @return true if everything was OK
	 */
	private void importToSystem(IcConnectorInstance icConnectorInstance, IcConnectorConfiguration config,
			IcObjectClass icObjectClass, List<SysSchemaAttributeDto> attributes, List<CSVRecord> records) {
		
		records.forEach(record -> {
			AccAccountDto account = new AccAccountDto();
			account.setId(UUID.randomUUID());
			
			try {
				List<IcAttribute> list = new LinkedList<>();
				IcUidAttribute uidAttribute = null;
				Map<String, String> line = record.toMap();
				for (Map.Entry<String, String> entry : line.entrySet()) {
					String name = entry.getKey();
					String[] values = entry.getValue().split(multivaluedSeparator);
					//
					if (name.equals(IcAttributeInfo.ENABLE)) {
						IcAttributeImpl attribute = new IcAttributeImpl();
						attribute.setName(name);
						attribute.setValues(Collections.singletonList(Boolean.valueOf(values[0])));
						list.add(attribute);
					} else {
						list.add(createAttribute(name, values, attributes));
					}
					//
					if (name.equals(nameHeaderAttribute)) {
						list.add(createAttribute(Name.NAME, values, attributes));
					}
					//
					if (name.equals(uidHeaderAttribute)) {
						uidAttribute = new IcUidAttributeImpl(name, values[0], null);
						// Only decorator
						account.setUid(values[0]);
					}
				}

				IcConnectorObject object = defaultIcConnectorFacade.readObject(icConnectorInstance, config,
						icObjectClass, uidAttribute);
				//
				if (object == null) {
					defaultIcConnectorFacade.createObject(icConnectorInstance, config, icObjectClass, list);
				} else {
					addUidAttribute(list, uidAttribute, attributes);
					defaultIcConnectorFacade.updateObject(icConnectorInstance, config, icObjectClass, uidAttribute,
							list);
				}
				logItems(account, null);
			} catch (Exception e) {
				logItems(account, e);
			}
			//increaseCounter();
		});
	}

	/**
	 * Log properly items into logItemProcessed
	 *
	 * @param account   created
	 * @param exception thrown
	 */
	private void logItems(AccAccountDto account, Exception exception) {
		if (exception == null) {
			logItemProcessed(account, new OperationResult
					.Builder(OperationState.EXECUTED)
					.setCode(account.getUid())
					.build());
			increaseCounter();
		} else {
			logItemProcessed(account, new OperationResult
					.Builder(OperationState.EXCEPTION)
					.setCause(exception)
					.setCode(account.getUid())
					.build());
		}
	}

	/**
	 * Add uid attribute to list
	 *
	 * @param list         where to add uid attribute
	 * @param uidAttribute attribute itself
	 */
	private void addUidAttribute(List<IcAttribute> list,
								 IcUidAttribute uidAttribute,
								 List<SysSchemaAttributeDto> attributes) {
		String[] temp = new String[1];
		assert uidAttribute != null;
		temp[0] = uidAttribute.getUidValue();
		list.add(createAttribute(Uid.NAME, temp, attributes));
	}

	private List<SysSchemaAttributeDto> getSchemaAttributes(SysSystemDto system) {
		SysSchemaObjectClassFilter objectClassFilter = new SysSchemaObjectClassFilter();
		objectClassFilter.setSystemId(system.getId());
		objectClassFilter.setObjectClassName(IcObjectClassInfo.ACCOUNT);
		List<SysSchemaObjectClassDto> schemas = schemaObjectClassService
				.find(objectClassFilter, null)
				.getContent();
		//
		if (schemas.isEmpty()) {
			throw new ResultCodeException(AccResultCode.CONNECTOR_SCHEMA_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}
		//
		SysSchemaObjectClassDto schema = schemas.get(0);
		SysSchemaAttributeFilter attributeFilter = new SysSchemaAttributeFilter();
		attributeFilter.setObjectClassId(schema.getId());
		List<SysSchemaAttributeDto> attributes = schemaAttributeService
				.find(attributeFilter, null)
				.getContent();
		if (attributes.isEmpty()) {
			throw new ResultCodeException(ExtrasResultCode.SYSTEM_SCHEMA_ATTRIBUTES_NOT_FOUND,
					ImmutableMap.of("schema", schema.getObjectClassName(),
							"system", system.getName()));
		}
		return attributes;
	}

	/**
	 * Validate CSV header and file - header have to be same as schema and cannot have attribute __PASSWORD__
	 *
	 * @param attributes from schema
	 * @return true if valid - no exception popped up
	 */
	private boolean validateCSV(List<SysSchemaAttributeDto> attributes, List<CSVRecord> records) {
		//
		CSVRecord record = records.get(0); // we only need to check one line
		Map<String, String> line = record.toMap();
		boolean usernamePresent = false;
		for (Map.Entry<String, String> entry : line.entrySet()) {
			if (entry.getKey().equals(nameHeaderAttribute)) {
				usernamePresent = true;
				continue;
			}
			// we don't want to import passwords
			if (entry.getKey().equals(IcAttributeInfo.PASSWORD)) {
				continue;
			}
			boolean toReturn = false;
			for (SysSchemaAttributeDto schemaAttribute : attributes) {
				if (entry.getKey().equals(schemaAttribute.getName())) {
					toReturn = true;
					break;
				}
			}
			if (!toReturn) {
				throw new ResultCodeException(AccResultCode.SYSTEM_SCHEMA_ATTRIBUTE_NOT_FOUND,
						ImmutableMap.of("objectClassName", attributes.get(0).getClassType(),
								"attributeName", entry.getKey()));
			}
		}
		
		if (!usernamePresent) {
			throw new ResultCodeException(AccResultCode.SYSTEM_SCHEMA_ATTRIBUTE_NOT_FOUND,
					ImmutableMap.of("objectClassName", attributes.get(0).getClassType(),
							"attributeName", nameHeaderAttribute));
		}
		//
		int size = line.size();

		for (CSVRecord rec : records) {
			Map<String, String> recLine = rec.toMap();
			if (recLine.size() != size) {
				throw new ResultCodeException(ExtrasResultCode.IMPORT_WRONG_LINE_LENGTH,
						ImmutableMap.of("number", rec));
			}
		}
		//
		return true;
	}

	/**
	 * Creates new attribute for system
	 *
	 * @param name   of attribute from header
	 * @param values which we got from CSV
	 * @return new Attribute
	 */
	private IcAttributeImpl createAttribute(String name, String[] values, List<SysSchemaAttributeDto> attributes) {
		List<SysSchemaAttributeDto> filtered;
		if (name.equals(nameHeaderAttribute) || name.equals(Uid.NAME)) {
			filtered = attributes
					.stream()
					.filter(attr -> attr.getName().equals(Name.NAME))
					.collect(Collectors.toList());
		} else {
			filtered = attributes
					.stream()
					.filter(attr -> attr.getName().equals(name))
					.collect(Collectors.toList());
		}
		if (filtered.isEmpty()) {
			throw new ResultCodeException(AccResultCode.SYSTEM_SCHEMA_ATTRIBUTE_NOT_FOUND,
					ImmutableMap.of("objectClassName", attributes.get(0).getClassType(),
							"attributeName", name));
		}
		SysSchemaAttributeDto schemaAttribute = filtered.get(0);
		IcAttributeImpl attribute = new IcAttributeImpl();
		attribute.setName(name);
		if (schemaAttribute.isMultivalued()) {
			attribute.setMultiValue(true);
		}
		if (schemaAttribute.isRequired() && values[0].isEmpty()) {
			throw new IllegalArgumentException("This field is required! - [{0}]");
		}
		attribute.setValues(convertEmptyStrings(values));
		return attribute;
	}

	/**
	 * Converts empty strings to null values - will not add anything in this case
	 *
	 * @param values potencional empty value
	 * @return Adjusted list
	 */
	private List<Object> convertEmptyStrings(String[] values) {
		List<Object> toReturn = new LinkedList<>();
		for (String value : values) {
			if (!value.isEmpty()) {
				toReturn.add(value);
			}
		}
		return toReturn;
	}
	
	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_SYSTEM_NAME, systemName);
		props.put(PARAM_NAME_ATTRIBUTE, nameHeaderAttribute);
		props.put(PARAM_UID_ATTRIBUTE, uidHeaderAttribute);
		props.put(PARAM_MULTIVALUED_SEPARATOR, multivaluedSeparator);

		return props;
	}

	/**
	 * Schema id and path to file are retrieved for following usage.
	 *
	 * @param properties map of properties given
	 */
	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		systemName = getParameterConverter().toString(properties, PARAM_SYSTEM_NAME);
		nameHeaderAttribute = getParameterConverter().toString(properties, PARAM_NAME_ATTRIBUTE);
		uidHeaderAttribute = getParameterConverter().toString(properties, PARAM_UID_ATTRIBUTE);
		multivaluedSeparator = getParameterConverter().toString(properties, PARAM_MULTIVALUED_SEPARATOR);
	}
	
	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();
		
		IdmFormAttributeDto systemNameAttribute = new IdmFormAttributeDto(PARAM_SYSTEM_NAME, PARAM_SYSTEM_NAME,
				PersistentType.SHORTTEXT);
		systemNameAttribute.setRequired(true);
		
		IdmFormAttributeDto nameAttribute = new IdmFormAttributeDto(PARAM_NAME_ATTRIBUTE, PARAM_NAME_ATTRIBUTE,
				PersistentType.SHORTTEXT);
		nameAttribute.setRequired(true);
		
		IdmFormAttributeDto uidAttribute = new IdmFormAttributeDto(PARAM_UID_ATTRIBUTE, PARAM_UID_ATTRIBUTE,
				PersistentType.SHORTTEXT);
		uidAttribute.setRequired(true);
		
		IdmFormAttributeDto multivaluedSeparatorAttribute = new IdmFormAttributeDto(PARAM_MULTIVALUED_SEPARATOR, PARAM_MULTIVALUED_SEPARATOR,
				PersistentType.SHORTTEXT);
		multivaluedSeparatorAttribute.setRequired(true);
		//
		formAttributes.addAll(Lists.newArrayList(systemNameAttribute, nameAttribute,
				uidAttribute, multivaluedSeparatorAttribute));
		
		return formAttributes;
		
	}
}
