package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RecursionType;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * This tasks allows to import automatic roles to tree nodes from a CSV file.
 * 
 * @author Tomáš Doischer
 *
 */

@Component
@Description("Create automatic roles for tree nodes from CSV")
public class ImportAutomaticRoleForTreeNodeFromCSVExecutor extends AbstractCsvImportTask {
	private static final Logger LOG = LoggerFactory.getLogger(ImportAutomaticRoleForTreeNodeFromCSVExecutor.class);
	
	static final String PARAM_ROLES_COLUMN_NAME = "Column with role codes";
	static final String PARAM_COLUMN_NODE_CODES = "Column with node codes";
	static final String PARAM_COLUMN_NODE_IDS = "Column with node IDs";
	static final String PARAM_COLUMN_RECURSION_TYPE = "Column with recursion type";
	
	private String roleCodesColumnName;
	private String nodeCodesColumnName;
	private String nodeIdColumnName;
	private String recursionTypeColumnName;
	private Boolean useNodeIds;
	private Boolean setRecursion;
	
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmRoleTreeNodeService roleTreeNodeService;
	@Autowired
	private IdmTreeNodeService treeNodeService;
	
	@Override
	protected void processRecords(List<CSVRecord> records) {
		LOG.debug("Start process");
		
		records.forEach(record -> {
			String roleCode = record.get(roleCodesColumnName);
			String nodeCode = record.get(nodeCodesColumnName);
			
			String nodeId = "";
			if (nodeIdColumnName != null) {
				nodeId = record.get(nodeIdColumnName);
			}
			
			String recursion = "";
			
			if (recursionTypeColumnName != null) {
				recursion = record.get(recursionTypeColumnName);
			}
			
			createRoleTreeNode(nodeCode, nodeId, roleCode, recursion);
		});
	}
	
	/**
	 * Creates new role tree node if it doesn't exist yet.
	 * 
	 * @param nodeCode
	 * @param nodeId
	 * @param roleCode
	 * @param recursionType
	 * @return
	 */
	private IdmRoleTreeNodeDto createRoleTreeNode(String nodeCode, String nodeId, String roleCode,
			String recursionType) {
		IdmTreeNodeDto treeNode = findTreeNode(nodeCode, nodeId);
		if (treeNode == null) {
			this.logItemProcessed(new IdmTreeNodeDto(UUID.randomUUID()), taskNotCompleted(String.format("The tree node %s does not exist, automatic role with role %s was not created", nodeCode, roleCode)));
			LOG.warn("The tree node [{}] doesn't exist.", nodeCode);
			return null;
		}
		IdmRoleDto role = roleService.getByCode(roleCode);
		if (role == null) {
			this.logItemProcessed(treeNode, taskNotCompleted(String.format("The role %s does not exist, automatic role on tree node %s was not created", roleCode, treeNode.getName())));
			LOG.warn("The role [{}] doesn't exist.", roleCode);
			return null;
		}
		
		if (roleTreeNodeExists(treeNode, role) != null) {
			this.logItemProcessed(treeNode, taskNotCompleted(String.format("Automatic role %s on node %s already exists", role.getName(), treeNode.getName())));
			LOG.warn("The role for [{}] and [{}] already exists", nodeCode, roleCode);
			return null;
		}
		
		IdmRoleTreeNodeDto roleTreeNode = new IdmRoleTreeNodeDto();
		roleTreeNode.setRole(role.getId());
		roleTreeNode.setTreeNode(treeNode.getId());
		roleTreeNode.setName(String.format("%s | %s", role.getName(), treeNode.getName()));
		if (setRecursion && !StringUtils.isEmpty(recursionType) && RecursionType.valueOf(recursionType) != null) {
			roleTreeNode.setRecursionType(RecursionType.valueOf(recursionType));		
		}
		
		++this.counter;
		this.logItemProcessed(treeNode, taskCompleted(String.format("Automatic role %s on node %s created", role.getName(), treeNode.getName())));
		return roleTreeNodeService.save(roleTreeNode);
	}
	
	/**
	 * Finds a tree node based on its code, if that fails and we have the node id, it uses node id.
	 * 
	 * @param nodeCode
	 * @param nodeId
	 * @return
	 */
	private IdmTreeNodeDto findTreeNode(String nodeCode, String nodeId) {
		IdmTreeNodeFilter filter = new IdmTreeNodeFilter();
		filter.setCode(nodeCode);
		IdmTreeNodeDto node = null;
		
		List<IdmTreeNodeDto> nodes = treeNodeService.find(filter, null).getContent();
		
		if (!nodes.isEmpty()) {
			node = nodes.get(0);
		}
		
		if (node != null) {
			return node;
		}
		
		if (useNodeIds) {
			node = treeNodeService.get(nodeId);
		}
		
		return node;
	}
	
	/**
	 * Checks if the automatic role already exists and if so, it return the role.
	 * 
	 * @param node
	 * @param role
	 * @return
	 */
	private IdmRoleTreeNodeDto roleTreeNodeExists(IdmTreeNodeDto node, IdmRoleDto role) {
		IdmRoleTreeNodeFilter nodeFilter = new IdmRoleTreeNodeFilter();
		nodeFilter.setRoleId(role.getId());
		nodeFilter.setTreeNodeId(node.getId());
		
		List<IdmRoleTreeNodeDto> existing = roleTreeNodeService.find(nodeFilter, null).getContent();
		if (existing.isEmpty()) {
			return null;
		} 
		
		return existing.get(0);
	}
	
	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		roleCodesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		nodeCodesColumnName = getParameterConverter().toString(properties, PARAM_COLUMN_NODE_CODES);
		nodeIdColumnName = getParameterConverter().toString(properties, PARAM_COLUMN_NODE_IDS);
		recursionTypeColumnName = getParameterConverter().toString(properties, PARAM_COLUMN_RECURSION_TYPE);
		
		if (nodeIdColumnName != null) {
			useNodeIds = Boolean.TRUE;
		} else {
			useNodeIds = Boolean.FALSE;
		}
		
		if (recursionTypeColumnName != null) {
			setRecursion = Boolean.TRUE;
		} else {
			setRecursion = Boolean.FALSE;
		}
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_ROLES_COLUMN_NAME, roleCodesColumnName);
		props.put(PARAM_COLUMN_NODE_CODES, nodeCodesColumnName);
		props.put(PARAM_COLUMN_NODE_IDS, nodeIdColumnName);
		props.put(PARAM_COLUMN_RECURSION_TYPE, recursionTypeColumnName);

		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();
		
		IdmFormAttributeDto nodeCodesColumnNameAttribute = new IdmFormAttributeDto(PARAM_COLUMN_NODE_CODES, PARAM_COLUMN_NODE_CODES,
				PersistentType.SHORTTEXT);
		nodeCodesColumnNameAttribute.setRequired(true);
		
		IdmFormAttributeDto nodeIdColumnNameAttribute = new IdmFormAttributeDto(PARAM_COLUMN_NODE_IDS, PARAM_COLUMN_NODE_IDS,
				PersistentType.SHORTTEXT);
		nodeIdColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto roleCodesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		roleCodesColumnNameAttribute.setRequired(true);
		
		IdmFormAttributeDto recursionTypeColumnAttribute = new IdmFormAttributeDto(PARAM_COLUMN_RECURSION_TYPE, PARAM_COLUMN_RECURSION_TYPE,
				PersistentType.SHORTTEXT);
		recursionTypeColumnAttribute.setRequired(false);
		//
		formAttributes.addAll(Lists.newArrayList(nodeCodesColumnNameAttribute, nodeIdColumnNameAttribute,
				roleCodesColumnNameAttribute, recursionTypeColumnAttribute));
		return formAttributes;
	}

	
	private OperationResult taskCompleted(String message) {
		return new OperationResult.Builder(OperationState.EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
				ImmutableMap.of("message", message))).build();
	}

	private OperationResult taskNotCompleted(String message) {
		return new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
				ImmutableMap.of("message", message))).build();
	}

	@Override
	protected void processOneDynamicAttribute(String namePrefix, String name, String valuePrefix, String value,
			boolean isEav) {
		throw new UnsupportedOperationException("No dynamic attributes present");
	}
}


