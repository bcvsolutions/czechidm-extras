package eu.bcvsolutions.idm.extras.scheduler.task.impl;


import com.google.common.collect.ImmutableMap;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
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
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import eu.bcvsolutions.idm.ic.api.*;
import eu.bcvsolutions.idm.ic.connid.domain.ConnIdIcConvertUtil;
import eu.bcvsolutions.idm.ic.czechidm.domain.IcConnectorConfigurationCzechIdMImpl;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcConfigurationPropertiesImpl;
import eu.bcvsolutions.idm.ic.impl.IcUidAttributeImpl;
import eu.bcvsolutions.idm.ic.service.impl.DefaultIcConnectorFacade;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This LRT imports all items from csv to IDM
 *
 * @author Marek Klement
 */
@Component
@Description("Get all items on mapping - system and import from CSV to IDM")
public class ImportFromCSVToSystemExecutor extends AbstractSchedulableTaskExecutor<Boolean> {

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
	private String pathToFile;
	private Character separator;
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

	/**
	 * Checks for existing properties and than process system
	 *
	 * @return boolean if it was successful
	 */
	@Override
	public Boolean process() {
		LOG.debug("Start process");
		//
		File fl = new File(pathToFile);
		if (!fl.canRead()) {
			throw new ResultCodeException(ExtrasResultCode.IMPORT_CANT_READ_FILE_PATH,
					ImmutableMap.of("path", pathToFile));
		}
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
		validateCSV(attributes);
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

		return importToSystem(icConnectorInstance, config, icObjectClass, attributes);
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
	private boolean importToSystem(IcConnectorInstance icConnectorInstance, IcConnectorConfiguration config,
			IcObjectClass icObjectClass, List<SysSchemaAttributeDto> attributes) {
		//
		CSVParser parser = new CSVParserBuilder().withEscapeChar(CSVParser.DEFAULT_ESCAPE_CHARACTER).withQuoteChar('"')
				.withSeparator(separator).build();
		CSVReader reader = null;
		try {
			reader = new CSVReaderBuilder(new FileReader(pathToFile)).withCSVParser(parser).build();
			String[] header = reader.readNext();
			for (String[] line : reader) {
				AccAccountDto account = new AccAccountDto();
				account.setId(UUID.randomUUID());
				try {
					List<IcAttribute> list = new LinkedList<>();
					IcUidAttribute uidAttribute = null;
					for (int column = 0; column < line.length; ++column) {
						String[] values = line[column].split(multivaluedSeparator);
						String name = header[column];
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
			}
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return true;
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
	private boolean validateCSV(List<SysSchemaAttributeDto> attributes) {
		//
		CSVParser parser = new CSVParserBuilder()
				.withEscapeChar(CSVParser.DEFAULT_ESCAPE_CHARACTER)
				.withQuoteChar('"')
				.withSeparator(separator)
				.build();
		CSVReader reader = null;
		try {
			reader = new CSVReaderBuilder(new FileReader(pathToFile)).withCSVParser(parser).build();
			String[] header = reader.readNext();
			//
			boolean usernamePresent = false;
			// check header
			for (String head : header) {
				if (head.equals(nameHeaderAttribute)) {
					usernamePresent = true;
					continue;
				}
				// we don't want to import passwords
				if (head.equals(IcAttributeInfo.PASSWORD)) {
					continue;
				}
				boolean toReturn = false;
				for (SysSchemaAttributeDto schemaAttribute : attributes) {
					if (head.equals(schemaAttribute.getName())) {
						toReturn = true;
						break;
					}
				}
				if (!toReturn) {
					throw new ResultCodeException(AccResultCode.SYSTEM_SCHEMA_ATTRIBUTE_NOT_FOUND,
							ImmutableMap.of("objectClassName", attributes.get(0).getClassType(),
									"attributeName", head));
				}
			}
			//
			if (!usernamePresent) {
				throw new ResultCodeException(AccResultCode.SYSTEM_SCHEMA_ATTRIBUTE_NOT_FOUND,
						ImmutableMap.of("objectClassName", attributes.get(0).getClassType(),
								"attributeName", nameHeaderAttribute));
			}
			//
			int size = header.length;
			long lineNumber = 1;
			for (String[] line : reader) {
				if (line.length != size) {
					throw new ResultCodeException(ExtrasResultCode.IMPORT_WRONG_LINE_LENGTH,
							ImmutableMap.of("number", lineNumber));
				}
				++lineNumber;
			}
			//
			setCount(lineNumber - 1);
			setCounter(0L);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
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
		if (filtered.size() == 0) {
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
			// todo
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

	/**
	 * We need to add synchronization id - needed as identifier of synchronization which we will export
	 * and path to file where we will export data.
	 *
	 * @return all parameters
	 */
	@Override
	public List<String> getPropertyNames() {
		LOG.debug("Start getPropertyName");
		List<String> params = super.getPropertyNames();
		params.add(PARAM_SYSTEM_NAME);
		params.add(PARAM_CSV_FILE_PATH);
		params.add(PARAM_NAME_ATTRIBUTE);
		params.add(PARAM_UID_ATTRIBUTE);
		params.add(PARAM_ATTRIBUTE_SEPARATOR);
		params.add(PARAM_MULTIVALUED_SEPARATOR);
		return params;
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
		pathToFile = getParameterConverter().toString(properties, PARAM_CSV_FILE_PATH);
		nameHeaderAttribute = getParameterConverter().toString(properties, PARAM_NAME_ATTRIBUTE);
		uidHeaderAttribute = getParameterConverter().toString(properties, PARAM_UID_ATTRIBUTE);
		separator = getParameterConverter().toString(properties, PARAM_ATTRIBUTE_SEPARATOR).charAt(0);
		multivaluedSeparator = getParameterConverter().toString(properties, PARAM_MULTIVALUED_SEPARATOR);
	}
}
