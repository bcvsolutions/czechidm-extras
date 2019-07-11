package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
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

public class CSVToIdM {
	
	private static final Logger LOG = LoggerFactory.getLogger(CSVToIdM.class);
	
	private InputStream attachmentData;
	private String rolesColumnName;
	private String descriptionColumnName;
	private String attributeColumnName;
	private String criticalityColumnName;
	private String guaranteeColumnName;
	private String guaranteeRoleColumnName;
	private String columnSeparator;
	private String multiValueSeparator;
	private Boolean hasDescription;
	private Boolean hasAttribute;
	private Boolean hasCriticality;
	private Boolean hasGuarantees;
	private Boolean hasGuaranteeRoles;

	
	private Maps maps;
	private Map<String, String> roleDescriptions;
	private Map<String, List<String>> roleAttributes;
	private Map<String, String> criticalities;
	private Map<String, List<String>> guarantees;
	private Map<String, List<String>> guaranteeRoles;
	
	String[] header = new String[0];
	
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
	
	

	public CSVToIdM(InputStream attachmentData, String rolesColumnName, String descriptionColumnName, 
			String attributeColumnName, String criticalityColumnName, String guaranteeColumnName,
			String guaranteeRoleColumnName, String columnSeparator, String multiValueSeparator,
			Boolean hasDescription, Boolean hasAttribute, Boolean hasCriticality, 
			Boolean hasGuarantees, Boolean hasGuaranteeRoles) {
		this.attachmentData = attachmentData;
		this.rolesColumnName = rolesColumnName;
		this.descriptionColumnName = descriptionColumnName;
		this.attributeColumnName = attributeColumnName;
		this.criticalityColumnName = criticalityColumnName;
		this.guaranteeColumnName = guaranteeColumnName;
		this.guaranteeRoleColumnName = guaranteeRoleColumnName;
		this.columnSeparator = columnSeparator;
		this.multiValueSeparator = multiValueSeparator;
		this.hasDescription = hasDescription;
		this.hasAttribute = hasAttribute;
		this.hasCriticality = hasCriticality;
		this.hasGuarantees = hasGuarantees;
		this.hasGuaranteeRoles = hasGuaranteeRoles;
		this.maps = parseCSV();
		this.roleDescriptions = maps.getRoleDescriptions();
		this.roleAttributes = maps.getRoleAttributes();
		this.criticalities = maps.getCriticalities();
		this.guarantees = maps.getGuarantees();
		this.guaranteeRoles = maps.getGuaranteeRoles();
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

			Map<String, String> roleDescriptions = new HashMap<>();
			Map<String, List<String>> roleAttributes = new HashMap<>();
			Map<String, String> criticalities = new HashMap<>();
			Map<String, List<String>> guarantees = new HashMap<>();
			Map<String, List<String>> guaranteeRoles = new HashMap<>();
			
			for (String[] line : reader) {
				String[] roleNames = line[roleColumnNumber].split(multiValueSeparator);
				
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
					if(criticality.equals("")) { // if no criticality is specified, 0 is set by default
						criticality = "0";
					}
					if(criticality.length() > 1) {
						LOG.error("The criticality in the CSV file cannot be multivalued! Error in line: " + line);
						throw new IllegalArgumentException("The criticality in the CSV file cannot be multivalued!" + line);
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
				
				for (String roleName : roleNames) {
					if (!StringUtils.isEmpty(roleName)) {
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
					}
				}
			}
			
			Maps maps = new Maps(roleDescriptions, roleAttributes, criticalities, guarantees, guaranteeRoles);
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
	private Map<String, List<String>> roleAttributes;
	private Map<String, String> criticalities;
	private Map<String, List<String>> guarantees;
	private Map<String, List<String>> guaranteeRoles;
	
	public Maps(Map<String, String> roleDescriptions, Map<String, List<String>> roleAttributes,
			Map<String, String> criticalities, Map<String, List<String>> guarantees,
			Map<String, List<String>> guaranteeRoles) {
		super();
		this.roleDescriptions = roleDescriptions;
		this.roleAttributes = roleAttributes;
		this.criticalities = criticalities;
		this.guarantees = guarantees;
		this.guaranteeRoles = guaranteeRoles;
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
 }

