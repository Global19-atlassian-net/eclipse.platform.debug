package org.eclipse.ui.externaltools.internal.view;

/**********************************************************************
Copyright (c) 2002 IBM Corp. and others. All rights reserved.
This file is made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
 
Contributors:
**********************************************************************/

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.externaltools.internal.core.ExternalToolsPlugin;
import org.eclipse.ui.externaltools.internal.core.ToolMessages;
import org.eclipse.ui.externaltools.internal.ui.IHelpContextIds;
import org.eclipse.ui.externaltools.model.ExternalTool;
import org.eclipse.ui.help.WorkbenchHelp;

/**
 * Action that will copy the currently selected
 * external tool in the view to the clipboard.
 */
public class CopyExternalToolAction extends Action {
	private IWorkbenchPage page;
	private ExternalTool selectedTool;

	/**
	 * Create an action to copy the selected external
	 * tool to the clipboard
	 */
	public CopyExternalToolAction(IWorkbenchPage page) {
		super();
		this.page = page;
		setText(ToolMessages.getString("CopyExternalToolAction.text")); //$NON-NLS-1$
		setToolTipText(ToolMessages.getString("CopyExternalToolAction.toolTip")); //$NON-NLS-1$
		setHoverImageDescriptor(ExternalToolsPlugin.getDefault().getImageDescriptor("icons/full/clcl16/copy_tool.gif")); //$NON-NLS-1$
		setImageDescriptor(ExternalToolsPlugin.getDefault().getImageDescriptor("icons/full/elcl16/copy_tool.gif")); //$NON-NLS-1$
		setDisabledImageDescriptor(ExternalToolsPlugin.getDefault().getImageDescriptor("icons/full/dlcl16/copy_tool.gif")); //$NON-NLS-1$
		WorkbenchHelp.setHelp(this, IHelpContextIds.COPY_TOOL_ACTION);
	}

	/**
	 * Returns the selected external tool.
	 */
	public ExternalTool getSelectedTool() {
		return selectedTool;
	}
	
	/* (non-Javadoc)
	 * Method declared on Action.
	 */
	public void run() {
		org.eclipse.jface.dialogs.MessageDialog.openInformation(page.getWorkbenchWindow().getShell(), "Action", "This action is not yet implemented");
	}

	/**
	 * Sets the selected external tool.
	 */
	public void setSelectedTool(ExternalTool tool) {
		selectedTool = tool;
		setEnabled(tool != null);
	}
}
