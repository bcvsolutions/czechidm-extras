package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.FileReader;
import java.io.IOException;
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
	
	private String pathToFile;
	private String rolesColumnName;
	private String descriptionColumnName;
	private String columnSeparator;
	private String multiValueSeparator;
	private Boolean hasDescription;

	public CSVToIdM(String pathToFile, String rolesColumnName, String descriptionColumnName, String columnSeparator, String multiValueSeparator, Boolean hasDescription) {
		this.pathToFile = pathToFile;
		this.rolesColumnName = rolesColumnName;
		this.descriptionColumnName = descriptionColumnName;
		this.columnSeparator = columnSeparator;
		this.multiValueSeparator = multiValueSeparator;
		this.hasDescription = hasDescription;
		// TODO Auto-generated constructor stub
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
			reader = new CSVReaderBuilder(new FileReader(pathToFile)).withCSVParser(parser).build();
			String[] header = reader.readNext();
			// find number of column with role name
			int roleColumnNumber = findColumnNumber(header, rolesColumnName);
			if (roleColumnNumber == -1) {
				throw new ResultCodeException(ExtrasResultCode.COLUMN_NOT_FOUND, ImmutableMap.of("column name", rolesColumnName));
			}
			//	find number of column with description name
			int descriptionColumnNumber = -1;
			if (hasDescription) {
				descriptionColumnNumber = findColumnNumber(header, descriptionColumnName);
				if (descriptionColumnNumber == -1) {
					throw new ResultCodeException(ExtrasResultCode.COLUMN_NOT_FOUND, ImmutableMap.of("column name", descriptionColumnName));
				}
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
						System.out.println(roleName);
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
		return -1;
	}
}
