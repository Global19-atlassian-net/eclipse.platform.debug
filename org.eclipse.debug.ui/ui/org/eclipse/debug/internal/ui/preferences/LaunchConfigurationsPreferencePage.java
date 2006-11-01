/*******************************************************************************
 * Copyright (c) 2004, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.preferences;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.internal.ui.SWTUtil;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchGroupExtension;
import org.eclipse.debug.internal.ui.launchConfigurations.MultiLaunchGroupFilter;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.wizard.ProgressMonitorPart;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.eclipse.ui.model.AdaptableList;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchViewerComparator;

/**
 * Provides the Launch Configuration preference page to the Run/Debug preferences
 * 
 * This page allows users to set filtering options as well as perform migration tasks.
 * This class is not intended to be subclasssed 
 * @since 3.2
 */
public class LaunchConfigurationsPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	/**
	 * @since 3.2
	 */
	class LaunchConfigurationMigrationSelectionDialog extends ListSelectionDialog {
		
		private String SETTINGS_ID = IDebugUIConstants.PLUGIN_ID + ".MIGRATION_SELECTION_DIALOG"; //$NON-NLS-1$
		
		public LaunchConfigurationMigrationSelectionDialog(Shell parentShell, Object input, IStructuredContentProvider contentProvider, ILabelProvider labelProvider, String message) {
			super(parentShell, input, contentProvider, labelProvider, message);
			setShellStyle(getShellStyle() | SWT.RESIZE);
		}

		protected IDialogSettings getDialogBoundsSettings() {
			IDialogSettings settings = DebugUIPlugin.getDefault().getDialogSettings();
			IDialogSettings section = settings.getSection(SETTINGS_ID);
			if (section == null) {
				section = settings.addNewSection(SETTINGS_ID);
			} 
			return section;
		}
	}
	
	/**
	 * Content provider for the launch configuration type table
	 */
	class TableContentProvider implements IStructuredContentProvider {

		public Object[] getElements(Object inputElement) {
			return getLaunchConfigurationTypes();
		}

		public void dispose() {}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}
	}
	
	/**
	 * describes the debug launch group
	 */
	private static final String DEBUG_LAUNCH_GROUP = "org.eclipse.debug.ui.launchGroup.debug"; //$NON-NLS-1$
	
	/**
	 * describes the external tools launch group
	 */
	private static final String EXT_BUILDER_GROUP = "org.eclipse.ui.externaltools.launchGroup"; //$NON-NLS-1$
	
	/**
	 * to monitor the progress of the migration process
	 */
	private ProgressMonitorPart fMonitor;
	
	/**
	 * the migrate now button
	 */
	private Button fMigrateNow;
	
	/**
	 * a list of the field editors
	 */
	private List fFieldEditors;
	
	/**
	 * The table for the launch configuration types
	 */
	private Table fTable;
	
	/**
	 * Constructor
	 */
	public LaunchConfigurationsPreferencePage() {
		super();
		setPreferenceStore(DebugUIPlugin.getDefault().getPreferenceStore());
		setTitle(DebugPreferencesMessages.LaunchConfigurationsPreferencePage_1);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.PreferencePage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		super.createControl(parent);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IDebugHelpContextIds.LAUNCH_CONFIGURATION_PREFERENCE_PAGE);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.PreferencePage#createContents(org.eclipse.swt.widgets.Composite)
	 */
	protected Control createContents(Composite parent) {
		fFieldEditors = new ArrayList();
		Composite comp = SWTUtil.createComposite(parent, parent.getFont(), 1, 1, GridData.FILL_HORIZONTAL);
		//filtering options
		Group group = createGroupComposite(comp, DebugPreferencesMessages.LaunchingPreferencePage_32);
		Composite spacer = SWTUtil.createComposite(group, group.getFont(), 1, 1, GridData.FILL_HORIZONTAL);
		FieldEditor edit = new BooleanFieldEditor(IInternalDebugUIConstants.PREF_FILTER_LAUNCH_CLOSED, DebugPreferencesMessages.LaunchingPreferencePage_33, SWT.NONE, spacer);
		fFieldEditors.add(edit);
		edit = new BooleanFieldEditor(IInternalDebugUIConstants.PREF_FILTER_LAUNCH_DELETED, DebugPreferencesMessages.LaunchingPreferencePage_34, SWT.NONE, spacer);
		fFieldEditors.add(edit);
		edit = new BooleanFieldEditor(IInternalDebugUIConstants.PREF_FILTER_WORKING_SETS, DebugPreferencesMessages.LaunchConfigurationsPreferencePage_3, SWT.NONE, spacer);
		fFieldEditors.add(edit);
		edit = new RadioGroupFieldEditor(IInternalDebugUIConstants.PREF_DELETE_CONFIGS_ON_PROJECT_DELETE, DebugPreferencesMessages.LaunchConfigurationsPreferencePage_2, 3,  
				 new String[][] {{DebugPreferencesMessages.LaunchingPreferencePage_3, MessageDialogWithToggle.ALWAYS}, 
				 {DebugPreferencesMessages.LaunchingPreferencePage_4, MessageDialogWithToggle.NEVER},
				 {DebugPreferencesMessages.LaunchingPreferencePage_5, MessageDialogWithToggle.PROMPT}}, 
				 comp,
				 true);
		fFieldEditors.add(edit);
		
		//add table options
		createTypeFiltering(group);
		
		//migration
		group = createGroupComposite(comp, DebugPreferencesMessages.LaunchingPreferencePage_35);
		Label label = new Label(group, SWT.LEFT | SWT.WRAP);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.widthHint = 350;
		label.setLayoutData(gd);
		label.setText(DebugPreferencesMessages.LaunchingPreferencePage_26);
		label.setFont(parent.getFont());
		fMigrateNow = SWTUtil.createPushButton(group, DebugPreferencesMessages.LaunchingPreferencePage_27, null);
		gd = new GridData(SWT.BEGINNING);

		fMigrateNow.setLayoutData(gd);
		fMigrateNow.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {}
			public void widgetSelected(SelectionEvent e) {
				handleMigrateNowSelected();
			}			
		});
	
		//init field editors
		initFieldEditors();
		fTable.setEnabled(getPreferenceStore().getBoolean(IInternalDebugUIConstants.PREF_FILTER_LAUNCH_TYPES));
		return comp;
	}
	
	/**
	 * @param parent the parent to add this composite to
	 * @return the new composite with the type selection table in it
	 */
	private Composite createTypeFiltering(Composite parent) {
		Composite comp = SWTUtil.createComposite(parent, parent.getFont(), 1, 1, GridData.FILL_HORIZONTAL);
		BooleanFieldEditor2 editor = new BooleanFieldEditor2(IInternalDebugUIConstants.PREF_FILTER_LAUNCH_TYPES, DebugPreferencesMessages.LaunchConfigurationsPreferencePage_0, SWT.NONE, comp);
		editor.setPropertyChangeListener(new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				boolean newvalue = false;
				if(event.getNewValue() instanceof Boolean) {
					newvalue = ((Boolean)event.getNewValue()).booleanValue();
				}
				else {
					newvalue = Boolean.valueOf(event.getNewValue().toString()).booleanValue();
				}
				if(newvalue) {
					fTable.setEnabled(true);
				}
				else {
					fTable.setEnabled(false);
				}
			}	
		});
		fFieldEditors.add(editor);
		fTable = new Table(comp, SWT.CHECK | SWT.BORDER);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.heightHint = 155;
		fTable.setLayoutData(gd);
		CheckboxTableViewer tviewer = new CheckboxTableViewer(fTable);
		tviewer.setLabelProvider(DebugUITools.newDebugModelPresentation());
		tviewer.setContentProvider(new TableContentProvider());
		tviewer.setComparator(new WorkbenchViewerComparator());
		LaunchGroupExtension[] groups = new LaunchGroupExtension[] {
				DebugUIPlugin.getDefault().getLaunchConfigurationManager().getLaunchGroup(DEBUG_LAUNCH_GROUP),
				DebugUIPlugin.getDefault().getLaunchConfigurationManager().getLaunchGroup(EXT_BUILDER_GROUP)};
		tviewer.addFilter(new MultiLaunchGroupFilter(groups));
		tviewer.setInput(getLaunchConfigurationTypes());
		fTable.setFont(parent.getFont());
		return comp;
	}
	
	/**
	 * Creates a standard grouping for this pref page
	 * @param parent the parent to add the group to
	 * @param title text the test for the group
	 * @return the new group
	 * @since 3.2
	 */
	private Group createGroupComposite(Composite parent, String text) {
		Group group = new Group(parent, SWT.NONE);
		group.setLayout(new GridLayout());
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalIndent = 0;
		gd.verticalIndent = 0;
		group.setLayoutData(gd);
		group.setText(text);
		group.setFont(parent.getFont());
		return group;
	}
	
	/**
	 * returns the launch configuration types
	 * @return the launch configuration types
	 */
	private ILaunchConfigurationType[] getLaunchConfigurationTypes() {
		return DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurationTypes();
	}
	
	/**
	 * handles the Migrate button being clicked
	 *
	 * @since 3.2
	 */
	private void handleMigrateNowSelected() {
		try {
			ILaunchManager lmanager = DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfiguration[] configurations = lmanager.getMigrationCandidates();
			if(configurations.length == 0) {
				MessageDialog.openInformation(getShell(), DebugPreferencesMessages.LaunchingPreferencePage_29, DebugPreferencesMessages.LaunchingPreferencePage_30);
				return;
			}
			LaunchConfigurationMigrationSelectionDialog listd = new LaunchConfigurationMigrationSelectionDialog(getShell(), 
																new AdaptableList(configurations), 
																new WorkbenchContentProvider(),	
																DebugUITools.newDebugModelPresentation(), 
																DebugPreferencesMessages.LaunchingPreferencePage_0);
			listd.setTitle(DebugPreferencesMessages.LaunchingPreferencePage_28);
			listd.setInitialSelections(configurations);
			if(listd.open() == IDialogConstants.OK_ID) {
				fMonitor = new ProgressMonitorPart(fMigrateNow.getParent(), new GridLayout());
				Object[] objs = listd.getResult();
				fMonitor.beginTask(DebugPreferencesMessages.LaunchingPreferencePage_31, objs.length);
				for(int i = 0; i < objs.length; i++) {
					if(objs[i] instanceof ILaunchConfiguration) {
						((ILaunchConfiguration)objs[i]).migrate();
					}
					fMonitor.worked(i);
				}
				fMonitor.done();
				fMonitor.dispose();
			}
		}
		catch (CoreException e) {DebugUIPlugin.log(e);}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {}

	/**
	 * Initializes the field editors to their values
	 * @since 3.2
	 */
	private void initFieldEditors() {
		FieldEditor editor;
		for(int i = 0; i < fFieldEditors.size(); i++) {
			editor = (FieldEditor)fFieldEditors.get(i);
			editor.setPreferenceStore(getPreferenceStore());
			editor.load();
		}
		//restore the tables' checked state
		String[] types = getPreferenceStore().getString(IInternalDebugUIConstants.PREF_FILTER_TYPE_LIST).split("\\,"); //$NON-NLS-1$
		TableItem[] items = fTable.getItems();
		ILaunchConfigurationType type;
		for(int i = 0; i < types.length; i++) {
			for(int j = 0; j < items.length; j++) {
				type = (ILaunchConfigurationType)items[j].getData();
				if(type.getIdentifier().equals(types[i])) {
					items[j].setChecked(true);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.PreferencePage#performDefaults()
	 */
	protected void performDefaults() {
		FieldEditor editor = null;
		for(int i = 0; i < fFieldEditors.size(); i++) {
			editor = (FieldEditor)fFieldEditors.get(i);
			editor.loadDefault();
			if(editor instanceof BooleanFieldEditor2) {
				fTable.setEnabled(((BooleanFieldEditor2)editor).getBooleanValue());
			}
		}
		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.PreferencePage#performOk()
	 */
	public boolean performOk() {
		//save field editors
		for(int i = 0; i < fFieldEditors.size(); i++) {
			((FieldEditor)fFieldEditors.get(i)).store();
		}
		//save table
		String types = ""; //$NON-NLS-1$
		TableItem[] items = fTable.getItems();
		ILaunchConfigurationType type;
		for(int i = 0; i < items.length; i++) {
			if(items[i].getChecked()) {
				type = (ILaunchConfigurationType)items[i].getData();
				types += type.getIdentifier()+","; //$NON-NLS-1$
			}
		}
		getPreferenceStore().setValue(IInternalDebugUIConstants.PREF_FILTER_TYPE_LIST, types);
		return super.performOk();
	}
}
