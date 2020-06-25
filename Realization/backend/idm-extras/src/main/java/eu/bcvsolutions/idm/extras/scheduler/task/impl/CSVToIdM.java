package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

import com.google.common.collect.ImmutableMap;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.ICSVParser;

import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * @author Petr Hanák
 * @author Tomáš Doischer
 *
 */
public class CSVToIdM {

	private InputStream attachmentData;

	private static final String ROLES_ATTRIBUTE = "roleNames";
	private static final String ROLE_CODES_ATTRIBUTE = "roleCodes";
	private static final String DESCRIPTION_ATTRIBUTE = "description";
	private static final String ATTRIBUTE_ATTRIBUTE = "attribute";
	private static final String MEMBER_OF_ATTRIBUTE_VALUE_ATTRIBUTE = "memberOfAttributeValue";
	private static final String CRITICALITY_ATTRIBUTE = "criticality";
	private static final String GUARANTEE_ATTRIBUTE = "guarantees";
	private static final String GUARANTEE_TYPE_ATTRIBUTE = "guaranteeTypes";
	private static final String GUARANTEE_ROLE_ATTRIBUTE = "guaranteeRoles";
	private static final String GUARANTEE_ROLE_TYPE_ATTRIBUTE = "guaranteeRoleType";
	private static final String CATALOGUE_ATTRIBUTE = "catalogues";
	private static final String SUBROLE_ATTRIBUTE = "subroles";

	private String columnSeparator;
	private String multiValueSeparator;
	private String encoding;

	private Map<String, String> roleDescriptions;
	private Map<String, List<String>> roleAttributes;
	private Map<String, String> criticalities;
	private Map<String, List<String>> guarantees;
	private Map<String, String> guaranteeTypes;
	private Map<String, List<String>> guaranteeRoles;
	private Map<String, String> guaranteeRoleTypes;
	private Map<String, List<String>> catalogues;
	private Map<String, List<String>> subRoles;
	private Map<String, String> roleCodes;
	private Map<String, String> memberOfValues;

	String[] header = new String[0];

	public Map<String, String> getRoleDescriptions() {
		return roleDescriptions;
	}

	public Map<String, String> getRoleCodes() {
		return roleCodes;
	}

	public Map<String, List<String>> getRoleAttributes() {
		return roleAttributes;
	}

	public Map<String, String> getCriticalities() {
		return criticalities;
	}

	public Map<String, List<String>> getGuarantees() {
		return guarantees;
	}

	public Map<String, List<String>> getGuaranteeRoles() {
		return guaranteeRoles;
	}

	public Map<String, String> getGuaranteeTypes() {
		return guaranteeTypes;
	}

	public Map<String, String> getGuaranteeRoleTypes() {
		return guaranteeRoleTypes;
	}

	public Map<String, List<String>> getCatalogues() {
		return catalogues;
	}

	public Map<String, List<String>> getSubRoles() {
		return subRoles;
	}

	public Map<String, String> getMemberOfValues() {
		return memberOfValues;
	}

	public CSVToIdM(InputStream attachmentData, String rolesColumnName, String roleCodeColumnName,
			String descriptionColumnName, String attributeColumnName, String criticalityColumnName,
			String guaranteeColumnName, String guaranteeTypeColumnName, String guaranteeRoleColumnName,
			String guaranteeRoleTypeColumnName, String catalogueColumnName, String subRoleColumnName,
			String columnSeparator, String multiValueSeparator, Boolean hasDescription, Boolean hasAttribute,
			Boolean hasCriticality, Boolean hasGuarantees, Boolean hasGuaranteeTypes, Boolean hasGuaranteeRoles,
			Boolean hasGuaranteeRoleTypes, Boolean hasCatalogues, Boolean hasSubRoles, Boolean hasRoleCodes,
			String encoding, String memberOfAttributeValueColumnName, Boolean hasMemberOfValue) {

		List<Attribute> attributes = new ArrayList<>();
		attributes.add(new Attribute(ROLES_ATTRIBUTE, rolesColumnName, Boolean.TRUE, Boolean.TRUE));
		attributes.add(new Attribute(ROLE_CODES_ATTRIBUTE, roleCodeColumnName, Boolean.FALSE, hasRoleCodes));
		attributes.add(new Attribute(DESCRIPTION_ATTRIBUTE, descriptionColumnName, Boolean.FALSE, hasDescription));
		attributes.add(new Attribute(ATTRIBUTE_ATTRIBUTE, attributeColumnName, Boolean.TRUE, hasAttribute));
		attributes.add(new Attribute(MEMBER_OF_ATTRIBUTE_VALUE_ATTRIBUTE, memberOfAttributeValueColumnName,
				Boolean.FALSE, hasMemberOfValue));
		attributes.add(new Attribute(CRITICALITY_ATTRIBUTE, criticalityColumnName, Boolean.FALSE, hasCriticality));
		attributes.add(new Attribute(GUARANTEE_ATTRIBUTE, guaranteeColumnName, Boolean.TRUE, hasGuarantees));
		attributes.add(
				new Attribute(GUARANTEE_TYPE_ATTRIBUTE, guaranteeTypeColumnName, Boolean.FALSE, hasGuaranteeTypes));
		attributes
				.add(new Attribute(GUARANTEE_ROLE_ATTRIBUTE, guaranteeRoleColumnName, Boolean.TRUE, hasGuaranteeRoles));
		attributes.add(new Attribute(GUARANTEE_ROLE_TYPE_ATTRIBUTE, guaranteeRoleTypeColumnName, Boolean.FALSE,
				hasGuaranteeRoleTypes));
		attributes.add(new Attribute(CATALOGUE_ATTRIBUTE, catalogueColumnName, Boolean.TRUE, hasCatalogues));
		attributes.add(new Attribute(SUBROLE_ATTRIBUTE, subRoleColumnName, Boolean.TRUE, hasSubRoles));

		this.attachmentData = attachmentData;
		this.columnSeparator = columnSeparator;
		this.multiValueSeparator = multiValueSeparator;
		this.encoding = encoding;

		List<Attribute> parsedAttributes = parseCSV(attributes);

		this.roleCodes = getValueOfAttribute(ROLE_CODES_ATTRIBUTE, parsedAttributes);
		this.roleDescriptions = getValueOfAttribute(DESCRIPTION_ATTRIBUTE, parsedAttributes);
		this.roleAttributes = getValuesOfAttribute(ATTRIBUTE_ATTRIBUTE, parsedAttributes);
		this.criticalities = getValueOfAttribute(CRITICALITY_ATTRIBUTE, parsedAttributes);
		this.guarantees = getValuesOfAttribute(GUARANTEE_ATTRIBUTE, parsedAttributes);
		this.guaranteeTypes = getValueOfAttribute(GUARANTEE_TYPE_ATTRIBUTE, parsedAttributes);
		this.guaranteeRoles = getValuesOfAttribute(GUARANTEE_ROLE_ATTRIBUTE, parsedAttributes);
		this.guaranteeRoleTypes = getValueOfAttribute(GUARANTEE_ROLE_TYPE_ATTRIBUTE, parsedAttributes);
		this.catalogues = getValuesOfAttribute(CATALOGUE_ATTRIBUTE, parsedAttributes);
		this.subRoles = getValuesOfAttribute(SUBROLE_ATTRIBUTE, parsedAttributes);
		this.memberOfValues = getValueOfAttribute(MEMBER_OF_ATTRIBUTE_VALUE_ATTRIBUTE, parsedAttributes);
	}

	/**
	 * Parse CSV file - read selected CSV column and return list of roles with
	 * description
	 * 
	 * @return
	 */
	private List<Attribute> parseCSV(List<Attribute> attributesAndColumnNames) {
		CSVParser parser = new CSVParserBuilder().withEscapeChar(ICSVParser.DEFAULT_ESCAPE_CHARACTER).withQuoteChar('"')
				.withSeparator(columnSeparator.charAt(0)).build();

		try (BufferedReader br = new BufferedReader(new InputStreamReader(attachmentData,
				StringUtils.isEmpty(encoding) ? Charset.defaultCharset() : Charset.forName(encoding)));
				CSVReader reader = new CSVReaderBuilder(br).withCSVParser(parser).build();
		) {
			header = reader.readNext();

			// get column numbers
			for (Attribute attribute : attributesAndColumnNames) {
				if (attribute.hasValue()) {
					attribute
							.setColumnNumber(findColumnNumber(header, attribute.getColumnName(), attribute.hasValue()));
				}
			}

			for (String[] line : reader) {
				for (Attribute attribute : attributesAndColumnNames) {
					attribute = setValue(attribute, line, attributesAndColumnNames);
				}
			}

			return attributesAndColumnNames;
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Finds number of column
	 * 
	 * @param header
	 * @param columnName
	 * @return
	 */
	private int findColumnNumber(String[] header, String columnName, boolean hasAttribute) {
		if (!hasAttribute) {
			return -1;
		}

		int counterHeader = 0;
		for (String item : header) {
			if (item.equals(columnName)) {
				return counterHeader;
			}
			counterHeader++;
		}
		throw new ResultCodeException(ExtrasResultCode.COLUMN_NOT_FOUND, ImmutableMap.of("column name", columnName));
	}

	/**
	 * Return attribute value
	 * 
	 * @param columnNumber
	 * @param hasAttribute
	 * @param line
	 * @return
	 */
	private String getAttributeValue(int columnNumber, boolean hasAttribute, String[] line) {
		if (hasAttribute) {
			return line[columnNumber];
		} else {
			return "";
		}
	}

	/**
	 * Return multiple attribute values
	 * 
	 * @param columnNumber
	 * @param hasAttribute
	 * @param line
	 * @return
	 */
	private List<String> getAttributeValues(int columnNumber, boolean hasAttribute, String[] line) {
		if (hasAttribute) {
			String[] v = line[columnNumber].split(multiValueSeparator);
			return Arrays.asList(v);
		} else {
			return new ArrayList<>();
		}
	}

	private Attribute setValue(Attribute attribute, String[] line, List<Attribute> attributesAndColumnNames) {
		List<String> roleNames = getRoleName(line, attributesAndColumnNames);

		if (!attribute.getAttributeName().equals(ROLES_ATTRIBUTE)) {
			for (String roleName : roleNames) {
				if (attribute.isMultivalued()) {
					List<String> values = new ArrayList<>();
					if (attribute.getColumnNumber() != null && attribute.getColumnNumber() != -1) {
						values = getAttributeValues(attribute.getColumnNumber(), attribute.hasValue(), line);
					}

					if (values != null && roleName != null) {
						attribute.addValues(roleName, values);
					}
				} else {
					String value = "";
					if (attribute.getColumnNumber() != null && attribute.getColumnNumber() != -1) {
						value = getAttributeValue(attribute.getColumnNumber(), attribute.hasValue(), line);
					}

					if (value != null && roleName != null) {
						attribute.addValue(roleName, value);
					}
				}
			}
		}

		return attribute;
	}

	private List<String> getRoleName(String[] line, List<Attribute> attributesAndColumnNames) {
		Attribute roleName = null;

		for (Attribute attribute : attributesAndColumnNames) {
			if (attribute.getAttributeName().equals(ROLES_ATTRIBUTE)) {
				roleName = attribute;
				break;
			}
		}

		if (roleName != null) {
			return getAttributeValues(roleName.getColumnNumber(), roleName.hasValue(), line);
		} else {
			return new ArrayList<>();
		}
	}

	private Map<String, List<String>> getValuesOfAttribute(String attributeName,
			List<Attribute> attributesAndColumnNames) {
		for (Attribute attribute : attributesAndColumnNames) {
			if (attribute.getAttributeName().equals(attributeName)) {
				return attribute.getValues();
			}
		}

		return new HashMap<>();
	}

	private Map<String, String> getValueOfAttribute(String attributeName, List<Attribute> attributesAndColumnNames) {
		for (Attribute attribute : attributesAndColumnNames) {
			if (attribute.getAttributeName().equals(attributeName)) {
				return attribute.getValue();
			}
		}

		return new HashMap<>();
	}
}

class Attribute {
	private String attributeName;
	private String columnName;
	private Boolean multivalued;
	private Boolean hasValue;
	private Integer columnNumber;
	private Map<String, String> value;
	private Map<String, List<String>> values;

	public Attribute(String attributeName, String columnName, Boolean multivalued, Boolean hasValue) {
		this.attributeName = attributeName;
		this.columnName = columnName;
		this.multivalued = multivalued;
		this.hasValue = hasValue;

		this.value = new HashMap<>();
		this.values = new HashMap<>();
	}

	public void setColumnNumber(Integer columnNumber) {
		this.columnNumber = columnNumber;
	}

	public String getColumnName() {
		return columnName;
	}

	public String getAttributeName() {
		return attributeName;
	}

	public Boolean isMultivalued() {
		return multivalued;
	}

	public void setMultivalued(Boolean multivalued) {
		this.multivalued = multivalued;
	}

	public Boolean hasValue() {
		return hasValue;
	}

	public Integer getColumnNumber() {
		return columnNumber;
	}

	public Map<String, String> getValue() {
		return value;
	}

	public Map<String, List<String>> getValues() {
		return values;
	}

	public void addValue(String roleName, String foundValue) {
		value.put(roleName, foundValue);
	}

	public void addValues(String roleName, List<String> foundValues) {
		values.put(roleName, foundValues);
	}
}
