package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RecursionType;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * This tasks allows to import automatic roles to tree nodes from a CSV file.
 * 
 * @author Tomáš Doischer
 *
 */

@Component
@Description("Create automatic roles for tree nodes from CSV")
public class ImportAutomaticRoleForTreeNodeFromCSVExecutor extends AbstractSchedulableTaskExecutor<OperationResult> {
	private static final Logger LOG = LoggerFactory.getLogger(ImportAutomaticRoleForTreeNodeFromCSVExecutor.class);
	
	static final String PARAM_CSV_ATTACHMENT = "Import csv file";
	static final String PARAM_ROLES_COLUMN_NAME = "Column with role codes";
	static final String PARAM_COLUMN_SEPARATOR = "Column separator";
	static final String PARAM_COLUMN_NODE_CODES = "Column with node codes";
	static final String PARAM_COLUMN_NODE_IDS = "Column with node IDs";
	static final String PARAM_COLUMN_RECURSION_TYPE = "Column with recursion type";
	
	private UUID attachmentId;
	private String roleCodesColumnName;
	private String columnSeparator;
	private String nodeCodesColumnName;
	private String nodeIdColumnName;
	private String recursionTypeColumnName;
	private Boolean useNodeIds;
	private Boolean setRecursion;
	
	@Autowired
	private AttachmentManager attachmentManager;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmRoleTreeNodeService roleTreeNodeService;
	@Autowired
	private IdmTreeNodeService treeNodeService;
	
	@Override
	public OperationResult process() {
		LOG.debug("Start process");
		Map<String, ImportedNodeRole> values = parseCsv();
		
		if (!values.isEmpty()) {
			this.count = (long) values.size();
			this.counter = 0L;
			
			for (Map.Entry<String, ImportedNodeRole> entry : values.entrySet()) {
				String node = entry.getKey();
				ImportedNodeRole inr = entry.getValue();
				Map<String, String> rolesAndRecursion = inr.getRolesAndRecursion();
				for (Map.Entry<String, String> secondEntry : rolesAndRecursion.entrySet()) {
					createRoleTreeNode(node, inr.getNodeId(), secondEntry.getKey(), secondEntry.getValue());
				}
			}
		} else {
			throw new ResultCodeException(ExtrasResultCode.ROLES_NOT_FOUND);
		}
		//
		return new OperationResult.Builder(OperationState.CREATED).build();
	}
	
	private Map<String, ImportedNodeRole> parseCsv() {
		CSVParser parser = new CSVParserBuilder().withEscapeChar(CSVParser.DEFAULT_ESCAPE_CHARACTER).withQuoteChar('"')
				.withSeparator(columnSeparator.charAt(0)).build();
		CSVReader reader = null;
		try {
			InputStream attachmentData = attachmentManager.getAttachmentData(attachmentId);
			BufferedReader br = new BufferedReader(new InputStreamReader(attachmentData));
			reader = new CSVReaderBuilder(br).withCSVParser(parser).build();
			String[] header = reader.readNext();
			
			// find number of column with role codes
			int roleCodeColumnNumber = findColumnNumber(header, roleCodesColumnName);

			// find number of column with node codes
			int nodeCodesColumnNumber = findColumnNumber(header, nodeCodesColumnName);

			// find number of column with node ids
			int nodeIdColumnNumber = findColumnNumber(header, nodeIdColumnName);

			// find number of column with recursion type
			int recursionTypeColumnNumber = findColumnNumber(header, recursionTypeColumnName);;		

			Map<String, ImportedNodeRole> values = new HashMap<>();

			for (String[] line : reader) {
				String roleCode = line[roleCodeColumnNumber];
				String nodeCode = line[nodeCodesColumnNumber];
				String nodeId = "";
				String recursionType = "";
				
				if (useNodeIds) {
					nodeId = line[nodeIdColumnNumber];
				}
				
				if (setRecursion) {
					recursionType = line[recursionTypeColumnNumber];
				}
				
				// the node is already in the map
				if (values.containsKey(nodeCode)) {
					values.get(nodeCode).addRoleAndRecursion(roleCode, recursionType);
				} else {
					if (useNodeIds) {
						ImportedNodeRole importedNodeRole = new ImportedNodeRole(nodeId, roleCode, recursionType);
						values.put(nodeCode, importedNodeRole);
					} else {
						ImportedNodeRole importedNodeRole = new ImportedNodeRole(roleCode, recursionType);
						values.put(nodeCode, importedNodeRole);
					}
				}
			}
			return values;
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
	}
	
	/**
	 * Finds number of column we need
	 * 
	 * @param header
	 * @param columnName
	 * @return
	 */
	private int findColumnNumber(String[] header, String columnName) {
		if (columnName == null) {
			return -1;
		}
		int counterHeader = 0;
		for (String item : header){
			if(item.equals(columnName)){
				return counterHeader;
			}
			counterHeader++;
		}
		counterHeader = -1;
		if (counterHeader == -1) {
			if ((useNodeIds == null || useNodeIds.equals(Boolean.FALSE)) && columnName.equals(PARAM_COLUMN_NODE_IDS)) {
				return -1;
			}
			if ((setRecursion == null || setRecursion.equals(Boolean.FALSE)) && columnName.equals(PARAM_COLUMN_RECURSION_TYPE)) {
				return -1;
			}
			throw new ResultCodeException(ExtrasResultCode.COLUMN_NOT_FOUND, ImmutableMap.of("column name", columnName));
		}
		return counterHeader;
	}
	
	/**
	 * Creates new tree node if it doesn't exist yet.
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
			LOG.debug(String.format("The tree node %s doesn't exist.", nodeCode));
			return null;
		}
		IdmRoleDto role = roleService.getByCode(roleCode);
		if (role == null) {
			LOG.debug(String.format("The role %s doesn't exist.", roleCode));
			return null;
		}
		
		if (roleTreeNodeExists(treeNode, role) != null) {
			LOG.debug(String.format("The role for %s and %s already exists", nodeCode, roleCode));
			return null;
		}
		
		IdmRoleTreeNodeDto roleTreeNode = new IdmRoleTreeNodeDto();
		roleTreeNode.setRole(role.getId());
		roleTreeNode.setTreeNode(treeNode.getId());
		roleTreeNode.setName(String.format("%s | %s", role.getName(), treeNode.getName()));
		if ((setRecursion && recursionType != null && !recursionType.equals("")) 
				&& (RecursionType.valueOf(recursionType) != null)) {
			roleTreeNode.setRecursionType(RecursionType.valueOf(recursionType));
		}
		
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
		
		IdmTreeNodeDto node = treeNodeService.find(filter, null, null).getContent().get(0);
		
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
	 * @param nodeCode
	 * @param role
	 * @return
	 */
	private IdmRoleTreeNodeDto roleTreeNodeExists(IdmTreeNodeDto node, IdmRoleDto role) {
		IdmRoleTreeNodeFilter nodeFilter = new IdmRoleTreeNodeFilter();
		nodeFilter.setRoleId(role.getId());
		nodeFilter.setTreeNodeId(node.getId());
		
		List<IdmRoleTreeNodeDto> existing = roleTreeNodeService.find(nodeFilter, null, null).getContent();
		if (existing == null || existing.isEmpty()) {
			return null;
		} 
		
		return existing.get(0);
	}
	
	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		attachmentId = getParameterConverter().toUuid(properties, PARAM_CSV_ATTACHMENT);
		roleCodesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		columnSeparator = getParameterConverter().toString(properties, PARAM_COLUMN_SEPARATOR);
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
		props.put(PARAM_CSV_ATTACHMENT, attachmentId);
		props.put(PARAM_ROLES_COLUMN_NAME, roleCodesColumnName);
		props.put(PARAM_COLUMN_SEPARATOR, columnSeparator);
		props.put(PARAM_COLUMN_NODE_CODES, nodeCodesColumnName);
		props.put(PARAM_COLUMN_NODE_IDS, nodeIdColumnName);
		props.put(PARAM_COLUMN_RECURSION_TYPE, recursionTypeColumnName);

		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		IdmFormAttributeDto csvAttachment = new IdmFormAttributeDto(PARAM_CSV_ATTACHMENT, PARAM_CSV_ATTACHMENT,
				PersistentType.ATTACHMENT);
		csvAttachment.setRequired(true);
		
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
		
		IdmFormAttributeDto columnSeparatorAttribute = new IdmFormAttributeDto(PARAM_COLUMN_SEPARATOR, PARAM_COLUMN_SEPARATOR,
				PersistentType.CHAR);
		columnSeparatorAttribute.setDefaultValue(";");
		columnSeparatorAttribute.setRequired(true);
		//
		return Lists.newArrayList(csvAttachment,nodeCodesColumnNameAttribute, nodeIdColumnNameAttribute,
				roleCodesColumnNameAttribute, recursionTypeColumnAttribute, columnSeparatorAttribute);
	}
	
	
	
	class ImportedNodeRole {
		String nodeId;
		Map<String, String> rolesAndRecursion;
		
		public ImportedNodeRole(String nodeId, String roleCode, String recursion) {
			this.nodeId = nodeId;
			this.rolesAndRecursion = new HashMap<>();
			rolesAndRecursion.put(roleCode, recursion);
		}
		
		public ImportedNodeRole(String roleCode, String recursion) {
			this.rolesAndRecursion = new HashMap<>();
			rolesAndRecursion.put(roleCode, recursion);
		}
		
		public void addRoleAndRecursion(String roleCode, String recursion) {
			this.rolesAndRecursion.put(roleCode, recursion);
		}

		public String getNodeId() {
			return nodeId;
		}
		public void setNodeIds(String nodeId) {
			this.nodeId = nodeId;
		}

		public Map<String, String> getRolesAndRecursion() {
			return rolesAndRecursion;
		}

		public void setRolesAndRecursion(Map<String, String> rolesAndRecursion) {
			this.rolesAndRecursion = rolesAndRecursion;
		}
	}
}


