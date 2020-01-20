package eu.bcvsolutions.idm.extras.config.domain;

import com.google.common.collect.Lists;
import eu.bcvsolutions.idm.core.api.config.domain.AbstractConfiguration;
import eu.bcvsolutions.idm.extras.util.ExtrasUtils;
import org.springframework.stereotype.Component;

import java.util.List;

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
