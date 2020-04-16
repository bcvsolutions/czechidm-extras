package eu.bcvsolutions.idm.extras.config.domain;

import java.util.List;

import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.config.domain.AbstractConfiguration;
import eu.bcvsolutions.idm.extras.util.ExtrasUtils;

/**
 * Extras configuration - implementation
 * 
 * @author peter.sourek@bcvsolutions.eu
 *
 */
@Component("extrasConfiguration")
public class DefaultExtrasConfiguration 
		extends AbstractConfiguration
		implements ExtrasConfiguration {

	@Override
	public List<String> getTitlesAfter() {
		String value = getConfigurationService().getValue(EXTRAS_TITLES_AFTER);
		if (value == null) {
			return ExtrasUtils.TITLES_AFTER;
		}

		return splitStringByComma(value);
	}

	@Override
	public List<String> getTitlesBefore() {
		String value = getConfigurationService().getValue(EXTRAS_TITLES_BEFORE);
		if (value == null) {
			return ExtrasUtils.TITLES_BEFORE;
		}

		return splitStringByComma(value);
	}

	@Override
	public String getCrossAdCodeList() {
		return getConfigurationService().getValue(EXTRAS_CROSS_AD_CODE_LIST);
	}

	/**
	 * Return string split by comma
	 *
	 * @return
	 */
	private List<String> splitStringByComma(String string) {
		if (string != null && !string.isEmpty()) {
			return Lists.newArrayList(string.split(","));
		}

		return Lists.newArrayList();
	}
}
