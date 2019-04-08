package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmContractPositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmContractPositionService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
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

	public static final String PARAM_NODE_CODE = "Code of top node";
	private static final Logger LOG = LoggerFactory.getLogger(SaveAllNodesForSubtreeExecutor.class);
	private String nodeCode;

	@Autowired
	private IdmTreeNodeService treeNodeService;
	@Autowired
	private IdmContractPositionService positionService;
	@Autowired
	private IdmIdentityContractService contractService;

	@Override
	public Boolean process() {
		if (nodeCode == null) {
			throw new ResultCodeException(ExtrasResultCode.SET_EAV_TREES_NODE_IS_NULL);
		}
		IdmTreeNodeFilter filter = new IdmTreeNodeFilter();
		filter.setCode(nodeCode);
		List<IdmTreeNodeDto> nodes = treeNodeService.find(filter, null).getContent();
		if (nodes.size() == 0) {
			throw new ResultCodeException(ExtrasResultCode.SET_EAV_TREES_SIZE_OF_NODES_IS_ZERO);
		} else if (nodes.size() > 1) {
			throw new ResultCodeException(ExtrasResultCode.SET_EAV_TREES_SIZE_OF_NODES_IS_NOT_ONE);
		}
		IdmTreeNodeDto topNode = nodes.get(0);
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
		setCount((long) (contractPositions.size() + identityContracts.size()));
		setCounter(0L);

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
	public List<String> getPropertyNames() {
		LOG.debug("Start getPropertyName");
		List<String> params = super.getPropertyNames();
		params.add(PARAM_NODE_CODE);

		return params;
	}

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		nodeCode = getParameterConverter().toString(properties, PARAM_NODE_CODE);
	}


}
