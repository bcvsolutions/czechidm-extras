package eu.bcvsolutions.idm.extras.report.provisioning;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.collect.Lists;
import eu.bcvsolutions.idm.acc.domain.AttributeMapping;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.*;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysAttributeControlledValueFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaObjectClassFilter;
import eu.bcvsolutions.idm.acc.eav.domain.AccFaceType;
import eu.bcvsolutions.idm.acc.entity.AccAccount_;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.*;
import eu.bcvsolutions.idm.core.api.domain.IdentityState;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmScriptDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmScriptService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.api.utils.EntityUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.api.IcObjectClassInfo;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcObjectClassImpl;
import eu.bcvsolutions.idm.rpt.api.dto.RptReportDto;
import eu.bcvsolutions.idm.rpt.api.exception.ReportGenerateException;
import eu.bcvsolutions.idm.rpt.api.executor.AbstractReportExecutor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Component;

import java.beans.IntrospectionException;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Report for compare values in IdM and system
 *
 * @author Ondrej Kopr
 *
 */

@Component
@Enabled(ExtrasModuleDescriptor.MODULE_ID)
@Description("Compare values in IdM with values in system")
public class CompareValueWithSystemReportExecutor extends AbstractReportExecutor {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(CompareValueWithSystemReportExecutor.class);

	public static final String REPORT_NAME = "compare-value-with-system-report";

	public static final String PARAMETER_SYSTEM = "system";
	public static final String PARAMETER_ATTRIBUTES = "attributes";
	public static final String PARAMETER_SYSTEM_MAPPING = "mapping";
	public static final String PARAMETER_ONLY_IDENTITY = "identities";
	public static final String PARAMETER_TREE_NODE = "treeNode";

	private static String REGEX = ",";
	private static String ATT_SCRIPT_SPLIT = ":";

	@Autowired
	private SysSystemService systemService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private AccAccountService accountService;
	@Autowired
	private SysSchemaObjectClassService schemaObjectClassService;
	@Autowired
	private SysSystemAttributeMappingService systemAttributeMappingService;
	@Autowired
	private IdmScriptService scriptService;
	@Autowired
	private ProvisioningService provisioningService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private FormService formService;
	@Autowired
	private ConfidentialStorage confidentialStorage;
	@Autowired
	private SysAttributeControlledValueService attributeControlledValueService;

	/*
	 * Temporary cache for controlled value
	 */
	private Map<UUID, List<SysAttributeControlledValueDto>> controledValueCache = new HashMap<UUID, List<SysAttributeControlledValueDto>>();
	/**
	 * Report ~ executor name
	 */
	@Override
	public String getName() {
		return REPORT_NAME;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		IdmFormAttributeDto system = new IdmFormAttributeDto(PARAMETER_SYSTEM, "System", PersistentType.UUID);
		system.setFaceType(AccFaceType.SYSTEM_SELECT);
		system.setRequired(true);
		//
		IdmFormAttributeDto attributes = new IdmFormAttributeDto(PARAMETER_ATTRIBUTES, "Attributes",
				PersistentType.SHORTTEXT);
		attributes.setRequired(true);
		//
		IdmFormAttributeDto mapping = new IdmFormAttributeDto(PARAMETER_SYSTEM_MAPPING, "Mapping",
				PersistentType.SHORTTEXT);
		mapping.setRequired(true);
		//
		IdmFormAttributeDto identities = new IdmFormAttributeDto(PARAMETER_ONLY_IDENTITY, "Identities",
				PersistentType.UUID);
		identities.setFaceType(BaseFaceType.IDENTITY_SELECT);
		identities.setMultiple(true);
		identities.setDescription("For testing purpose. This option process only selected identities. But will be iterate over all accounts.");
		//
		IdmFormAttributeDto treeNode = new IdmFormAttributeDto(PARAMETER_TREE_NODE, "Treenode",
				PersistentType.SHORTTEXT);
		treeNode.setDescription("Organization ID. Filtering users only on this organization unit and recursively down.");
		//
		return Lists.newArrayList(system, attributes, mapping, treeNode, identities);
	}

	@Override
	protected IdmAttachmentDto generateData(RptReportDto report) {
		// Prepare stage we need all information
		// TODO: return null is not nice, please throw exception
		SysSystemDto systemDto = getSystem(report);
		if (systemDto == null) {
			LOG.error("System not found.");
			return null;
		}
		SysSystemMappingDto systemMapping = getSystemMapping(report);
		if (systemMapping == null) {
			LOG.error("System mapping  not found.");
			return null;
		}
		List<SysSystemAttributeMappingDto> attributes = getAttributes(report, systemDto, systemMapping);
		if (attributes.isEmpty()) {
			LOG.error("No given attributes.");
			return null;
		}

		// list identities is only for testing purpose
		List<UUID> identities = getIdentities(report);
		
		File temp = null;
		FileOutputStream outputStream = null;

		try {
			temp = getAttachmentManager().createTempFile();
			outputStream = new FileOutputStream(temp);
			// create json generator from dtos
			JsonGenerator jGenerator = getMapper().getFactory().createGenerator(outputStream, JsonEncoding.UTF8);

			try {
				// start array, probably isn't needed
				jGenerator.writeStartArray();

				SysSchemaObjectClassFilter schemaObjectClassFilter = new SysSchemaObjectClassFilter();
				schemaObjectClassFilter.setSystemId(systemDto.getId());
				schemaObjectClassFilter.setObjectClassName(IcObjectClassInfo.ACCOUNT);
				List<SysSchemaObjectClassDto> schemas = schemaObjectClassService.find(schemaObjectClassFilter, null)
						.getContent();
				// TODO: we counted with only one schema for account
				SysSchemaObjectClassDto schemaObjectClassDto = schemas.get(0);

				CompareValueDataInfoDto infoData = new CompareValueDataInfoDto();
				infoData.setSystem(systemDto);
				infoData.setAttributes(attributes);
				infoData.setSystemMapping(systemMapping);

				SysSchemaAttributeFilter filter = new SysSchemaAttributeFilter();
				filter.setSystemId(systemDto.getId());
				List<SysSchemaAttributeDto> schemaAttributes = schemaAttributeService.find(filter, null).getContent();
				
				counter = 0l;
				
				AccAccountFilter filterAccount = new AccAccountFilter();
				filterAccount.setSystemId(systemDto.getId());
				filterAccount.setEntityType(SystemEntityType.IDENTITY);

				// set of identites can be also set as tree node
				UUID treeNode = getTreeNode(report);

				// if identities is empty use filter without defined identities
				if (identities.isEmpty() && treeNode == null) {
					LOG.info("Generate report for all identities.");
					infoData = generateRows(infoData, filterAccount, schemaObjectClassDto, systemDto, attributes, schemaAttributes, report);
				} else {
					LOG.info("Generate report for given identities, or tree node.");

					if (treeNode != null) {
						IdmIdentityFilter identityFilter = new IdmIdentityFilter();
						identityFilter.setTreeNode(treeNode);
						identityFilter.setRecursively(true);
						identities.addAll(identityService.findIds(identityFilter, null).getContent());
					}

					long finalCount = 0;
					// iterate over all given identities ids
					for (UUID identityId : identities) {
						filterAccount.setIdentityId(identityId);
						infoData = generateRows(infoData, filterAccount, schemaObjectClassDto, systemDto, attributes, schemaAttributes, report);
						finalCount++;
					}
					count = finalCount;
				}

				getMapper().writeValue(jGenerator, infoData);
			} finally {
				jGenerator.close();
			}

			return createAttachment(report, new FileInputStream(temp));
		} catch (IOException e) {
			throw new ReportGenerateException(report.getName(), e);
		} finally {
			IOUtils.closeQuietly(outputStream);
			FileUtils.deleteQuietly(temp);
		}
	}

	/**
	 * Generate rows for given filter and return same instance infoData
	 *
	 * @param infoData
	 * @param filterAccount
	 * @param schemaObjectClassDto
	 * @param systemDto
	 * @param attributes
	 * @param schemaAttributes
	 * @param report
	 * @return
	 */
	private CompareValueDataInfoDto generateRows(CompareValueDataInfoDto infoData, AccAccountFilter filterAccount, SysSchemaObjectClassDto schemaObjectClassDto, SysSystemDto systemDto, List<SysSystemAttributeMappingDto> attributes, List<SysSchemaAttributeDto> schemaAttributes, RptReportDto report) {
		boolean canContinue = true;
		Pageable pageable = PageRequest.of(0, 100, Sort.by(Direction.ASC, AccAccount_.created.getName()));
		List<CompareValueRowDto> rows = infoData.getRows();
		do {
			Page<AccAccountDto> accounts = accountService.find(filterAccount, pageable, IdmBasePermission.READ);
			if (count == null) {
				count = accounts.getTotalElements();
			}

			// iterate over accounts
			for (AccAccountDto account : accounts) {
				if (account.isInProtection()) {
					LOG.info("Account UID: [{}] is in protection. Skip this account.", account.getUid());
					continue;
				}
				LOG.info("Process account UID [{}]. Account id [{}]", account.getUid(), account.getId());
				CompareValueRowDto row = null;
				try {
					// generate row for given account
					row = generateRowForAccount(account, schemaObjectClassDto, systemDto, attributes, schemaAttributes, report);
				} catch (Exception e) {
					// exception during execute this account
					row = new CompareValueRowDto();
					row.setKey(createKey(account, null));
					row.setFailed(true);
					row.setFailedMessage(e.getMessage());
				}
				if (row == null) {
					LOG.info("Row for account id [{}] was not generated.", account.getId());
					continue;
				}
				rows.add(row);
				++counter;
			}
			
			canContinue = updateState();
			if (!canContinue) {
				break;
			}

		pageable = accounts.hasNext() && canContinue ? accounts.nextPageable() : null;
		} while (canContinue && pageable != null);

		infoData.setRows(rows);
		return infoData;
	}

	/**
	 * Generate one row for given account
	 *
	 * @param account
	 * @param schemaObjectClassDto
	 * @param systemDto
	 * @param attributes
	 * @param schemaAttributes
	 * @param report
	 * @return
	 */
	private CompareValueRowDto generateRowForAccount(AccAccountDto account, SysSchemaObjectClassDto schemaObjectClassDto, SysSystemDto systemDto, List<SysSystemAttributeMappingDto> attributes, List<SysSchemaAttributeDto> schemaAttributes, RptReportDto report) {
		UUID targetEntityId = account.getTargetEntityId();
		if (targetEntityId == null) {
			LOG.info("Target entity for account id: [{}] is null", account.getId());
			return null;
		}

		IdmIdentityDto identity = identityService.get(targetEntityId);
		
		if (identity == null) {
			LOG.info("Identity id [{}] not found", targetEntityId);
			return null;
		}

		
		IcObjectClass objectClass = new IcObjectClassImpl(
				schemaObjectClassDto.getObjectClassName());
		SysSystemEntityDto systemEntityDto = DtoUtils.getEmbedded(account, AccAccount_.systemEntity,
				SysSystemEntityDto.class);

		String uid = systemEntityDto.getUid();

		List<CompareValueCellDto> cells = new ArrayList<>();
		String rowKey = createKey(account, identity);
		CompareValueRowDto row = new CompareValueRowDto();
		row.setKey(rowKey);

		// Identity is left set diferent state
		if (identity.getState() != null && identity.getState() == IdentityState.LEFT) {
			row.setIdentityLeft(true);
		}

		// set if is account in protection
		row.setAccountInProtection(account.isInProtection());

		List<AttributeMapping> resolveMappedAttributes = provisioningService.resolveMappedAttributes(account, identity, systemDto, SystemEntityType.IDENTITY);

		// read
		IcConnectorObject connectorObject = systemService.readConnectorObject(systemDto.getId(),
				uid, objectClass);
		
		if (connectorObject == null) {
			// entity doesn't exists in system
			row.setExistEntityOnSystem(false);
		} else {
			// first remove and sort attributes by given order in report
			List<IcAttribute> unsortedAttributes = connectorObject.getAttributes();

			List<IcAttribute> sortedAttributes = new ArrayList<>();
			for (SysSystemAttributeMappingDto att : attributes) {
				SysSchemaAttributeDto schemaAttributeDto = getSchemaAttribute(schemaAttributes, att);
				IcAttribute attribute = unsortedAttributes.stream().filter(unsortedAtt -> {
					return unsortedAtt.getName().equals(schemaAttributeDto.getName());
				}).findFirst().orElse(null);
				if (attribute != null) {
					sortedAttributes.add(attribute);
				} else {
					// Attribute doesn't exists on system
					IcAttributeImpl nonExisting = new IcAttributeImpl(schemaAttributeDto.getName(), null);
					nonExisting.setMultiValue(schemaAttributeDto.isMultivalued());
					sortedAttributes.add(nonExisting);
				}
			}

			// iterate over sorted attributes
			for (IcAttribute icAttribute : sortedAttributes) {

				AttributeMapping attributeMapping = resolveMappedAttributes.stream().filter(mappedAtt -> {
					SysSchemaAttributeDto schemaAttributeDto = getSchemaAttribute(schemaAttributes, mappedAtt);
					return schemaAttributeDto.getName().equals(icAttribute.getName());
				}).findFirst().orElse(null);
	
				SysSchemaAttributeDto schemaAttributeDto = getSchemaAttribute(schemaAttributes, attributeMapping);
				
				if (attributeMapping == null) {
					continue;
				}
				
				Object transformValueToResource = null;

				// if attribute is multivalued is needed iterate over all attributes in roles
				if (attributeMapping.getStrategyType() == AttributeMappingStrategyType.AUTHORITATIVE_MERGE ||
						attributeMapping.getStrategyType() == AttributeMappingStrategyType.MERGE) {
					Set<Object> mergedValues = new LinkedHashSet<>();

					resolveMappedAttributes.stream().filter(attribute -> {
						return attributeMapping.getSchemaAttribute().equals(attribute.getSchemaAttribute());
					}).forEach(attribute -> {
						Object attributeValue = systemAttributeMappingService.getAttributeValue(uid,
								identity, attribute);
						if (attributeValue != null) {
							// If is value collection, then we add all its items to
							// main list!
							if (attributeValue instanceof Collection) {
								Collection<?> collectionNotNull = ((Collection<?>) attributeValue).stream().filter(item -> {
									return item != null;
								}).collect(Collectors.toList());
								mergedValues.addAll(collectionNotNull);
							} else {
								mergedValues.add(attributeValue);
							}
						}
					});
					transformValueToResource = new ArrayList<>(mergedValues);
				} else {
					// attribute is single value
					Object attributeValue = getAttributeValue(uid, identity, attributeMapping, schemaAttributeDto);
					
					// check script and transform
					IdmScriptDto overrideScript = getOverrideScript(report, attributeMapping);
					if (overrideScript != null) {
						transformValueToResource = systemAttributeMappingService
								.transformValueToResource(uid, attributeValue, overrideScript.getScript(), identity, systemDto);
					} else {
						transformValueToResource = systemAttributeMappingService
								.transformValueToResource(uid, attributeValue, attributeMapping, identity);
					}
					
				}

				// create cell and set additional information
				CompareValueCellDto newCell = new CompareValueCellDto();
				newCell.setAttributeName(icAttribute.getName());
				newCell.setIdmValue(transformValueToResource);
				newCell.setMultivalued(schemaAttributeDto.isMultivalued());
				if (schemaAttributeDto.isMultivalued()) {
					UUID systemAttributeMappingId = null;
					if (attributeMapping instanceof SysRoleSystemAttributeDto) {
						systemAttributeMappingId = ((SysRoleSystemAttributeDto)attributeMapping).getSystemAttributeMapping();
					} else if (attributeMapping instanceof SysSystemAttributeMappingDto) {
						systemAttributeMappingId = ((SysSystemAttributeMappingDto)attributeMapping).getId();
					}
					List<SysAttributeControlledValueDto> controlledValues = controledValueCache.get(systemAttributeMappingId);
					if (systemAttributeMappingId == null) {
						LOG.error("System attribute mapping ID for attribute id [{}] didn't found. We will use classic behavior.", attributeMapping.getId());
						newCell.setSystemValue(icAttribute.getValues());
					} else {
						if (controlledValues == null) {
							// Get controlled values
							SysAttributeControlledValueFilter filter = new SysAttributeControlledValueFilter();
							
							filter.setAttributeMappingId(systemAttributeMappingId); // This must exists
							filter.setHistoricValue(Boolean.FALSE); // Actually controlled
							// We need them all
							controlledValues = attributeControlledValueService.find(filter, null).getContent();
							controledValueCache.put(systemAttributeMappingId, controlledValues);
						}
						newCell.setSystemValue(removeNotControlledValues(icAttribute.getValues(), controlledValues));
					}
				} else {
					newCell.setSystemValue(icAttribute.getValue());
				}
				cells.add(newCell);
				
			}
			
		}

		// set generated cells and return new row
		row.setCells(cells);
		return row;
	}

	/**
	 * Process given value in values attribute with controlled values given in attribute controlledValues.
	 * Return new created list of controlled values.
	 *
	 * @param values
	 * @param controlledValues
	 * @return
	 */
	private Collection<?> removeNotControlledValues(List<Object> values, List<SysAttributeControlledValueDto> controlledValues) {
		List<Object> valuesAfterCheck = new ArrayList<Object>();
		
		values.forEach(val -> {
			SysAttributeControlledValueDto existingValue = controlledValues.stream().filter(controlledValue -> {
				return ObjectUtils.equals(String.valueOf(controlledValue.getValue()), String.valueOf(val));
			}).findAny().orElse(null);
			if (existingValue != null) {
				valuesAfterCheck.add(val);
			}
		});

		return valuesAfterCheck;
	}

	/**
	 * Get schema attribute by attribute mapping
	 *
	 * @param schemaAttributes
	 * @param attributeMapping
	 * @return
	 */
	private SysSchemaAttributeDto getSchemaAttribute(List<SysSchemaAttributeDto> schemaAttributes, AttributeMapping attributeMapping) {
		return schemaAttributes.stream().filter(schemaAtt -> {
			return schemaAtt.getId().equals(attributeMapping.getSchemaAttribute());
		}).findFirst().orElse(null);
	}

	/**
	 * Create key for given account and identity
	 *
	 * @param account
	 * @param identity
	 * @return
	 */
	private String createKey(AccAccountDto account, IdmIdentityDto identity) {
		StringBuilder result = new StringBuilder();
		if (identity != null) {
			result.append(identity.getUsername());
		}
		result.append(" (");
		result.append(account.getUid());
		result.append(')');
		return result.toString();
	}

	/**
	 * Get system from report configuration
	 *
	 * @param report
	 * @return
	 */
	private SysSystemDto getSystem(RptReportDto report) {
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto(report, getFormDefinition(), report.getFilter());
		Serializable systemId = formInstance.toSinglePersistentValue(PARAMETER_SYSTEM);
		if (systemId == null) {
			return null;
		}

		return systemService.get(UUID.fromString(systemId.toString()));
	}

	/**
	 * Get system mapping from report configuration
	 *
	 * @param report
	 * @return
	 */
	private SysSystemMappingDto getSystemMapping(RptReportDto report) {
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto(report, getFormDefinition(), report.getFilter());
		Serializable systemMappingId = formInstance.toSinglePersistentValue(PARAMETER_SYSTEM_MAPPING);
		if (systemMappingId == null) {
			return null;
		}
		return systemMappingService.get(UUID.fromString(systemMappingId.toString()));
	}

	/**
	 * Get defined attributes for report
	 *
	 * @param report
	 * @param system
	 * @param systemAttributeMappingDto
	 * @return
	 */
	private List<SysSystemAttributeMappingDto> getAttributes(RptReportDto report, SysSystemDto system,
			SysSystemMappingDto systemAttributeMappingDto) {
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto(report, getFormDefinition(), report.getFilter());
		Serializable attributesAsSerializable = formInstance.toSinglePersistentValue(PARAMETER_ATTRIBUTES);
		if (attributesAsSerializable == null) {
			return Lists.newArrayList();
		}

		List<SysSystemAttributeMappingDto> result = new ArrayList<>();
		for (String attribute : attributesAsSerializable.toString().split(REGEX)) {
			attribute = attribute.trim();

			if (attribute.contains(ATT_SCRIPT_SPLIT)) {
				String[] splited = attribute.split(ATT_SCRIPT_SPLIT);
				attribute = splited[0];
			}

			SysSystemAttributeMappingDto attributeMappingDto = systemAttributeMappingService.get(attribute);
			if (attributeMappingDto != null) {
				result.add(attributeMappingDto);
			}
		}

		return result;
	}

	/**
	 * Get slected identites only
	 *
	 * @param report
	 * @return
	 */
	private List<UUID> getIdentities(RptReportDto report) {
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto(report, getFormDefinition(), report.getFilter());
		List<Serializable> identitiesAsSerializable = formInstance.toPersistentValues(PARAMETER_ONLY_IDENTITY);
		if (identitiesAsSerializable == null) {
			return Lists.newArrayList();
		}

		List<UUID> result = new ArrayList<>((int) (identitiesAsSerializable.size() / 0.75));
		for (Serializable identityIdAsSerializable : identitiesAsSerializable) {
			if (identityIdAsSerializable == null) {
				continue;
			}
			String identityId = identityIdAsSerializable.toString();
			identityId = identityId.trim();

			result.add(UUID.fromString(identityId));
		}

		return result;
	}

	private UUID getTreeNode(RptReportDto report) {
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto(report, getFormDefinition(), report.getFilter());
		Serializable treeNodeIdAsSerializable = formInstance.toSinglePersistentValue(PARAMETER_TREE_NODE);
		if (treeNodeIdAsSerializable == null) {
			return null;
		}

		return UUID.fromString(treeNodeIdAsSerializable.toString());
	}

	/**
	 * Get overriden script for given attribute
	 *
	 * @param report
	 * @param attributeMapping
	 * @return
	 */
	private IdmScriptDto getOverrideScript(RptReportDto report, AttributeMapping attributeMapping) {
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto(report, getFormDefinition(), report.getFilter());
		Serializable attributesAsSerializable = formInstance.toSinglePersistentValue(PARAMETER_ATTRIBUTES);
		if (attributesAsSerializable == null) {
			return null;
		}
		for (String attribute : attributesAsSerializable.toString().split(REGEX)) {
			attribute = attribute.trim();
			
			if (attribute == null || !attribute.contains(attributeMapping.getId().toString())) {
				continue;
			}
			
			String scriptCode = null;
			if (attribute.contains(ATT_SCRIPT_SPLIT)) {
				String[] splited = attribute.split(ATT_SCRIPT_SPLIT);
				attribute = splited[0];
				scriptCode = splited[1];
			}
			
			if (scriptCode != null) {
				return scriptService.getByCode(scriptCode);
			}

			return null;
		}
		return null;
	}

	/**
	 * Get attribute from identity. Different between eavs and dto attrs.
	 *
	 * @param uid
	 * @param entity
	 * @param attributeHandling
	 * @param schemaAttributeDto
	 * @return
	 */
	private Object getAttributeValue(String uid, IdmIdentityDto entity, AttributeMapping attributeHandling, SysSchemaAttributeDto schemaAttributeDto) {
		Object idmValue = null;

		if (attributeHandling.isExtendedAttribute() && entity != null && formService.isFormable(entity.getClass())) {
			List<IdmFormValueDto> formValues = formService.getValues(entity, attributeHandling.getIdmPropertyName());
			if (formValues.isEmpty()) {
				idmValue = null;
			} else if(schemaAttributeDto.isMultivalued()){
				// Multiple value extended attribute
				List<Object> values = new ArrayList<>();
				formValues.stream().forEachOrdered(formValue -> {
					values.add(formValue.getValue());
				});
				idmValue = values;
			} else {
				// Single value extended attribute
				IdmFormValueDto formValue = formValues.get(0);
				if (formValue.isConfidential()) {
					Object confidentialValue = formService.getConfidentialPersistentValue(formValue);
					// If is confidential value String and schema attribute is GuardedString type, then convert to GuardedString will be did.
					if(confidentialValue instanceof String && schemaAttributeDto.getClassType().equals(GuardedString.class.getName())){
						idmValue = new GuardedString((String) confidentialValue);
					}else {
						idmValue = confidentialValue;
					}
				} else {
					idmValue = formValue.getValue();
				}
			}
		}
		// Find value from entity
		else if (attributeHandling.isEntityAttribute()) {
			if (attributeHandling.isConfidentialAttribute()) {
				// If is attribute isConfidential, then we will find value in
				// secured storage
				idmValue = confidentialStorage.getGuardedString(entity.getId(), entity.getClass(), attributeHandling.getIdmPropertyName());
			} else {
				try {
					// We will search value directly in entity by property name
					idmValue = EntityUtils.getEntityValue(entity, attributeHandling.getIdmPropertyName());
				} catch (IntrospectionException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | ProvisioningException o_O) {
					// this can't be true :D, please ignore
				}
			}
		} else {
			// value comes from script, probably
		}
		return idmValue;
	}
}