/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.launchConfigurations;

 
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILaunchMode;
import org.eclipse.debug.internal.core.IConfigurationElementConstants;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.LaunchConfigurationTabExtension;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.ILaunchConfigurationTab;
import org.eclipse.debug.ui.ILaunchConfigurationTabGroup;

import com.ibm.icu.text.MessageFormat;

/**
 * Manages contributed launch configuration tabs
 */ 
public class LaunchConfigurationPresentationManager {
	
	/**
	 * The singleton launch configuration presentation manager
	 */
	private static LaunchConfigurationPresentationManager fgDefault;
			
	/**
	 * Collection of launch configuration tab group extensions
	 * defined in plug-in xml. Entries are keyed by launch
	 * configuration type identifier (<code>String</code>),
	 * and entires are tables of launch modes (<code>String</code>)
	 * to <code>LaunchConfigurationTabGroupExtension</code>. "*" is
	 * used to represent the default tab group (i.e. unspecified mode).
	 */
	private Hashtable fTabGroupExtensions;	
	
	/**
	 * contributed tabs are stored by the tab group id that they contribute to.
	 * each entry is a futher <code>Hashtable</code> consisting of the corrseponding
	 * <code>LaunchConfigurationTabExtension</code> objects for each contributed tab stored by their 
	 * id
	 * 
	 * @since 3.3
	 */
	private Hashtable fContributedTabs;
			
	/**
	 * Constructs the singleton launch configuration presentation
	 * manager.
	 */
	private LaunchConfigurationPresentationManager() {
		fgDefault = this;
		initializeTabGroupExtensions();
		initializeContributedTabExtensions();
	}

	/**
	 * Returns the launch configuration presentation manager
	 */
	public static LaunchConfigurationPresentationManager getDefault() {
		if (fgDefault == null) {
			fgDefault = new LaunchConfigurationPresentationManager();
		}
		return fgDefault;
	}
		
	/**
	 * Creates launch configuration tab group extensions for each extension
	 * defined in XML, and adds them to the table of tab group extensions.
	 */
	private void initializeTabGroupExtensions() {
		fTabGroupExtensions = new Hashtable();
		IExtensionPoint extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(DebugUIPlugin.getUniqueIdentifier(), IDebugUIConstants.EXTENSION_POINT_LAUNCH_CONFIGURATION_TAB_GROUPS);
		IConfigurationElement[] groups = extensionPoint.getConfigurationElements();
		LaunchConfigurationTabGroupExtension group = null;
		String typeId = null;
		Map map = null;
		Set modes = null;
		for (int i = 0; i < groups.length; i++) {
			group = new LaunchConfigurationTabGroupExtension(groups[i]);
			typeId = group.getTypeIdentifier();
			map = (Map)fTabGroupExtensions.get(typeId);
			if (map == null) {
				map = new Hashtable();
				fTabGroupExtensions.put(typeId, map);
			}
			modes = group.getModes();
			if (modes == null) {
				// default tabs - store with "*"
				map.put("*", group); //$NON-NLS-1$
			} else {
				// store per mode
				Iterator iterator = modes.iterator();
				while (iterator.hasNext()) {
					map.put(iterator.next(), group);
				}
			}
		}
	}	
	
	/**
	 * This method is used to collect all of the contributed tabs defined by the <code>launchConfigurationTabs</code>
	 * extension point
	 * <p>
	 * <strong>EXPERIMENTAL</strong>. This method has been added as
	 * part of a work in progress. There is no guarantee that this API will
	 * remain unchanged during the 3.3 release cycle. Please do not use this API
	 * without consulting with the Platform/Debug team.
	 * </p>
	 * @since 3.3
	 */
	private void initializeContributedTabExtensions() {
		fContributedTabs = new Hashtable();
		IExtensionPoint epoint = Platform.getExtensionRegistry().getExtensionPoint(DebugUIPlugin.getUniqueIdentifier(), IDebugUIConstants.EXTENSION_POINT_LAUNCH_TABS);
		IConfigurationElement[] elements = epoint.getConfigurationElements();
		LaunchConfigurationTabExtension tab = null;
		Hashtable element = null;
		for(int i = 0; i < elements.length; i++) {
			tab = new LaunchConfigurationTabExtension(elements[i]);
			element = (Hashtable) fContributedTabs.get(tab.getTabGroupId());
			if(element == null) {
				element = new Hashtable();
				element.put(tab.getIdentifier(), tab);
				fContributedTabs.put(tab.getTabGroupId(), element);
			}
			element.put(tab.getIdentifier(), tab);
		}
	}
	
	/**
	 * Returns the tab group for the given launch configuration type and mode.
	 * 
	 * @param type launch configuration type
	 * @param mode launch mode
	 * @return the tab group for the given type of launch configuration, or <code>null</code> if none
	 * @exception CoreException if an exception occurs creating the group
	 */
	public ILaunchConfigurationTabGroup getTabGroup(ILaunchConfigurationType type, String mode) throws CoreException {
		LaunchConfigurationTabGroupExtension ext = getExtension(type.getIdentifier(), mode);
		if (ext == null) {
			IStatus status = new Status(IStatus.ERROR, IDebugUIConstants.PLUGIN_ID, IDebugUIConstants.INTERNAL_ERROR,
			 MessageFormat.format(LaunchConfigurationsMessages.LaunchConfigurationPresentationManager_No_tab_group_defined_for_launch_configuration_type__0__3, (new String[] {type.getIdentifier()})), null);  
			 throw new CoreException(status);
		} 
		return new LaunchConfigurationTabGroupWrapper(ext.newTabGroup(), ext.getIdentifier());		
	}
	
	/**
	 * Returns the listing of <code>ILaunchConfigurationTab</code>s for the specified <code>ILaunchConfigurationTabGroup</code>.
	 * If no tabs are found for the specified id an empty array is returned, never <code>null</code>
	 * @param groupid
	 * @return the <code>ILaunchConfigurationTab</code>s for the specified <code>ILaunchConfigurationTabGroup</code> id,
	 * or an empty array if none are found
	 * 
	 * <p>
	 * <strong>EXPERIMENTAL</strong>. This method has been added as
	 * part of a work in progress. There is no guarantee that this API will
	 * remain unchanged during the 3.3 release cycle. Please do not use this API
	 * without consulting with the Platform/Debug team.
	 * </p>
	 * @since 3.3
	 */
	public ILaunchConfigurationTab[] createContributedTabs(String groupid) {
		Hashtable tabs = (Hashtable) fContributedTabs.get(groupid);
		ArrayList list = new ArrayList();
		if(tabs != null) {
			LaunchConfigurationTabExtension ext = null;
			for(Iterator iter = tabs.keySet().iterator(); iter.hasNext();) {
				ext = (LaunchConfigurationTabExtension) tabs.get(iter.next());
				if(ext != null) {
					list.add(ext.getTab());
				}
			}
		}
		return (ILaunchConfigurationTab[]) list.toArray(new ILaunchConfigurationTab[list.size()]);
	}
	
	/**
	 * Returns the launch tab group extension for the given type and mode, or
	 * <code>null</code> if none
	 * 
	 * @param type launch configuration type identifier
	 * @param mode launch mode identifier
	 * @return launch tab group extension or <code>null</code>
	 */
	protected LaunchConfigurationTabGroupExtension getExtension(String type, String mode) {
		// get the map for the config type
		Map map = (Map)fTabGroupExtensions.get(type);
		if (map != null) {
			// try the specific mode
			Object extension = map.get(mode);
			if (extension == null) {
				// get the default tabs
				extension = map.get("*"); //$NON-NLS-1$
			}
			return (LaunchConfigurationTabGroupExtension)extension;
		}
		return null;
	}
	
	/**
	 * Returns the identifier of the help context that is associated with the
	 * specified launch configuration type and mode, or <code>null</code> if none.
	 * 
	 * @param type launch config type
	 * @param mode launch mode
	 * @return the identifier for the help context associated with the given
	 * type of launch configuration, or <code>null</code>
	 * @exception CoreException if an exception occurs creating the group
	 * @since 2.1
	 */
	public String getHelpContext(ILaunchConfigurationType type, String mode) throws CoreException {
		LaunchConfigurationTabGroupExtension ext = getExtension(type.getIdentifier(), mode);
		if (ext == null) {
			IStatus status = new Status(IStatus.ERROR, IDebugUIConstants.PLUGIN_ID, IDebugUIConstants.INTERNAL_ERROR,
			 MessageFormat.format(LaunchConfigurationsMessages.LaunchConfigurationPresentationManager_No_tab_group_defined_for_launch_configuration_type__0__3, (new String[] {type.getIdentifier()})), null); 
			 throw new CoreException(status);
		} 
		return ext.getHelpContextId();		
	}
	
	/**
	 * Returns the description of the given configuration type
	 * in the specified mode or <code>null</code> if none.
	 * 
	 * @param configType the config type
	 * @param mode the launch mode
	 * @return the description of the given configuration type, possible <code>null</code>
	 */
	public String getDescription(ILaunchConfigurationType configType, String mode) {
		LaunchConfigurationPresentationManager manager = LaunchConfigurationPresentationManager.getDefault();
		LaunchConfigurationTabGroupExtension extension = manager.getExtension(configType.getAttribute(IConfigurationElementConstants.ID), mode);
		return (extension != null ? extension.getDescription(mode) : null);
	}	
	
	/**
	 * Returns a sorted list of launch mode names corresponding to the given identifiers.
	 * 
	 * @param modes set of launch mode identifiers
	 * @return sorted list of launch mode names
	 */
	public List getLaunchModeNames(Set modes) {
		List names = new ArrayList();
		Iterator iterator = modes.iterator();
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		while (iterator.hasNext()) {
			String id = (String) iterator.next();
			ILaunchMode mode = manager.getLaunchMode(id);
			if (mode == null) {
				names.add(id);
			} else {
				names.add(DebugUIPlugin.removeAccelerators(mode.getLabel()));
			}
		}
		Collections.sort(names);
		return names;
	}
}

