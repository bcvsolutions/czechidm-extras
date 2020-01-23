package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.eav.domain.AccFaceType;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.utils.EntityUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseCodeList;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.event.RoleEvent;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * @author Roman Kucera
 */
@Service
@DisallowConcurrentExecution
@Description("Duplicate cross domain roles")
public class CrossAdRolesDuplication extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CrossAdRolesDuplication.class);

	private static String SYSTEMS_PARAM = "systems";
	private static String ROLES_PARAM = "roles";
	private static String ENV_PARAM = "environment";

	private UUID targetSystemUuid;
	private List<UUID> roles;
	private String environment;

	@Autowired
	private IdmRoleService roleService;

	@Override
	public void init(Map<String, Object> properties) {
		super.init(properties);
		targetSystemUuid = getParameterConverter().toUuid(properties, SYSTEMS_PARAM);
		roles = getParameterConverter().toUuids(properties, ROLES_PARAM);
		environment = getParameterConverter().toString(properties, ENV_PARAM);
	}

	@Override
	public OperationResult process() {
		this.count = (long) roles.size();
		this.counter = 0L;

		// duplicate each role
		roles.forEach(idmRole -> {
			IdmRoleDto idmRoleDto = roleService.get(idmRole);

			IdmRoleDto targetRole = new IdmRoleDto();
			targetRole.setId(UUID.randomUUID());
			targetRole.setBaseCode(idmRoleDto.getBaseCode());
			targetRole.setEnvironment(environment);

			// TODO maybe need to add some properties into event we will see
			EntityEvent<IdmRoleDto> event = new RoleEvent(RoleEvent.RoleEventType.DUPLICATE, targetRole, new ConfigurationMap(getProperties()).toMap());
			event.setOriginalSource(idmRoleDto); // original source is the cloned role
			event.setPriority(PriorityType.IMMEDIATE); // we want to be sync
			roleService.publish(event, IdmBasePermission.CREATE, IdmBasePermission.UPDATE);
			this.logItemProcessed(idmRoleDto, new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
					ImmutableMap.of("message", "Role" + idmRoleDto.getName() + " duplicated"))).build());

			++this.counter;
		});

		// TODO create system mapping
		// get new created roles

		// create mapping to system and fill value

		return new OperationResult.Builder(OperationState.EXECUTED).build();
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

		IdmFormAttributeDto roles = new IdmFormAttributeDto(
				ROLES_PARAM,
				"Roles which will be duplicated",
				PersistentType.UUID);
		roles.setFaceType(AccFaceType.ROLE_SELECT);
		roles.setMultiple(true);
		roles.setRequired(true);

		IdmFormAttributeDto env = new IdmFormAttributeDto(
				ENV_PARAM,
				"Environment of the new duplicated roles",
				PersistentType.CODELIST,
				BaseCodeList.ENVIRONMENT);
		env.setRequired(true);

		attributes.add(systems);
		attributes.add(roles);
		attributes.add(env);
		return attributes;
	}

	/**
	 * Get roles for duplication
	 *
	 * @return
	 */
	private List<IdmRoleDto> getRoles() {
		Object rolesAsObject = this.getProperties().get(ROLES_PARAM);
		//
		if (rolesAsObject == null) {
			return Collections.emptyList();
		}
		//
		if (!(rolesAsObject instanceof Collection)) {
			return Collections.emptyList();
		}
		List<IdmRoleDto> roles = new ArrayList<>();
		((Collection<?>) rolesAsObject).forEach(role -> {
			UUID uuid = EntityUtils.toUuid(role);
			IdmRoleDto roleDto = roleService.get(uuid);
			if (roleDto == null) {
				LOG.warn("Role with id [{}] not found. The role will be skipped.", uuid);
			} else {
				roles.add(roleService.get(uuid));
			}
		});
		return roles;
	}
}
