package org.eclipse.ui.externaltools.internal.ui;

/**********************************************************************
Copyright (c) 2002 IBM Corp. and others.
All rights reserved. � This program and the accompanying materials
are made available under the terms of the Common Public License v0.5
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v05.html
�
Contributors:
**********************************************************************/
import java.net.*;
import java.util.ArrayList;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.externaltools.internal.model.*;
import org.eclipse.ui.help.WorkbenchHelp;

/**
 * First page of the run Ant wizard. Allows the user to pick
 * the targets, supply extra arguments, and decide to show
 * output to the console.
 */
public class AntLaunchWizardPage extends WizardPage {
	private static final int SIZING_SELECTION_WIDGET_HEIGHT = 200;
	private static final int SIZING_SELECTION_WIDGET_WIDTH = 200;

	private AntTargetList targetList;
	private String initialTargets[];
	private String initialArguments;
	private boolean initialDisplayLog = true;
	private ArrayList selectedTargets = new ArrayList();
	
	private CheckboxTableViewer listViewer;
	private AntTargetLabelProvider labelProvider = new AntTargetLabelProvider();
	private Button showLog;
	private Text argumentsField;

	public AntLaunchWizardPage(AntTargetList targetList) {
		super("AntScriptPage"); //$NON-NLS-1$;
		this.targetList = targetList;
		setTitle(ToolMessages.getString("AntLaunchWizard.dialogTitle")); //$NON-NLS-1$;
		setDescription(ToolMessages.getString("AntLaunchWizard.dialogDescription")); //$NON-NLS-1$;
		setImageDescriptor(getImageDescriptor("icons/full/wizban/ant_wiz.gif")); //$NON-NLS-1$;
	}
	
	/**
	 * Returns the image descriptor for the banner
	 */
	private ImageDescriptor getImageDescriptor(String relativePath) {
		try {
			URL installURL = ExternalToolsPlugin.getDefault().getDescriptor().getInstallURL();
			URL url = new URL(installURL, relativePath);
			return ImageDescriptor.createFromURL(url);
		} catch (MalformedURLException e) {
			return null;
		}
	}
	
	/* (non-Javadoc)
	 * Method declared on IWizardPage.
	 */
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));

		// The list of targets
		Label label = new Label(composite, SWT.NONE);
		label.setText(ToolMessages.getString("AntLaunchWizardPage.targetLabel")); //$NON-NLS-1$;

		listViewer = CheckboxTableViewer.newCheckList(composite, SWT.BORDER);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.heightHint = SIZING_SELECTION_WIDGET_HEIGHT;
		data.widthHint = SIZING_SELECTION_WIDGET_WIDTH;
		listViewer.getTable().setLayoutData(data);
		listViewer.setSorter(new ViewerSorter() {
			public int compare(Viewer viewer, Object o1, Object o2) {
				return ((String)o1).compareTo((String) o2);
			}
		});
		if (targetList.getDefaultTarget() != null)
			labelProvider.setDefaultTargetName(targetList.getDefaultTarget());
		listViewer.setLabelProvider(labelProvider);
		listViewer.setContentProvider(new AntTargetContentProvider());
		listViewer.setInput(targetList);

		// The arguments field
		label = new Label(composite, SWT.NONE);
		label.setText(ToolMessages.getString("AntLaunchWizardPage.argsLabel")); //$NON-NLS-1$;
		
		argumentsField = new Text(composite, SWT.BORDER);
		argumentsField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		argumentsField.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				validatePageComplete();
			}
		});

		// The show log option
		showLog = new Button(composite, SWT.CHECK);
		showLog.setText(ToolMessages.getString("AntLaunchWizardPage.showLogLabel")); //$NON-NLS-1$;
		
		// Setup initial field values
		if (initialArguments != null)
			argumentsField.setText(initialArguments);
		showLog.setSelection(initialDisplayLog);
		selectInitialTargets();
		
		validatePageComplete();

		listViewer.addCheckStateListener(new TargetCheckListener());
		listViewer.refresh();
		argumentsField.setFocus();
		
		setControl(composite);

		WorkbenchHelp.setHelp(composite, IHelpContextIds.ANT_LAUNCH_WIZARD_PAGE);
	}
	
	/**
	 * Returns the arguments that the user has entered
	 * to run the ant file.
	 * 
	 * @return String the arguments
	 */
	public String getArguments() {
		return argumentsField.getText().trim();
	}
	
	/**
	 * Returns the targets selected by the user
	 */
	public String[] getSelectedTargets() {
		String[] names = new String[selectedTargets.size()];
		selectedTargets.toArray(names);
		return names;
	}
	
	/**
	 * Returns whether the users wants messages from running
	 * the tool displayed in the console
	 */
	public boolean getShowLog() {
		return showLog.getSelection();
	}

	/**
	 * Setup the initial selected targets in the viewer
	 */	
	private void selectInitialTargets() {
		if (initialTargets != null && initialTargets.length > 0) {
			String[] targets = targetList.getTargets();
			for (int i = 0; i < initialTargets.length; i++) {
				for (int j = 0; j < targets.length; j++) {
					if (targets[j].equals(initialTargets[i])) {
						String target = targets[j];
						listViewer.setChecked(target, true);
						selectedTargets.add(target);
						break;
					}
				}
			}
		} else {
			String target = targetList.getDefaultTarget();
			if (target != null) {
				listViewer.setChecked(target, true);
				selectedTargets.add(target);
			}
		}
		
		labelProvider.setSelectedTargets(selectedTargets);
	}
	
	/**
	 * Sets the initial contents of the target list field.
	 * Ignored if controls already created.
	 */
	public void setInitialTargets(String value[]) {
		initialTargets = value;
	}
	
	/**
	 * Sets the initial contents of the arguments text field.
	 * Ignored if controls already created.
	 */
	public void setInitialArguments(String value) {
		initialArguments = value;
	}
	
	/**
	 * Sets the initial contents of the display to log option field.
	 * Ignored if controls already created.
	 */
	public void setInitialDisplayLog(boolean value) {
		initialDisplayLog = value;
	}
	
	/**
	 * Validates the page is complete
	 */
	private void validatePageComplete() {
		setPageComplete(selectedTargets.size() > 0 || getArguments().length() > 0);
	}
	
	
	/**
	 * Inner class for checkbox listener
	 */
	private class TargetCheckListener implements ICheckStateListener {
		public void checkStateChanged(CheckStateChangedEvent e) {
			String checkedTarget = (String) e.getElement();
			if (e.getChecked())
				selectedTargets.add(checkedTarget);
			else
				selectedTargets.remove(checkedTarget);
	
			labelProvider.setSelectedTargets(selectedTargets);
			listViewer.refresh();
			validatePageComplete();
		}
	}
}