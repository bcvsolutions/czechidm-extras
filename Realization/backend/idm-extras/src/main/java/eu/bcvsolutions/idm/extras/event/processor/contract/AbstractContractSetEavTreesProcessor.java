package eu.bcvsolutions.idm.extras.event.processor.contract;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.dto.IdmContractPositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractPositionFilter;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.EventType;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmContractPositionService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;

public abstract class AbstractContractSetEavTreesProcessor<E extends Serializable> extends CoreEventProcessor<E> {

	public static final String EAV_CONFIG_NAME = "module.extras.processor.set-structure-to-eav";

	@Autowired
	private FormService formService;
	@Autowired
	private IdmTreeNodeService treeNodeService;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private IdmContractPositionService positionService;

	public AbstractContractSetEavTreesProcessor(EventType... type) {
		super(type);
	}

	public void actualProcess(IdmIdentityContractDto contract) {
		IdmContractPositionFilter filter = new IdmContractPositionFilter();
		filter.setIdentityContractId(contract.getId());
		List<IdmContractPositionDto> positions = positionService.find(filter, null).getContent();
		//
		String eavName = configurationService.getValue(EAV_CONFIG_NAME);
		//
		List<String> values = new LinkedList<>();
		positions.forEach(position -> addValues(position.getWorkPosition(), values));
		// just for identityContract
		addValues(contract.getWorkPosition(), values);
		//
		IdmFormDefinitionDto definition = formService.getDefinition(contract.getClass(), FormService.DEFAULT_DEFINITION_CODE);
		List result = formService.saveValues(contract.getId(),
				IdmIdentityContract.class,
				definition,
				eavName,
				Lists.newArrayList(values));
		if (result == null) {
			throw new IllegalArgumentException("Values not saved for some reason!");
		}
	}

	public void addValues(UUID workPosition, List<String> values) {
		IdmTreeNodeDto idmTreeNodeDto = treeNodeService.get(workPosition);
		List<IdmTreeNodeDto> allNodes = treeNodeService.findAllParents(idmTreeNodeDto.getId(), null);
		allNodes.add(idmTreeNodeDto);
		allNodes.forEach(parent -> addParentsToEav(parent.getCode(), values));
	}

	public void addParentsToEav(String code, List<String> values) {
		if (!values.contains(code)) {
			values.add(code);
		}
	}

	@Override
	public int getOrder() {
		return 600;
	}
}
