package org.eclipse.ui.externaltools.dialog;

/**********************************************************************
Copyright (c) 2002 IBM Corp. and others. All rights reserved.
This file is made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
 
Contributors:
**********************************************************************/

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.externaltools.group.ExternalToolMainGroup;
import org.eclipse.ui.externaltools.group.ExternalToolOptionGroup;
import org.eclipse.ui.externaltools.group.ExternalToolRefreshGroup;
import org.eclipse.ui.externaltools.internal.core.ExternalToolsPlugin;
import org.eclipse.ui.externaltools.internal.core.ToolMessages;
import org.eclipse.ui.externaltools.internal.registry.ExternalToolType;
import org.eclipse.ui.externaltools.internal.ui.IHelpContextIds;
import org.eclipse.ui.externaltools.model.IExternalToolConstants;
import org.eclipse.ui.externaltools.model.ToolUtil;

/**
 

/**
 * Abstract wizard to create new external tools of a specified type.
 * <p>
 * This class can be extended by clients.
 * </p>
 */
public abstract class ExternalToolNewWizard extends Wizard implements INewWizard {
	private ExternalToolType toolType;
	private IResource selectedResource;
	private IWorkbench workbench;
	protected ExternalToolMainGroup mainGroup;
	protected ExternalToolOptionGroup optionGroup;
	protected ExternalToolRefreshGroup refreshGroup;
	
	/**
	 * Creates the wizard for a new external tool
	 */
	public ExternalToolNewWizard(String toolTypeId) {
		super();
		setWindowTitle(ToolMessages.getString("ExternalToolNewWizard.shellTitle")); //$NON-NLS-1$
		setDefaultPageImageDescriptor(getDefaultImageDescriptor());
		setNeedsProgressMonitor(true);
		toolType = ExternalToolsPlugin.getDefault().getTypeRegistry().getToolType(toolTypeId);
	}
	
	/**
	 * Creates a wizard page to contain the external tool
	 * main group component and adds it to the wizard page
	 * list.
	 */
	protected void addMainPage() {
		createMainGroup();
		if (mainGroup == null)
			return;
		ExternalToolGroupWizardPage page;
		page = new ExternalToolGroupWizardPage("mainGroupPage", mainGroup, IHelpContextIds.TOOL_MAIN_WIZARD_PAGE);  //$NON-NLS-1$
		if (toolType != null) {
			page.setTitle(toolType.getName());
			page.setDescription(toolType.getDescription());
		}
		addPage(page);
	}
	
	/**
	 * Creates a wizard page to contain the external tool
	 * option group component and adds it to the wizard page
	 * list.
	 */
	protected void addOptionPage() {
		createOptionGroup();
		if (optionGroup == null)
			return;
		ExternalToolGroupWizardPage page;
		page = new ExternalToolGroupWizardPage("optionGroupPage", optionGroup, IHelpContextIds.TOOL_OPTION_WIZARD_PAGE);  //$NON-NLS-1$
		page.setTitle(ToolMessages.getString("ExternalToolNewWizard.optionPageTitle")); //$NON-NLS-1$
		page.setDescription(ToolMessages.getString("ExternalToolNewWizard.optionPageDescription")); //$NON-NLS-1$
		addPage(page);
	}
	
	/**
	 * Creates a wizard page to contain the external tool
	 * refresh scope group component and adds it to the wizard page
	 * list.
	 */
	protected void addRefreshPage() {
		createRefreshGroup();
		if (refreshGroup == null)
			return;
		ExternalToolGroupWizardPage page;
		page = new ExternalToolGroupWizardPage("refreshGroupPage", refreshGroup, IHelpContextIds.TOOL_REFRESH_WIZARD_PAGE);  //$NON-NLS-1$
		page.setTitle(ToolMessages.getString("ExternalToolNewWizard.refreshPageTitle")); //$NON-NLS-1$
		page.setDescription(ToolMessages.getString("ExternalToolNewWizard.refreshPageDescription")); //$NON-NLS-1$
		addPage(page);
	}
	
	/**
	 * Creates the main group and initializes it using
	 * the information from the selected resource.
	 */
	protected void createMainGroup() {
		if (mainGroup != null)
			return;
		mainGroup = new ExternalToolMainGroup();
		if (selectedResource != null) {
			String path = selectedResource.getFullPath().toString();
			String loc = ToolUtil.buildVariableTag(
				IExternalToolConstants.VAR_RESOURCE_LOC, 
				path);
			mainGroup.setInitialLocation(loc);
			mainGroup.setInitialName(path);
		}
	}

	/**
	 * Creates the option group and initializes it.
	 */
	protected void createOptionGroup() {
		if (optionGroup != null)
			return;
		optionGroup = new ExternalToolOptionGroup();
	}

	/**
	 * Creates the refresh scope group and initializes it.
	 */
	protected void createRefreshGroup() {
		if (refreshGroup != null)
			return;
		refreshGroup = new ExternalToolRefreshGroup();
	}

	/* (non-Javadoc)
	 * Method declared on IWizard.
	 */
	public void dispose() {
		super.dispose();
		selectedResource = null;
	}

	/**
	 * Returns the selected resource.
	 */
	protected final IResource getSelectedResource() {
		return selectedResource;
	}

	/**
	 * Returns the default image descriptor for this wizard.
	 * 
	 * @return the image descriptor or <code>null</code> if
	 * 		none required.
	 */
	protected abstract ImageDescriptor getDefaultImageDescriptor();
	
	/**
	 * Returns the workbench.
	 */
	protected final IWorkbench getWorkbench() {
		return workbench;
	}

	/* (non-Javadoc)
	 * Method declared on IWorkbenchWizard.
	 */
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.workbench = workbench;
		
		Object sel = selection.getFirstElement();
		if (sel != null) {
			if (sel instanceof IResource)
				selectedResource = (IResource) sel;
			else if (sel instanceof IAdaptable)
				selectedResource = (IResource)((IAdaptable)sel).getAdapter(IResource.class);
		}
	}
}
