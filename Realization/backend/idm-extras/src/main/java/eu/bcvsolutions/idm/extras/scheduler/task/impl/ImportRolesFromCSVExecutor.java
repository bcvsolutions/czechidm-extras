package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemFilter;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.acc.service.impl.DefaultSysRoleSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmRoleService;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * This LRT imports all roles from csv to IDM
 *
 * @author Marek Klement
 */
@Component
@Description("Get all roles on mapping - system and import from CSV to IDM")
public class ImportRolesFromCSVExecutor extends AbstractSchedulableTaskExecutor<Boolean> {

	private static final Logger LOG = LoggerFactory.getLogger(ImportRolesFromCSVExecutor.class);

	public static final String PARAM_CSV_FILE_PATH = "Path to file";
	public static final String PARAM_SYSTEM_NAME = "System name";
	public static final String PARAM_ROLES_COLUMN_NAME = "Column with name";

	private String pathToFile;
	private String systemName;
	private String rolesColumnName;

	@Autowired
	private SysSystemService sysSystemService;
	@Autowired
	private DefaultIdmRoleService roleService;
	@Autowired
	private DefaultSysRoleSystemService roleSystemService;
	@Autowired
	private SysSystemMappingService mappingService;

	@Override
	public Boolean process() {
		// todo same code as in ImportFromCSVToSystemExecutor - Abstract class can be created
		LOG.debug("Start process");
		File fl = new File(pathToFile);
		if (!fl.canRead()) {
			throw new ResultCodeException(ExtrasResultCode.IMPORT_CANT_READ_FILE_PATH,
					ImmutableMap.of("path", pathToFile));
		}
		SysSystemFilter systemFilter = new SysSystemFilter();
		systemFilter.setCodeableIdentifier(systemName);
		List<SysSystemDto> systems = sysSystemService.find(systemFilter, null).getContent();
		//
		if (systems.isEmpty()) {
			throw new ResultCodeException(ExtrasResultCode.SYSTEM_NAME_NOT_FOUND,
					ImmutableMap.of("system", systemName));
		}
		SysSystemDto system = systems.get(0);
		parseCSV(system);
		//
		return true;
	}

	/**
	 * this method parse CSV file - mostly read and work with it
	 * @param system
	 * @return
	 */
	private boolean parseCSV(SysSystemDto system) {
		CSVParser parser = new CSVParserBuilder().withEscapeChar(CSVParser.DEFAULT_ESCAPE_CHARACTER).withQuoteChar('"')
				// todo maybe setting of separator
				.withSeparator(';').build();
		CSVReader reader = null;
		try {
			reader = new CSVReaderBuilder(new FileReader(pathToFile)).withCSVParser(parser).build();
			String[] header = reader.readNext();
			//
			int roleNumber = findRoleNumber(header);
			if(roleNumber==-1){
				// todo ResultCodeException
				throw new IllegalArgumentException("Column not found!");
			}
			for (String[] line : reader) {
				createRoles(line, system, roleNumber);
			}
		} catch (IOException e){
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
		return true;
	}

	/**
	 * finds number of column we need for name and code of role
	 * @param header
	 * @return
	 */
	private int findRoleNumber(String[] header) {
		int counterHeader = 0;
		for (String item : header){
			if(item.equals(rolesColumnName)){
				return counterHeader;
			}
			counterHeader++;
		}
		return -1;
	}

	/**
	 * creates roles from column given in input
	 * @param line
	 * @param system
	 * @param roleNumber
	 * @return
	 */
	private boolean createRoles(String[] line, SysSystemDto system, int roleNumber) {
		String lineRole = line[roleNumber];
		// todo - do we split multivaued attribute by '\n'?
		String[] split = lineRole.split("/n");
		for (String role : split) {
			IdmRoleDto roleDto = new IdmRoleDto();
			// code should not contain spaces
			roleDto.setCode(role.replace(' ', '_'));
			roleDto.setName(role);
			roleDto.setPriority(0);
			roleDto = roleService.save(roleDto);
			SysRoleSystemDto roleSystem = new SysRoleSystemDto();
			roleSystem.setSystem(system.getId());
			roleSystem.setRole(roleDto.getId());
			// get system mapping
			SysSystemMappingDto systemMapping = getSystemMapping(system);
			roleSystem.setSystemMapping(systemMapping.getId());
			roleSystemService.save(roleSystem);
		}
		return true;
	}

	/**
	 * get system mapping from system
	 * todo maybe change setting for mapping
	 * @param system
	 * @return
	 */
	private SysSystemMappingDto getSystemMapping(SysSystemDto system) {
		List<SysSystemMappingDto> bySystem = mappingService.findBySystem(system, SystemOperationType.SYNCHRONIZATION, SystemEntityType.IDENTITY);
		return bySystem.get(0);
	}

	/**
	 * Schema id and path to file are retrieved for following usage.
	 *
	 * @param properties map of properties given
	 */
	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		pathToFile = getParameterConverter().toString(properties, PARAM_CSV_FILE_PATH);
		systemName = getParameterConverter().toString(properties, PARAM_SYSTEM_NAME);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
	}

	/**
	 * We need to add synchronization id - needed as identifier of synchronization which we will export
	 * and path to file where we will export data.
	 *
	 * @return all parameters
	 */
	@Override
	public List<String> getPropertyNames() {
		LOG.debug("Start getPropertyName");
		List<String> params = super.getPropertyNames();
		params.add(PARAM_CSV_FILE_PATH);
		params.add(PARAM_SYSTEM_NAME);
		params.add(PARAM_ROLES_COLUMN_NAME);
		return params;
	}
}
