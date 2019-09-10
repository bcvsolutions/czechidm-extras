package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.google.common.collect.ImmutableMap;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * @author Petr Hanák
 *
 */
public class CSVToIdM {
	
	private static final Logger LOG = LoggerFactory.getLogger(CSVToIdM.class);
	
	private InputStream attachmentData;
	private String rolesColumnName;
	private String roleCodeColumnName;
	private String descriptionColumnName;
	private String attributeColumnName;
	private String criticalityColumnName;
	private String guaranteeColumnName;
	private String guaranteeRoleColumnName;
	private String catalogueColumnName;
	private String subRoleColumnName;
	private String columnSeparator;
	private String multiValueSeparator;
	private Boolean hasDescription;
	private Boolean hasAttribute;
	private Boolean hasCriticality;
	private Boolean hasGuarantees;
	private Boolean hasGuaranteeRoles;
	private Boolean hasCatalogues;
	private Boolean hasSubRoles;
	private Boolean hasRoleCodes;
	
	private Map<String, String> roleDescriptions;
	private Map<String, List<String>> roleAttributes;
	private Map<String, String> criticalities;
	private Map<String, List<String>> guarantees;
	private Map<String, List<String>> guaranteeRoles;
	private Map<String, List<String>> catalogues;
	private Map<String, List<String>> subRoles;
	private Map<String, String> roleCodes;
	
	String[] header = new String[0];
	
	public Map<String, String> getRoleDescriptions() {
		return roleDescriptions;
	}
	
	public void setRoleDescriptions(Map<String, String> roleDescriptions) {
		this.roleDescriptions = roleDescriptions;
	}
	
	public Map<String, String> getRoleCodes() {
		return roleCodes;
	}
	
	public void setRoleCodes(Map<String, String> roleCodes) {
		this.roleCodes = roleCodes;
	}
	
	public Map<String, List<String>> getRoleAttributes() {
		return roleAttributes;
	}

	public void setRoleAttributes(Map<String, List<String>> roleAttributes) {
		this.roleAttributes = roleAttributes;
	}
	
	public Map<String, String> getCriticalities() {
		return criticalities;
	}

	public void setCriticalities(Map<String, String> criticalities) {
		this.criticalities = criticalities;
	}

	public Map<String, List<String>> getGuarantees() {
		return guarantees;
	}

	public void setGuarantees(Map<String, List<String>> guarantees) {
		this.guarantees = guarantees;
	}

	public Map<String, List<String>> getGuaranteeRoles() {
		return guaranteeRoles;
	}

	public void setGuaranteeRoles(Map<String, List<String>> guaranteeRoles) {
		this.guaranteeRoles = guaranteeRoles;
	}
	
	public Map<String, List<String>> getCatalogues() {
		return catalogues;
	}

	public void setCatalogues(Map<String, List<String>> catalogues) {
		this.catalogues = catalogues;
	}
	
	public Map<String, List<String>> getSubRoles() {
		return subRoles;
	}

	public void setSubRoles(Map<String, List<String>> subRoles) {
		this.subRoles = subRoles;
	}

	public CSVToIdM(InputStream attachmentData, String rolesColumnName, String roleCodeColumnName, 
			String descriptionColumnName, 
			String attributeColumnName, String criticalityColumnName, String guaranteeColumnName,
			String guaranteeRoleColumnName, String catalogueColumnName, String subRoleColumnName,
			String columnSeparator, String multiValueSeparator,
			Boolean hasDescription, Boolean hasAttribute, Boolean hasCriticality, 
			Boolean hasGuarantees, Boolean hasGuaranteeRoles, Boolean hasCatalogues, Boolean hasSubRoles,
			Boolean hasRoleCodes) {
		
		this.attachmentData = attachmentData;
		this.rolesColumnName = rolesColumnName;
		this.roleCodeColumnName = roleCodeColumnName;
		this.descriptionColumnName = descriptionColumnName;
		this.attributeColumnName = attributeColumnName;
		this.criticalityColumnName = criticalityColumnName;
		this.guaranteeColumnName = guaranteeColumnName;
		this.guaranteeRoleColumnName = guaranteeRoleColumnName;
		this.catalogueColumnName = catalogueColumnName;
		this.subRoleColumnName = subRoleColumnName;
		this.columnSeparator = columnSeparator;
		this.multiValueSeparator = multiValueSeparator;
		this.hasDescription = hasDescription;
		this.hasAttribute = hasAttribute;
		this.hasCriticality = hasCriticality;
		this.hasGuarantees = hasGuarantees;
		this.hasGuaranteeRoles = hasGuaranteeRoles;
		this.hasCatalogues = hasCatalogues;
		this.hasSubRoles = hasSubRoles;
		this.hasRoleCodes = hasRoleCodes;
		
		Maps maps = parseCSV();
		
		this.roleCodes = maps.getRoleCodes();
		this.roleDescriptions = maps.getRoleDescriptions();
		this.roleAttributes = maps.getRoleAttributes();
		this.criticalities = maps.getCriticalities();
		this.guarantees = maps.getGuarantees();
		this.guaranteeRoles = maps.getGuaranteeRoles();
		this.catalogues = maps.getCatalogues();
		this.subRoles = maps.getSubRoles();
	}
	
	/**
	 * Parse CSV file
	 * - read selected CSV column and return list of roles with description
	 * 
	 * @return
	 */
	private Maps parseCSV() {
		CSVParser parser = new CSVParserBuilder().withEscapeChar(CSVParser.DEFAULT_ESCAPE_CHARACTER).withQuoteChar('"')
				.withSeparator(columnSeparator.charAt(0)).build();
		CSVReader reader = null;
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(attachmentData));
			reader = new CSVReaderBuilder(br).withCSVParser(parser).build();
			
			header = reader.readNext();
			// find number of column with role name
			int roleColumnNumber = findColumnNumber(header, rolesColumnName);
			// find number of column with description name
			int descriptionColumnNumber = -1;
			if (hasDescription) {
				descriptionColumnNumber = findColumnNumber(header, descriptionColumnName);
			}
			// find number of column with description name
			int roleCodesColumnNumber = -1;
			if (hasRoleCodes) {
				roleCodesColumnNumber = findColumnNumber(header, roleCodeColumnName);
			}
			// find number of column with attributes
			int attributeColumnNumber = -1;
			if (hasAttribute) {
				attributeColumnNumber = findColumnNumber(header, attributeColumnName);
			}
			// find number of column with criticality
			int criticalityColumnNumber = -1;
			if (hasCriticality) {
				criticalityColumnNumber = findColumnNumber(header, criticalityColumnName);
			}
			// find number of column with guarantee
			int guaranteeColumnNumber = -1;
			if (hasGuarantees) {
				guaranteeColumnNumber = findColumnNumber(header, guaranteeColumnName);
			}
			// find number of column with guaranteeRole
			int guaranteeRolesColumnNumber = -1;
			if (hasGuaranteeRoles) {
				guaranteeRolesColumnNumber = findColumnNumber(header, guaranteeRoleColumnName);
			}
			
			// find number of column with catalogues
			int cataloguesColumnNumber = -1;
			if (hasCatalogues) {
				cataloguesColumnNumber = findColumnNumber(header, catalogueColumnName);
			}
			
			// find number of column with sub roles
			int subRolesColumnNumber = -1;
			if (hasSubRoles) {
				subRolesColumnNumber = findColumnNumber(header, subRoleColumnName);
			}

			Map<String, String> roleCodes = new HashMap<>();
			Map<String, String> roleDescriptions = new HashMap<>();
			Map<String, List<String>> roleAttributes = new HashMap<>();
			Map<String, String> criticalities = new HashMap<>();
			Map<String, List<String>> guarantees = new HashMap<>();
			Map<String, List<String>> guaranteeRoles = new HashMap<>();
			Map<String, List<String>> catalogues = new HashMap<>();
			Map<String, List<String>> subRoles = new HashMap<>();
			
			for (String[] line : reader) {
				String[] roleNames = line[roleColumnNumber].split(multiValueSeparator);
				// get role codes from the csv
				String roleCode;
				if (hasRoleCodes) {
					roleCode = line[roleCodesColumnNumber];
				} else {
					roleCode = "";
				}
				
				
				// get description from the csv
				String description;
				if (hasDescription) {
					description = line[descriptionColumnNumber];
				} else {
					description = "";
				}
				
				// get attributes from the csv
				String[] attributes;
				
				if (hasAttribute) {
					attributes = line[attributeColumnNumber].split(multiValueSeparator);
				} else {
					attributes = new String[0];
				}
				
				// get criticalities from the csv
				String criticality;
				if (hasCriticality) {
					criticality = line[criticalityColumnNumber];

					if(criticality.length() > 1) {
						LOG.error(String.format("The criticality in the CSV file cannot be multivalued! Error in line: %s", Arrays.toString(line)));
						throw new IllegalArgumentException("The criticality in the CSV file cannot be multivalued!" + Arrays.toString(line));
					}
				} else {
					criticality = "";
				}
				
				// get guarantees from the csv
				String[] guaranteesArray;
				
				if (hasGuarantees) {
					guaranteesArray = line[guaranteeColumnNumber].split(multiValueSeparator);
				} else {
					guaranteesArray = new String[0];
				}
				
				// get guaranteesRoles from the csv
				String[] guaranteesRolesArray;
				
				if (hasGuaranteeRoles) {
					guaranteesRolesArray = line[guaranteeRolesColumnNumber].split(multiValueSeparator);
				} else {
					guaranteesRolesArray = new String[0];
				}
				
				// get catalogues from the csv
				String[] cataloguesArray;
				
				if (hasCatalogues) {
					cataloguesArray = line[cataloguesColumnNumber].split(multiValueSeparator);
				} else {
					cataloguesArray = new String[0];
				}
				
				// get sub roles from the csv
				String[] subRolesArray;
				
				if (hasSubRoles) {
					subRolesArray = line[subRolesColumnNumber].split(multiValueSeparator);
				} else {
					subRolesArray = new String[0];
				}
				
				for (String roleName : roleNames) {
					if (!StringUtils.isEmpty(roleName)) {
						// save role codes
						roleCodes.put(roleName, roleCode);
						
						// save descriptions
						roleDescriptions.put(roleName, description);
						
						// save attributes
						List<String> attr = new ArrayList<>();
						for(String attribute : attributes) {
							attr.add(attribute);
						}
						roleAttributes.put(roleName, attr);
						
						// save criticalities
						criticalities.put(roleName, criticality);
						
						// save guarantees
						List<String> guar = new ArrayList<>();
						for(String guarantee : guaranteesArray) {
							guar.add(guarantee);
						}
						guarantees.put(roleName, guar);
						
						// save guaranteesRoles
						List<String> guarRoles = new ArrayList<>();
						for(String guaranteeRole : guaranteesRolesArray) {
							guarRoles.add(guaranteeRole);
						}
						guaranteeRoles.put(roleName, guarRoles);
						
						// save catalogues
						List<String> catalogueList = new ArrayList<>();
						for(String catalogue : cataloguesArray) {
							catalogueList.add(catalogue);
						}
						catalogues.put(roleName, catalogueList);
						
						// save sub roles
						List<String> subRolesList = new ArrayList<>();
						for(String subRole : subRolesArray) {
							subRolesList.add(subRole);
						}
						subRoles.put(roleName, subRolesList);
					}
				}
			}
			
			Maps maps = new Maps(roleCodes, roleDescriptions, roleAttributes, criticalities, guarantees, guaranteeRoles, catalogues, subRoles);
			return maps;
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					LOG.error("Reader exception: ", e);
				}
			}
		}
	}

	
	/**
	 * finds number of column
	 * 
	 * @param header
	 * @param columnName
	 * @return
	 */
	private int findColumnNumber(String[] header, String columnName) {
		int counterHeader = 0;
		for (String item : header){
			if(item.equals(columnName)){
				return counterHeader;
			}
			counterHeader++;
		}
		throw new ResultCodeException(ExtrasResultCode.COLUMN_NOT_FOUND, ImmutableMap.of("column name", columnName));
	}

}

 class Maps {
	private Map<String, String> roleDescriptions;
	private Map<String, String> roleCodes;
	private Map<String, List<String>> roleAttributes;
	private Map<String, String> criticalities;
	private Map<String, List<String>> guarantees;
	private Map<String, List<String>> guaranteeRoles;
	private Map<String, List<String>> catalogues; 
	private Map<String, List<String>> subRoles;
	
	public Maps(Map<String, String> roleCodes, Map<String, String> roleDescriptions, Map<String, List<String>> roleAttributes,
			Map<String, String> criticalities, Map<String, List<String>> guarantees,
			Map<String, List<String>> guaranteeRoles, Map<String, List<String>> catalogues, Map<String, List<String>> subRoles) {
		super();
		this.roleCodes = roleCodes;
		this.roleDescriptions = roleDescriptions;
		this.roleAttributes = roleAttributes;
		this.criticalities = criticalities;
		this.guarantees = guarantees;
		this.guaranteeRoles = guaranteeRoles;
		this.catalogues = catalogues;
		this.subRoles = subRoles;
	}

	public Map<String, String> getRoleCodes() {
		return roleCodes;
	}

	public void setRoleCodes(Map<String, String> roleCodes) {
		this.roleCodes = roleCodes;
	}
	
	public Map<String, String> getRoleDescriptions() {
		return roleDescriptions;
	}

	public void setRoleDescriptions(Map<String, String> roleDescriptions) {
		this.roleDescriptions = roleDescriptions;
	}

	public Map<String, List<String>> getRoleAttributes() {
		return roleAttributes;
	}

	public void setRoleAttributes(Map<String, List<String>> roleAttributes) {
		this.roleAttributes = roleAttributes;
	}

	public Map<String, String> getCriticalities() {
		return criticalities;
	}

	public void setCriticalities(Map<String, String> criticalities) {
		this.criticalities = criticalities;
	}

	public Map<String, List<String>> getGuarantees() {
		return guarantees;
	}

	public void setGuarantees(Map<String, List<String>> guarantees) {
		this.guarantees = guarantees;
	}

	public Map<String, List<String>> getGuaranteeRoles() {
		return guaranteeRoles;
	}

	public void setGuaranteeRoles(Map<String, List<String>> guaranteeRoles) {
		this.guaranteeRoles = guaranteeRoles;
	}
	
	public Map<String, List<String>> getCatalogues() {
		return catalogues;
	}

	public void setCatalogues(Map<String, List<String>> catalogues) {
		this.catalogues = catalogues;
	}
	
	public Map<String, List<String>> getSubRoles() {
		return subRoles;
	}

	public void setSubRoles(Map<String, List<String>> subRoles) {
		this.subRoles = subRoles;
	}
 }