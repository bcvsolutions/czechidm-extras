package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmContractPositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmContractPositionService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * Save all sub-nodes for given top node
 *
 * @author Marek Klement
 */
@Component
@Description("Save all sub-nodes for given top node")
public class SaveAllNodesForSubtreeExecutor extends AbstractSchedulableTaskExecutor<Boolean> {

	public static final String PARAM_NODE_ID = "Id of top node";
	private static final Logger LOG = LoggerFactory.getLogger(SaveAllNodesForSubtreeExecutor.class);
	private UUID nodeId;

	@Autowired
	private IdmTreeNodeService treeNodeService;
	@Autowired
	private IdmContractPositionService positionService;
	@Autowired
	private IdmIdentityContractService contractService;

	@Override
	public Boolean process() {
		if (nodeId == null) {
			throw new ResultCodeException(ExtrasResultCode.SET_EAV_TREES_NODE_IS_NULL);
		}

		IdmTreeNodeDto topNode = treeNodeService.get(nodeId);
		List<IdmTreeNodeDto> allNodes = treeNodeService.findChildrenByParent(topNode.getId(), null).getContent();
		saveContractsForNode(topNode);
		allNodes.forEach(this::saveContractsForNode);
		return true;
	}

	private void saveContractsForNode(IdmTreeNodeDto node) {
		// find contracts
		List<IdmContractPositionDto> contractPositions = positionService.findAllByWorkPosition(node.getId(), null);
		List<IdmIdentityContractDto> identityContracts = contractService.findAllByWorkPosition(node.getId(), null);
		// set count
		setCount(getCount() + (long) (contractPositions.size() + identityContracts.size()));

		// ContactPosition
		contractPositions.forEach(this::saveAndIncreaseContractPosition);
		// IdentityContract
		identityContracts.forEach(this::saveAndIncreaseIdentityContract);
	}

	private void saveAndIncreaseIdentityContract(IdmIdentityContractDto contract) {
		try {
			contractService.save(contract);
			logIdentityContract(contract, null);
		} catch (Exception e) {
			logIdentityContract(contract, e);
		}
	}

	private void logIdentityContract(IdmIdentityContractDto contract, Exception e) {
		if (e == null) {
			logItemProcessed(contract, new OperationResult
					.Builder(OperationState.EXECUTED)
					.setCode(contract.getPosition())
					.build());
			increaseCounter();
		} else {
			logItemProcessed(contract, new OperationResult
					.Builder(OperationState.EXCEPTION)
					.setCause(e)
					.setCode(contract.getPosition())
					.build());
		}
	}

	private void saveAndIncreaseContractPosition(IdmContractPositionDto contract) {
		try {
			positionService.save(contract);
			logContractPosition(contract, null);
		} catch (Exception e) {
			logContractPosition(contract, e);
		}
	}

	private void logContractPosition(IdmContractPositionDto contract, Exception e) {
		if (e != null) {
			logItemProcessed(contract, new OperationResult
					.Builder(OperationState.EXECUTED)
					.setCode(contract.getPosition())
					.build());
			increaseCounter();
		} else {
			logItemProcessed(contract, new OperationResult
					.Builder(OperationState.EXCEPTION)
					.setCause(e)
					.setCode(contract.getPosition())
					.build());
		}
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> attributes = super.getFormAttributes();
		for (IdmFormAttributeDto attribute : attributes) {
			if (attribute.getCode().equals(this.PARAM_NODE_ID)) {
				attribute.setFaceType(BaseFaceType.TREE_NODE_SELECT);
				attribute.setPersistentType(PersistentType.UUID);
			}
		}
		return attributes;
	}
	
	@Override
	public List<String> getPropertyNames() {
		LOG.debug("Start getPropertyName");
		List<String> params = super.getPropertyNames();
		params.add(PARAM_NODE_ID);

		return params;
	}

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		nodeId = getParameterConverter().toUuid(properties, PARAM_NODE_ID);
	}


}
