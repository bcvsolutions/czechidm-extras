package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.eav.domain.AccFaceType;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseCodeList;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.event.RoleEvent;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import eu.bcvsolutions.idm.ic.api.IcConfigurationProperty;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;

/**
 * @author Roman Kucera
 */
@Service
@DisallowConcurrentExecution
@Description("Duplicate cross domain roles")
public class CrossAdRolesDuplication extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CrossAdRolesDuplication.class);

	public static String SYSTEMS_PARAM = "systems";
	public static String ROLE_CATALOGUE_PARAM = "role_catalogue";
	public static String ENV_PARAM = "environment";
	public static String CATALOG_PARAM = "catalog";

	private final int domainLocalSecurityGroupNumber = -2_147_483_644;
	private final int globalSecurityGroupNumber = -2_147_483_646;
	private final int universalSecurityGroupNumber = -2_147_483_640;

	private UUID targetSystemUuid;
	private UUID role_catalogue;
	private String environment;
	private UUID catalog;

	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private SysRoleSystemAttributeService sysRoleSystemAttributeService;
	@Autowired
	private SysRoleSystemService sysRoleSystemService;
	@Autowired
	private IdmRoleCatalogueRoleService roleCatalogueRoleService;
	@Autowired
	private FormService formService;
	@Autowired
	private SysSystemService systemService;

	@Override
	public void init(Map<String, Object> properties) {
		super.init(properties);
		targetSystemUuid = getParameterConverter().toUuid(properties, SYSTEMS_PARAM);
		role_catalogue = getParameterConverter().toUuid(properties, ROLE_CATALOGUE_PARAM);
		environment = getParameterConverter().toString(properties, ENV_PARAM);
		catalog = getParameterConverter().toUuid(properties, CATALOG_PARAM);
	}

	@Override
	public OperationResult process() {
		List<UUID> roles = roleCatalogueRoleService.findAllByRoleCatalogue(role_catalogue).stream()
				.map(IdmRoleCatalogueRoleDto::getRole)
				.collect(Collectors.toList());

		this.count = (long) roles.size();
		this.counter = 0L;

		// duplicate each role
		roles.forEach(idmRole -> {
			IdmRoleDto idmRoleDto = roleService.get(idmRole);

			// check type of group, because we only want to duplicate domain local
			Optional<IdmFormValueDto> groupType = formService.getValues(idmRoleDto.getId(), IdmRoleDto.class, "groupType").stream().findFirst();
			if (!groupType.isPresent()) {
				this.logItemProcessed(idmRoleDto, new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.DUPLICATE_CROSS_AD_LRT_ERROR,
						ImmutableMap.of("message", "Role " + idmRoleDto.getName() + " not duplicate, because role has empty groupType EAV."))).build());
				return;
			}
			Integer groupTypeValue = (Integer) groupType.get().getValue();

			// get mapping from source role
			SysRoleSystemFilter filter = new SysRoleSystemFilter();
			filter.setRoleId(idmRoleDto.getId());
			List<SysRoleSystemDto> source = sysRoleSystemService.find(filter, null).getContent();

			if (groupTypeValue.equals(domainLocalSecurityGroupNumber) || groupTypeValue.equals(universalSecurityGroupNumber)) {
				LOG.info("Role [{}] is domain local or universal so we can duplicate it", idmRoleDto.getId());
			} else if (groupTypeValue.equals(globalSecurityGroupNumber)) {
				LOG.info("Role [{}] is global group, we can duplicate it only if the target system is in the same domain as this AD group", idmRoleDto.getId());
				// Get source system
				if (source.size() == 1) {
					UUID sourceSystemUuid = source.get(0).getSystem();
					String sourceHostname = getSystemHostname(sourceSystemUuid);

					// Get target system
					String targetHostname = getSystemHostname(targetSystemUuid);

					// If domain is same we can duplicate role, otherwise we will not perform duplication
					if (!sourceHostname.equals(targetHostname)) {
						this.logItemProcessed(idmRoleDto, new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.DUPLICATE_CROSS_AD_LRT_ERROR,
								ImmutableMap.of("message", "Source system and target system has different hostname so we can't duplicate role when groupType is global"))).build());
						return;
					}
				} else {
					this.logItemProcessed(idmRoleDto, new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.DUPLICATE_CROSS_AD_LRT_ERROR,
							ImmutableMap.of("message", "Role " + idmRoleDto.getId() + " has no system, so we can't validate if hostname are same. Role will can't be duplicated"))).build());
					return;
				}
			} else {
				this.logItemProcessed(idmRoleDto, new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.DUPLICATE_CROSS_AD_LRT_ERROR,
						ImmutableMap.of("message", "Role " + idmRoleDto.getId() + " is not domain local, universal nor global we can't duplicate it."))).build());
				return;
			}

			LOG.info("Duplicating role [{}]", idmRoleDto.getCode());

			IdmRoleDto byBaseCodeAndEnvironment = roleService.getByBaseCodeAndEnvironment(idmRoleDto.getBaseCode(), environment);

			if (byBaseCodeAndEnvironment == null) {
				IdmRoleDto targetRole = new IdmRoleDto();
				targetRole.setId(UUID.randomUUID());
				targetRole.setBaseCode(idmRoleDto.getBaseCode());
				targetRole.setEnvironment(environment);

				EntityEvent<IdmRoleDto> event = new RoleEvent(RoleEvent.RoleEventType.DUPLICATE, targetRole, new ConfigurationMap(getProperties()).toMap());
				event.setOriginalSource(idmRoleDto); // original source is the cloned role
				event.setPriority(PriorityType.IMMEDIATE); // we want to be sync
				roleService.publish(event, IdmBasePermission.CREATE, IdmBasePermission.UPDATE);
				this.logItemProcessed(idmRoleDto, new OperationResult.Builder(OperationState.EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.DUPLICATE_CROSS_AD_LRT,
						ImmutableMap.of("message", "Role " + idmRoleDto.getName() + " duplicated"))).build());
			} else {
				this.logItemProcessed(idmRoleDto, new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.DUPLICATE_CROSS_AD_LRT_ERROR,
						ImmutableMap.of("message", "Role " + idmRoleDto.getName() + " not duplicate, because role with this code and environment exists"))).build());
				return;
			}

			IdmRoleDto newRole = roleService.getByBaseCodeAndEnvironment(idmRoleDto.getBaseCode(), environment);

			LOG.info("Add mapping to system to role [{}]", idmRoleDto.getCode());


			if (source.size() == 1) {
				SysRoleSystemDto sysRoleSystemDto = source.get(0);

				SysRoleSystemAttributeFilter attributeFilter = new SysRoleSystemAttributeFilter();
				attributeFilter.setRoleSystemId(sysRoleSystemDto.getId());
				List<SysRoleSystemAttributeDto> sourceAttribute = sysRoleSystemAttributeService.find(attributeFilter, null).getContent();

				if (sourceAttribute.size() == 1) {
					SysRoleSystemAttributeDto attributeDto = sourceAttribute.get(0);
					// create mapping to system and fill value
					sysRoleSystemAttributeService.addRoleMappingAttribute(targetSystemUuid, newRole.getId(), attributeDto.getName(), attributeDto.getTransformScript(), "__ACCOUNT__");
					LOG.info("Mapping for role [{}] created", idmRoleDto.getCode());
				}
			} else {
				LOG.info("Role [{}] has more then one systems can't pick right one, new role will be without mapping", idmRoleDto.getCode());
			}

			// add role to catalogue
			if (catalog != null) {
				IdmRoleCatalogueRoleDto roleCatalogueRoleDto = new IdmRoleCatalogueRoleDto();
				roleCatalogueRoleDto.setRole(newRole.getId());
				roleCatalogueRoleDto.setRoleCatalogue(catalog);
				roleCatalogueRoleService.save(roleCatalogueRoleDto);
				LOG.info("Role [{}] assigned to role catalogue", newRole.getCode());
			}

			++this.counter;
		});


		return new OperationResult.Builder(OperationState.EXECUTED).build();
	}

	private String getSystemHostname(UUID systemId) {
		SysSystemDto systemDto = systemService.get(systemId);
		IcConnectorConfiguration configuration = systemService.getConnectorConfiguration(systemDto);
		List<IcConfigurationProperty> properties = configuration.getConfigurationProperties().getProperties();
		Optional<IcConfigurationProperty> hostname = properties.stream().filter(icConfigurationProperty -> icConfigurationProperty.getName().equals("host")).findFirst();
		if (hostname.isPresent()) {
			return String.valueOf(hostname.get().getValue());
		}
		return "";
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> attributes = new LinkedList<>();
		IdmFormAttributeDto systems = new IdmFormAttributeDto(
				SYSTEMS_PARAM,
				"Systems for which the duplicated roles will be created",
				PersistentType.UUID);
		systems.setFaceType(AccFaceType.SYSTEM_SELECT);
		systems.setRequired(true);

		IdmFormAttributeDto roleCatalogue = new IdmFormAttributeDto(
				ROLE_CATALOGUE_PARAM,
				"Catalog from which all roles will be duplicated",
				PersistentType.UUID);
		roleCatalogue.setFaceType(AccFaceType.ROLE_CATALOGUE_SELECT);
		roleCatalogue.setRequired(true);

		IdmFormAttributeDto env = new IdmFormAttributeDto(
				ENV_PARAM,
				"Environment of the new duplicated roles",
				PersistentType.CODELIST,
				BaseCodeList.ENVIRONMENT);
		env.setRequired(true);

		IdmFormAttributeDto catalogue = new IdmFormAttributeDto(
				CATALOG_PARAM,
				"Catalogue in which the new roles will be created",
				PersistentType.UUID);
		catalogue.setFaceType(AccFaceType.ROLE_CATALOGUE_SELECT);
		catalogue.setRequired(true);

		attributes.add(systems);
		attributes.add(roleCatalogue);
		attributes.add(env);
		attributes.add(catalogue);
		return attributes;
	}
}
