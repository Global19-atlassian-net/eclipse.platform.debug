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
import java.net.URL;

import org.eclipse.ant.core.AntCorePlugin;
import org.eclipse.jface.dialogs.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.externaltools.internal.model.*;
import org.eclipse.ui.externaltools.internal.model.ToolMessages;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jface.dialogs.Dialog;

/**
 * Dialog to prompt the user to add a custom ant task or type.
 */
public class AddTaskDialog extends Dialog {
	private String title;
	private String description;

	//task/type attributes
	private String taskName;
	private String className;
	private URL library;

	//widgets
	private Button okButton;
	private Text nameField;
	private Text classField;
	private Combo libraryField;

	private URL[] libraryUrls;

	/**
	 * Creates a new task dialog with the given shell and title.
	 */
	protected AddTaskDialog(
		Shell parentShell,
		String title,
		String description) {
		super(parentShell);
		this.title = title;
		this.description = description;
	}
	/**
	 * @see Window#configureShell(Shell)
	 */
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(title);
		WorkbenchHelp.setHelp(newShell, IHelpContextIds.ADD_TASK_DIALOG);
	}
	/**
	 * @see Dialog#createButtonsForButtonBar(Composite)
	 */
	protected void createButtonsForButtonBar(Composite parent) {
		okButton =
			createButton(
				parent,
				IDialogConstants.OK_ID,
				IDialogConstants.OK_LABEL,
				true);
		createButton(
			parent,
			IDialogConstants.CANCEL_ID,
			IDialogConstants.CANCEL_LABEL,
			false);
		updateEnablement();
	}
	/**
	 * @see Dialog#createDialogArea(Composite)
	 */
	protected Control createDialogArea(Composite parent) {
		Composite dialogArea = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 10;
		layout.marginWidth = 10;
		dialogArea.setLayout(layout);

		Label label = new Label(dialogArea, SWT.NONE);
		label.setText(description);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.horizontalSpan = 2;
		label.setLayoutData(data);

		label = new Label(dialogArea, SWT.NONE);
		label.setText(ToolMessages.getString("AddTaskDialog.name")); //$NON-NLS-1$;
		nameField = new Text(dialogArea, SWT.BORDER);
		data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = IDialogConstants.ENTRY_FIELD_WIDTH;
		nameField.setLayoutData(data);
		nameField.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateEnablement();
			}
		});

		label = new Label(dialogArea, SWT.NONE);
		label.setText(ToolMessages.getString("AddTaskDialog.class")); //$NON-NLS-1$;
		classField = new Text(dialogArea, SWT.BORDER);
		data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = IDialogConstants.ENTRY_FIELD_WIDTH;
		classField.setLayoutData(data);
		classField.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateEnablement();
			}
		});

		label = new Label(dialogArea, SWT.NONE);
		label.setText(ToolMessages.getString("AddTaskDialog.library")); //$NON-NLS-1$;
		libraryField = new Combo(dialogArea, SWT.READ_ONLY | SWT.BORDER);
		data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = IDialogConstants.ENTRY_FIELD_WIDTH;
		libraryField.setLayoutData(data);
		libraryField.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateEnablement();
			}
		});

		//populate library combo and select input library
		libraryUrls =
			AntCorePlugin.getPlugin().getPreferences().getCustomURLs();
		int selection = 0;
		for (int i = 0; i < libraryUrls.length; i++) {
			libraryField.add(libraryUrls[i].getFile());
			if (libraryUrls[i].equals(library))
				selection = i;
		}

		//intialize fields
		if (taskName != null)
			nameField.setText(taskName);
		if (className != null)
			classField.setText(className);
		if (libraryUrls.length >= 0)
			libraryField.select(selection);
		return dialogArea;
	}

	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	public URL getLibrary() {
		return library;
	}
	/**
	 * @see Dialog#okPressed()
	 */
	protected void okPressed() {
		className = classField.getText();
		taskName = nameField.getText();
		int selection = libraryField.getSelectionIndex();
		if (selection >= 0)
			library = libraryUrls[selection];
		super.okPressed();
	}
	public void setLibrary(URL library) {
		this.library = library;
	}
	public String getTaskName() {
		return taskName;
	}
	public void setTaskName(String taskName) {
		this.taskName = taskName;
	}
	private void updateEnablement() {
		if (okButton != null)
			okButton.setEnabled(
				nameField.getText().length() > 0
					&& classField.getText().length() > 0
					&& libraryField.getSelectionIndex() >= 0);
	}
}