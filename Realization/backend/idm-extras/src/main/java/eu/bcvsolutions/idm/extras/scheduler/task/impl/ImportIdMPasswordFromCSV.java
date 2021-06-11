package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import com.google.common.collect.Lists;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmPasswordDto;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmPasswordService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.entity.IdmPassword_;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static eu.bcvsolutions.idm.extras.scheduler.task.impl.ImportCSVUserContractRolesTaskExecutor.PARAM_USERNAME_COLUMN_NAME;

/**
 * Task which will create/update {@link eu.bcvsolutions.idm.core.model.entity.IdmPassword}
 *
 * @author Peter Štrunc <peter.strunc@bcvsolutions.eu>
 */

@Component(ImportIdMPasswordFromCSV.TASK_NAME)
@Description("Task which will create/update IdmPassword from CSV file")
public class ImportIdMPasswordFromCSV extends AbstractCsvImportTask {

	public static final String TASK_NAME = "extras-import-password";

	public static final Logger LOG = LoggerFactory.getLogger(ImportIdMPasswordFromCSV.class);
	public static final String PARAM_DATE_FORMAT = "dateFormatColumn";
	public static final String PARAM_USERNAME_COLUMN_NAME = "usernameColumn";


	private String usernameColumnName;
	private String dateFormat;

	@Autowired
	private IdmPasswordService passwordService;

	@Autowired
	private IdmIdentityService identityService;


	@Override
	public String getName() {
		return TASK_NAME;
	}

	@Override
	protected void processRecords(List<CSVRecord> records) {
		records.forEach(record -> {
			LOG.info("Processing row [{}]", record);
			final String login = record.get(usernameColumnName);

			if (StringUtils.isEmpty(login)) {
				LOG.info(String.format("No identity found for username %s - skipping", login));
				return;
			}

			IdmPasswordDto currentPassword = passwordService.findOneByIdentity(login);
			final boolean create = currentPassword == null;

			if (create) {
				LOG.info("Creating new password for {}", login);
				IdmIdentityDto identityDto = identityService.getByUsername(login);
				if (identityDto == null) {
					LOG.info(String.format("No identity found for username %s - skipping", login));
					return;
				}
				//
				currentPassword = new IdmPasswordDto();
				currentPassword.setIdentity(identityDto.getId());
			} else {
				LOG.info("Updating existing password for {}", login);
			}

			if (record.isMapped(IdmPassword_.PASSWORD)) {
				currentPassword.setPassword(record.get(IdmPassword_.PASSWORD));
			}

			if (record.isMapped(IdmPassword_.BLOCK_LOGIN_DATE)) {
				currentPassword.setBlockLoginDate(formatDate(record.get(IdmPassword_.BLOCK_LOGIN_DATE)));
			}

			if (record.isMapped(IdmPassword_.LAST_SUCCESSFUL_LOGIN)) {
				currentPassword.setLastSuccessfulLogin(formatDate(record.get(IdmPassword_.LAST_SUCCESSFUL_LOGIN)));
			}

			if (record.isMapped(IdmPassword_.MUST_CHANGE)) {
				currentPassword.setMustChange(Boolean.parseBoolean(record.get(IdmPassword_.MUST_CHANGE)));
			}

			if (record.isMapped(IdmPassword_.PASSWORD_NEVER_EXPIRES)) {
				currentPassword.setPasswordNeverExpires(Boolean.parseBoolean(record.get(IdmPassword_.PASSWORD_NEVER_EXPIRES)));
			}

			if (record.isMapped(IdmPassword_.VALID_FROM)) {
				currentPassword.setValidFrom(formatDate(record.get(IdmPassword_.VALID_FROM)).toLocalDate());
			}

			if (record.isMapped(IdmPassword_.VALID_TILL)) {
				currentPassword.setValidTill(formatDate(record.get(IdmPassword_.VALID_TILL)).toLocalDate());
			}

			if (record.isMapped(IdmPassword_.UNSUCCESSFUL_ATTEMPTS)) {
				currentPassword.setUnsuccessfulAttempts(Integer.parseInt(record.get(IdmPassword_.UNSUCCESSFUL_ATTEMPTS)));
			}

			/* only in IdM 11
			if (record.isMapped(IdmPassword_.VERIFICATION_SECRET)) {
				currentPassword.setVerificationSecret(record.get(IdmPassword_.VERIFICATION_SECRET));
			} */

			currentPassword = passwordService.save(currentPassword);

			LOG.info("Password for identity [{}] saved", login);

			++this.counter;
			this.logItemProcessed(currentPassword, taskCompleted(String.format("Password was %s", create ? "created" : "updated"),
					ExtrasResultCode.IMPORT_PASSWORD_EXECUTED));
		});
	}

	@Override
	protected void processOneDynamicAttribute(String namePrefix, String name, String valuePrefix, String value, boolean isEav) {
		// No dynamic attributes supported for IdmPassword
	}

	private ZonedDateTime formatDate(String csvDate) {
		return LocalDate.parse(csvDate, DateTimeFormatter.ofPattern(dateFormat))
				.atStartOfDay(ZoneId.systemDefault());
	}

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		usernameColumnName = getParameterConverter().toString(properties, PARAM_USERNAME_COLUMN_NAME);
		dateFormat = getParameterConverter().toString(properties, PARAM_DATE_FORMAT);
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_DATE_FORMAT, dateFormat);
		props.put(PARAM_USERNAME_COLUMN_NAME, usernameColumnName);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();

		IdmFormAttributeDto dateFormatAttr = new IdmFormAttributeDto(PARAM_DATE_FORMAT, PARAM_DATE_FORMAT,
				PersistentType.SHORTTEXT);
		dateFormatAttr.setRequired(true);
		dateFormatAttr.setPlaceholder("Format of dates in the input file");

		IdmFormAttributeDto usernameAttr = new IdmFormAttributeDto(PARAM_USERNAME_COLUMN_NAME, PARAM_USERNAME_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		usernameAttr.setRequired(true);
		usernameAttr.setPlaceholder("Name of column with identities usernames");

		formAttributes.addAll(Lists.newArrayList(dateFormatAttr, usernameAttr));
		return formAttributes;
	}

}
