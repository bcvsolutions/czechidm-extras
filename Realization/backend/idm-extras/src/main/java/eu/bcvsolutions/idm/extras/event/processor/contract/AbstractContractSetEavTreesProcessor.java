package eu.bcvsolutions.idm.extras.event.processor.contract;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractPositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractPositionFilter;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.EventType;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmContractPositionService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Abstract class for setting tree structure or node to eav of contract
 *
 * @author Marek Klement
 */
public abstract class AbstractContractSetEavTreesProcessor<E extends AbstractDto> extends CoreEventProcessor<E> {

	public static final String EAV_CONFIG_NODE_NAME = "idm.sec.extras.processor.set-node-to-eav";
	public static final String EAV_CONFIG_TREE_NAME = "idm.sec.extras.processor.set-structure-to-eav";
	public static final String EAV_CONFIG_FORM_CODE = "idm.sec.extras.processor.set-eav-tree.form-code";

	@Autowired
	private FormService formService;
	@Autowired
	private IdmTreeNodeService treeNodeService;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private IdmContractPositionService positionService;
	@Autowired
	private IdmFormAttributeService formAttributeService;

	public AbstractContractSetEavTreesProcessor(EventType... type) {
		super(type);
	}

	public void actualProcess(IdmIdentityContractDto contract) {
		IdmContractPositionFilter filter = new IdmContractPositionFilter();
		filter.setIdentityContractId(contract.getId());
		List<IdmContractPositionDto> positions = positionService.find(filter, null).getContent();
		//
		String eavNameTree = configurationService.getValue(EAV_CONFIG_TREE_NAME);
		String eavNameNode = configurationService.getValue(EAV_CONFIG_NODE_NAME);

		final boolean doTreeDown = eavNameTree != null && !eavNameTree.equals("");
		//
		List<String> valuesTree = new LinkedList<>();
		List<String> valuesNode = new LinkedList<>();
		// add all tree
		positions.forEach(position -> addValues(position.getWorkPosition(), valuesTree, valuesNode, doTreeDown));
		// just for identityContract
		addValues(contract.getWorkPosition(), valuesTree, valuesNode, doTreeDown);
		//
		String formCode = configurationService.getValue(EAV_CONFIG_FORM_CODE);
		IdmFormDefinitionDto definition;
		if(StringUtils.isBlank(formCode)){
			definition = getFormDefinition(FormService.DEFAULT_DEFINITION_CODE);
		} else {
			definition = getFormDefinition(formCode);
		}
		boolean attributeNodepresent = false;
		boolean attributeTreeepresent = false;
		for (IdmFormAttributeDto attribute : definition.getFormAttributes()) {
			if (attribute.getCode().equals(eavNameNode)) {
				attributeNodepresent = true;
			}
			if (attribute.getCode().equals(eavNameTree)) {
				attributeTreeepresent = true;
			}
		}
		if (!attributeNodepresent) {
			createFormAttribute(eavNameNode, definition.getId());
		}
		if (!attributeTreeepresent && doTreeDown) {
			createFormAttribute(eavNameTree, definition.getId());
		}
		if (doTreeDown) {
			getResult(contract.getId(), definition, eavNameTree, valuesTree);
		}
		getResult(contract.getId(), definition, eavNameNode, valuesNode);
	}

	private IdmFormDefinitionDto getFormDefinition(String formCode){
		IdmFormDefinitionDto definition = formService.getDefinition(IdmIdentityContractDto.class, formCode);
		if(definition==null){
			throw new ResultCodeException(ExtrasResultCode.SET_EAV_TREES_LRT_FAILED, ImmutableMap.of("code",
					EAV_CONFIG_FORM_CODE));
		}
		return definition;
	}

	private void createFormAttribute(String code, UUID formDefinition) {
		IdmFormAttributeDto formAttributeDto = new IdmFormAttributeDto();
		formAttributeDto.setCode(code);
		formAttributeDto.setName(code);
		formAttributeDto.setFormDefinition(formDefinition);
		formAttributeDto.setPersistentType(PersistentType.SHORTTEXT);
		formAttributeDto.setMultiple(true);
		formAttributeService.save(formAttributeDto);
	}

	private void getResult(UUID contract, IdmFormDefinitionDto definition, String eavName, List<String> values) {
		if (!values.isEmpty()) {
			List resultTree = formService.saveValues(contract,
					IdmIdentityContract.class,
					definition,
					eavName,
					Lists.newArrayList(values));
			if (resultTree == null) {
				throw new ResultCodeException(ExtrasResultCode.SET_EAV_TREES_NOT_SAVED);
			}
		}
	}

	public void addValues(UUID workPosition, List<String> treeValues, List<String> nodeValues, boolean treeEnabled) {
		if (workPosition != null) {
			IdmTreeNodeDto idmTreeNodeDto = treeNodeService.get(workPosition);
			if (idmTreeNodeDto != null) {
				if (treeEnabled) {
					List<IdmTreeNodeDto> allNodes = treeNodeService.findAllParents(idmTreeNodeDto.getId(), null);
					allNodes.add(idmTreeNodeDto);
					allNodes.forEach(parent -> addParentsToEav(parent.getCode(), treeValues));
				}
				addParentsToEav(idmTreeNodeDto.getCode(), nodeValues);
			}
		}
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
