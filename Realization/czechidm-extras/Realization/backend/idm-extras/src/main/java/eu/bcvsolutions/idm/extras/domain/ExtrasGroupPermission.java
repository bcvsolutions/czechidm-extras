package eu.bcvsolutions.idm.extras.domain;

import java.util.Arrays;
import java.util.List;

import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;

/**
 * Aggregate base permission. Name can't contain character '_' - its used for joining to authority name.
 * 
 * @author peter.sourek@bcvsolutions.eu
 *
 */
public enum ExtrasGroupPermission implements GroupPermission {
	
	/*
	 * Define your group permission there and example permission you can remove
	 */
	EXAMPLEEXTRAS(
			IdmBasePermission.ADMIN);

	public static final String EXAMPLE_EXTRAS_ADMIN = "EXAMPLEEXTRAS" + BasePermission.SEPARATOR + "ADMIN";

	private final List<BasePermission> permissions;
	
	private ExtrasGroupPermission(BasePermission... permissions) {
		this.permissions = Arrays.asList(permissions);
	}
	
	@Override
	public List<BasePermission> getPermissions() {		
		return permissions;
	}
	
	@Override
	public String getName() {
		return name();
	}	
	
	@Override
	public String getModule() {
		return ExtrasModuleDescriptor.MODULE_ID;
	}
}
