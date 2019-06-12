package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
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
	private String columnSeparator;
	private String multiValueSeparator;
	private Boolean hasDescription;
	
	private Map<String, String> roleDescriptions;
	
	public Map<String, String> getRoleDescriptions() {
		return roleDescriptions;
	}
	
	public void setRoleDescriptions(Map<String, String> roleDescriptions) {
		this.roleDescriptions = roleDescriptions;
	}

	public CSVToIdM(InputStream attachmentData, String rolesColumnName, String descriptionColumnName, String columnSeparator, String multiValueSeparator, Boolean hasDescription) {
		this.attachmentData = attachmentData;
		this.rolesColumnName = rolesColumnName;
		this.descriptionColumnName = descriptionColumnName;
		this.columnSeparator = columnSeparator;
		this.multiValueSeparator = multiValueSeparator;
		this.hasDescription = hasDescription;
		this.roleDescriptions = parseCSV();
	}
	
	/**
	 * Parse CSV file
	 * - read selected CSV column and return list of roles with description
	 * 
	 * @return
	 */
	private Map<String, String> parseCSV() {
		CSVParser parser = new CSVParserBuilder().withEscapeChar(CSVParser.DEFAULT_ESCAPE_CHARACTER).withQuoteChar('"')
				.withSeparator(columnSeparator.charAt(0)).build();
		CSVReader reader = null;
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(attachmentData));
			reader = new CSVReaderBuilder(br).withCSVParser(parser).build();
			
			String[] header = reader.readNext();
			// find number of column with role name
			int roleColumnNumber = findColumnNumber(header, rolesColumnName);
			//	find number of column with description name
			int descriptionColumnNumber = -1;
			if (hasDescription) {
				descriptionColumnNumber = findColumnNumber(header, descriptionColumnName);
			}

			Map<String, String> roleDescriptions = new HashMap<>();
			for (String[] line : reader) {
				String[] roleNames = line[roleColumnNumber].split(multiValueSeparator);
				String description;
				if (hasDescription) {
					description = line[descriptionColumnNumber];
				} else {
					description = "";
				}
				for (String roleName : roleNames) {
					if (!StringUtils.isEmpty(roleName)) {
						roleDescriptions.put(roleName, description);
					}
				}
			}
			return roleDescriptions;
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
