/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.ui;

 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchConfigurationManager;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchConfigurationsMessages;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchGroupExtension;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchHistory;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.WorkbenchEncoding;
import org.eclipse.ui.dialogs.ContainerSelectionDialog;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.ide.IDEEncoding;

/**
 * Launch configuration tab used to specify the location a launch configuration
 * is stored in, whether it should appear in the favorites list, and perspective
 * switching behavior for an associated launch.
 * <p>
 * Clients may instantiate this class. This class is not intended to be subclassed.
 * </p>
 * @since 2.0
 */
public class CommonTab extends AbstractLaunchConfigurationTab {
		
	// Local/shared UI widgets
	private Button fLocalRadioButton;
	private Button fSharedRadioButton;
	
	// Shared location UI widgets
	private Text fSharedLocationText;
	private Button fSharedLocationButton;
	
	protected Button fLaunchInBackgroundButton;
	
	/**
	 * Check box list for specifying favorites
	 */
	private CheckboxTableViewer fFavoritesTable;
			
	/**
	 * Modify listener that simply updates the owning launch configuration dialog.
	 */
	private ModifyListener fBasicModifyListener = new ModifyListener() {
			public void modifyText(ModifyEvent evt) {
				updateLaunchConfigurationDialog();
			}
	};
	
    private Button fDefaultEncodingButton;
    private Button fAltEncodingButton;
    private Combo fEncodingCombo;
	
	/**
	 * @see ILaunchConfigurationTab#createControl(Composite)
	 */
	public void createControl(Composite parent) {		
		Composite comp = new Composite(parent, SWT.NONE);
		setControl(comp);
		WorkbenchHelp.setHelp(getControl(), IDebugHelpContextIds.LAUNCH_CONFIGURATION_DIALOG_COMMON_TAB);
		GridLayout topLayout = new GridLayout();
		comp.setLayout(topLayout);
		
		Group group = new Group(comp, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		group.setLayout(layout);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		group.setLayoutData(gd);
		group.setText(LaunchConfigurationsMessages.getString("CommonTab.0")); //$NON-NLS-1$
				
		setLocalRadioButton(new Button(group, SWT.RADIO));
		getLocalRadioButton().setText(LaunchConfigurationsMessages.getString("CommonTab.L&ocal_3")); //$NON-NLS-1$
		gd = new GridData();
		gd.horizontalSpan = 3;
		getLocalRadioButton().setLayoutData(gd);
		setSharedRadioButton(new Button(group, SWT.RADIO));
		getSharedRadioButton().setText(LaunchConfigurationsMessages.getString("CommonTab.S&hared_4")); //$NON-NLS-1$
		getSharedRadioButton().addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent evt) {
				handleSharedRadioButtonSelected();
			}
		});
		gd = new GridData();
		getSharedRadioButton().setLayoutData(gd);
				
		setSharedLocationText(new Text(group, SWT.SINGLE | SWT.BORDER));
		gd = new GridData(GridData.FILL_HORIZONTAL);
		getSharedLocationText().setLayoutData(gd);
		getSharedLocationText().addModifyListener(fBasicModifyListener);
		
		setSharedLocationButton(createPushButton(group, LaunchConfigurationsMessages.getString("CommonTab.&Browse_6"), null));	 //$NON-NLS-1$
		getSharedLocationButton().addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent evt) {
				handleSharedLocationButtonSelected();
			}
		});	

		getLocalRadioButton().setSelection(true);
		setSharedEnabled(false);

		createVerticalSpacer(comp, 1);
		
		Composite favComp = new Composite(comp, SWT.NONE);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		favComp.setLayoutData(gd);
		GridLayout favLayout = new GridLayout();
		favLayout.marginHeight = 0;
		favLayout.marginWidth = 0;
		favLayout.numColumns = 2;
		favLayout.makeColumnsEqualWidth = true;
		favComp.setLayout(favLayout);
		
		Label favLabel = new Label(favComp, SWT.HORIZONTAL | SWT.LEFT);
		favLabel.setText(LaunchConfigurationsMessages.getString("CommonTab.Display_in_favorites_menu__10")); //$NON-NLS-1$
		gd = new GridData(GridData.BEGINNING);
		gd.horizontalSpan = 2;
		favLabel.setLayoutData(gd);
		
		fFavoritesTable = CheckboxTableViewer.newCheckList(favComp, SWT.CHECK | SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION);
		Control table = fFavoritesTable.getControl();
		gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gd.horizontalSpan = 1;
		table.setLayoutData(gd);
		
		fFavoritesTable.setContentProvider(new FavoritesContentProvider());
		fFavoritesTable.setLabelProvider(new FavoritesLabelProvider());
		fFavoritesTable.addCheckStateListener(
			new ICheckStateListener() {
				/**
				 * @see org.eclipse.jface.viewers.ICheckStateListener#checkStateChanged(org.eclipse.jface.viewers.CheckStateChangedEvent)
				 */
				public void checkStateChanged(CheckStateChangedEvent event) {
					updateLaunchConfigurationDialog();
				}
			});
		
		createVerticalSpacer(comp, 1);
		addEncodingBlock(comp);
		
		createVerticalSpacer(comp, 1);
		createLaunchInBackgroundComponent(comp);
		
		Dialog.applyDialogFont(parent);
	}
	
	private void addEncodingBlock(Composite parent) {
	    List allEncodings = IDEEncoding.getIDEEncodings();
	    String defaultEncoding = WorkbenchEncoding.getWorkbenchDefaultEncoding();
	    
	    Group group = new Group(parent, SWT.NONE);
	    group.setText(LaunchConfigurationsMessages.getString("CommonTab.1"));  //$NON-NLS-1$
	    GridData gd = new GridData(SWT.BEGINNING, SWT.NORMAL, true, false);
	    gd.horizontalSpan = 2;
	    group.setLayoutData(gd);
	    group.setLayout(new GridLayout(2, false));
	    
	    fDefaultEncodingButton = createRadioButton(group, LaunchConfigurationsMessages.getString("CommonTab.2") + defaultEncoding + LaunchConfigurationsMessages.getString("CommonTab.3"));  //$NON-NLS-1$ //$NON-NLS-2$
	    gd = new GridData(SWT.BEGINNING, SWT.NORMAL, true, false);
	    gd.horizontalSpan = 2;
	    fDefaultEncodingButton.setLayoutData(gd);
	    
	    fAltEncodingButton = createRadioButton(group, LaunchConfigurationsMessages.getString("CommonTab.4"));  //$NON-NLS-1$
	    fAltEncodingButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
	    
	    fEncodingCombo = new Combo(group, SWT.READ_ONLY);
	    fEncodingCombo.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
	    fEncodingCombo.setItems((String[]) allEncodings.toArray(new String[0]));
	    
	    SelectionListener listener = new SelectionAdapter() {
	        public void widgetSelected(SelectionEvent e) {
	            updateLaunchConfigurationDialog();
	            fEncodingCombo.setEnabled(fAltEncodingButton.getSelection() == true);
	        }
	    };
	    fAltEncodingButton.addSelectionListener(listener);
	    fDefaultEncodingButton.addSelectionListener(listener);
	    fEncodingCombo.addSelectionListener(listener);
	}

	/**
	 * Creates the controls needed to edit the launch in background
	 * attribute of an external tool
	 *
	 * @param parent the composite to create the controls in
	 */
	protected void createLaunchInBackgroundComponent(Composite parent) {
		fLaunchInBackgroundButton = createCheckButton(parent, LaunchConfigurationsMessages.getString("CommonTab.10")); //$NON-NLS-1$
		GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data.horizontalSpan = 2;
		fLaunchInBackgroundButton.setLayoutData(data);
		fLaunchInBackgroundButton.setFont(parent.getFont());
		fLaunchInBackgroundButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateLaunchConfigurationDialog();
			}
		});
	}

	
	private void setSharedLocationButton(Button sharedLocationButton) {
		this.fSharedLocationButton = sharedLocationButton;
	}

	private Button getSharedLocationButton() {
		return fSharedLocationButton;
	}

	private void setSharedLocationText(Text sharedLocationText) {
		this.fSharedLocationText = sharedLocationText;
	}

	private Text getSharedLocationText() {
		return fSharedLocationText;
	}

 	private void setLocalRadioButton(Button button) {
 		fLocalRadioButton = button;
 	}
 	
 	private Button getLocalRadioButton() {
 		return fLocalRadioButton;
 	} 	
 	
 	private void setSharedRadioButton(Button button) {
 		fSharedRadioButton = button;
 	}
 	
 	private Button getSharedRadioButton() {
 		return fSharedRadioButton;
 	} 	

	private void handleSharedRadioButtonSelected() {
		setSharedEnabled(isShared());
		updateLaunchConfigurationDialog();
	}
	
	private void setSharedEnabled(boolean enable) {
		getSharedLocationText().setEnabled(enable);
		getSharedLocationButton().setEnabled(enable);
	}
	
	private boolean isShared() {
		return getSharedRadioButton().getSelection();
	}
	
	private void handleSharedLocationButtonSelected() {
		ContainerSelectionDialog dialog = new ContainerSelectionDialog(getShell(),
																	   getWorkspaceRoot(),
																	   false,
																	   LaunchConfigurationsMessages.getString("CommonTab.Select_a_location_for_the_launch_configuration_13")); //$NON-NLS-1$
		
		String currentContainerString = getSharedLocationText().getText();
		IContainer currentContainer = getContainer(currentContainerString);
		if (currentContainer != null) {
			IPath path = currentContainer.getFullPath();
			dialog.setInitialSelections(new Object[] {path});
		}
		
		dialog.showClosedProjects(false);
		dialog.open();
		Object[] results = dialog.getResult();		
		if ((results != null) && (results.length > 0) && (results[0] instanceof IPath)) {
			IPath path = (IPath)results[0];
			String containerName = path.toOSString();
			getSharedLocationText().setText(containerName);
		}		
	}
	
	private IContainer getContainer(String path) {
		Path containerPath = new Path(path);
		return (IContainer) getWorkspaceRoot().findMember(containerPath);
	}
	
	/**
	 * @see ILaunchConfigurationTab#initializeFrom(ILaunchConfiguration)
	 */
	public void initializeFrom(ILaunchConfiguration configuration) {	
		updateLocalSharedFromConfig(configuration);
		updateSharedLocationFromConfig(configuration);
		updateFavoritesFromConfig(configuration);
		updateLaunchInBackground(configuration);
		updateEncoding(configuration);
	}
	
	protected void updateLaunchInBackground(ILaunchConfiguration configuration) { 
		fLaunchInBackgroundButton.setSelection(isLaunchInBackground(configuration));
	}
	
	protected void updateEncoding(ILaunchConfiguration configuration) {
	    String encoding = null;
	    try {
	        encoding = configuration.getAttribute(IDebugUIConstants.ATTR_CONSOLE_ENCODING, (String)null);
        } catch (CoreException e) {
        }
        
        if (encoding != null) {
            fAltEncodingButton.setSelection(true);
            fDefaultEncodingButton.setSelection(false);
            fEncodingCombo.setText(encoding);
        } else {
            fDefaultEncodingButton.setSelection(true);
            fAltEncodingButton.setSelection(false);
        }
	}
	
	/**
	 * Returns whether the given configuration should be launched in the background.
	 * 
	 * @param configuration the configuration
	 * @return whether the configuration is configured to launch in the background
	 */
	public static boolean isLaunchInBackground(ILaunchConfiguration configuration) {
		boolean launchInBackground= true;
		try {
			launchInBackground= configuration.getAttribute(IDebugUIConstants.ATTR_LAUNCH_IN_BACKGROUND, true);
		} catch (CoreException ce) {
			DebugUIPlugin.log(ce);
		}
		return launchInBackground;
	}
	
	private void updateLocalSharedFromConfig(ILaunchConfiguration config) {
		boolean isShared = !config.isLocal();
		getSharedRadioButton().setSelection(isShared);
		getLocalRadioButton().setSelection(!isShared);
		setSharedEnabled(isShared);
	}
	
	private void updateSharedLocationFromConfig(ILaunchConfiguration config) {
		getSharedLocationText().setText(""); //$NON-NLS-1$
		IFile file = config.getFile();
		if (file != null) {
			IContainer parent = file.getParent();
			if (parent != null) {
				String containerName = parent.getFullPath().toOSString();
				getSharedLocationText().setText(containerName);
			}
		}
	}
		
	private void updateFavoritesFromConfig(ILaunchConfiguration config) {
		fFavoritesTable.setInput(config);
		fFavoritesTable.setCheckedElements(new Object[]{});
		try {
			List groups = config.getAttribute(IDebugUIConstants.ATTR_FAVORITE_GROUPS, new ArrayList());
			if (groups.isEmpty()) {
				// check old attributes for backwards compatible
				if (config.getAttribute(IDebugUIConstants.ATTR_DEBUG_FAVORITE, false)) {
					groups.add(IDebugUIConstants.ID_DEBUG_LAUNCH_GROUP);
				}
				if (config.getAttribute(IDebugUIConstants.ATTR_RUN_FAVORITE, false)) {
					groups.add(IDebugUIConstants.ID_RUN_LAUNCH_GROUP);
				}
			}
			if (!groups.isEmpty()) {
				List list = new ArrayList();
				Iterator iterator = groups.iterator();
				while (iterator.hasNext()) {
					String id = (String)iterator.next();
					LaunchGroupExtension extension = getLaunchConfigurationManager().getLaunchGroup(id);
					list.add(extension);
				}
				fFavoritesTable.setCheckedElements(list.toArray());
			}
		} catch (CoreException e) {
			DebugUIPlugin.log(e);
		}
	}

	private void updateConfigFromLocalShared(ILaunchConfigurationWorkingCopy config) {
		if (isShared()) {
			String containerPathString = getSharedLocationText().getText();
			IContainer container = getContainer(containerPathString);
			config.setContainer(container);
		} else {
			config.setContainer(null);
		}
	}
		
	/**
	 * Update the favorite settings.
	 * 
	 * NOTE: set to NULL instead of false for backwards compatibility
	 *  when comparing if content is equal, since 'false' is default
	 * 	and will be missing for older configs.
	 */
	private void updateConfigFromFavorites(ILaunchConfigurationWorkingCopy config) {
		try {
			Object[] checked = fFavoritesTable.getCheckedElements();
			boolean debug = config.getAttribute(IDebugUIConstants.ATTR_DEBUG_FAVORITE, false);
			boolean run = config.getAttribute(IDebugUIConstants.ATTR_RUN_FAVORITE, false);
			if (debug || run) {
				// old attributes
				List groups = new ArrayList();
				int num = 0;
				if (debug) {
					groups.add(getLaunchConfigurationManager().getLaunchGroup(IDebugUIConstants.ID_DEBUG_LAUNCH_GROUP));
					num++;
				}
				if (run) {
					num++;
					groups.add(getLaunchConfigurationManager().getLaunchGroup(IDebugUIConstants.ID_DEBUG_LAUNCH_GROUP));
				}
				// see if there are any changes
				if (num == checked.length) {
					boolean different = false;
					for (int i = 0; i < checked.length; i++) {
						if (!groups.contains(checked[i])) {
							different = true;
							break;
						}
					}
					if (!different) {
						return;
					}
				}
			} 
			// erase old attributes (if any)
			config.setAttribute(IDebugUIConstants.ATTR_DEBUG_FAVORITE, (String)null);
			config.setAttribute(IDebugUIConstants.ATTR_RUN_FAVORITE, (String)null);
			// new attribute
			List groups = null;
			for (int i = 0; i < checked.length; i++) {
				LaunchGroupExtension group = (LaunchGroupExtension)checked[i];
				if (groups == null) {
					groups = new ArrayList();
				}
				groups.add(group.getIdentifier());
			}
			config.setAttribute(IDebugUIConstants.ATTR_FAVORITE_GROUPS, groups);
		} catch (CoreException e) {
			DebugUIPlugin.log(e);
		}		
	}	
	
	/**
	 * Convenience method for getting the workspace root.
	 */
	private IWorkspaceRoot getWorkspaceRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}
	
	/**
	 * @see ILaunchConfigurationTab#isValid(ILaunchConfiguration)
	 */
	public boolean isValid(ILaunchConfiguration config) {
		setMessage(null);
		setErrorMessage(null);
		
		return validateLocalShared();		
	}
	
	private boolean validateLocalShared() {
		if (isShared()) {
			String path = fSharedLocationText.getText().trim();
			IContainer container = getContainer(path);
			if (container == null || container.equals(ResourcesPlugin.getWorkspace().getRoot())) {
				setErrorMessage(LaunchConfigurationsMessages.getString("CommonTab.Invalid_shared_configuration_location_14")); //$NON-NLS-1$
				return false;
			} else if (!container.getProject().isOpen()) {
				setErrorMessage(LaunchConfigurationsMessages.getString("CommonTab.Cannot_save_launch_configuration_in_a_closed_project._1")); //$NON-NLS-1$
				return false;				
			}
		}
		
		return true;		
	}

	/**
	 * @see ILaunchConfigurationTab#setDefaults(ILaunchConfigurationWorkingCopy)
	 */
	public void setDefaults(ILaunchConfigurationWorkingCopy config) {
		config.setContainer(null);
		config.setAttribute(IDebugUIConstants.ATTR_LAUNCH_IN_BACKGROUND, true);
	}

	/**
	 * @see ILaunchConfigurationTab#performApply(ILaunchConfigurationWorkingCopy)
	 */
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		updateConfigFromLocalShared(configuration);
		updateConfigFromFavorites(configuration);
		setAttribute(IDebugUIConstants.ATTR_LAUNCH_IN_BACKGROUND, configuration, fLaunchInBackgroundButton.getSelection(), true);
		String encoding = null;
		if(fAltEncodingButton.getSelection()) {
		    encoding = fEncodingCombo.getText();
		}
		configuration.setAttribute(IDebugUIConstants.ATTR_CONSOLE_ENCODING, encoding);
		
	}

	/**
	 * @see ILaunchConfigurationTab#getName()
	 */
	public String getName() {
		return LaunchConfigurationsMessages.getString("CommonTab.&Common_15"); //$NON-NLS-1$
	}
	
	/**
	 * @see ILaunchConfigurationTab#canSave()
	 */
	public boolean canSave() {
		return validateLocalShared();
	}

	/**
	 * @see ILaunchConfigurationTab#getImage()
	 */
	public Image getImage() {
		return DebugUITools.getImage(IInternalDebugUIConstants.IMG_OBJS_COMMON_TAB);
	}

	class FavoritesContentProvider implements IStructuredContentProvider {
		/**
		 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
		 */
		public Object[] getElements(Object inputElement) {
			ILaunchGroup[] groups = DebugUITools.getLaunchGroups();
			List possibleGroups = new ArrayList();
			ILaunchConfiguration configuration = (ILaunchConfiguration)inputElement;
			for (int i = 0; i < groups.length; i++) {
				ILaunchGroup extension = groups[i];
				LaunchHistory history = getLaunchConfigurationManager().getLaunchHistory(extension.getIdentifier());
				if (history != null && history.accepts(configuration)) {
					possibleGroups.add(extension);
				} 
			}
			return possibleGroups.toArray();
		}

		/**
		 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
		 */
		public void dispose() {
		}

		/**
		 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
		 */
		public void inputChanged(
			Viewer viewer,
			Object oldInput,
			Object newInput) {
		}

	}
	
	class FavoritesLabelProvider implements ITableLabelProvider {
		
		private Map fImages = new HashMap();
		
		/**
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(java.lang.Object, int)
		 */
		public Image getColumnImage(Object element, int columnIndex) {
			Image image = (Image)fImages.get(element);
			if (image == null) {
				ImageDescriptor descriptor = ((LaunchGroupExtension)element).getImageDescriptor();
				if (descriptor != null) {
					image = descriptor.createImage();
					fImages.put(element, image);
				}
			}
			return image;
		}

		/**
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnText(java.lang.Object, int)
		 */
		public String getColumnText(Object element, int columnIndex) {
			String label = ((LaunchGroupExtension)element).getLabel();
			return DebugUIPlugin.removeAccelerators(label);
		}

		/**
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#addListener(org.eclipse.jface.viewers.ILabelProviderListener)
		 */
		public void addListener(ILabelProviderListener listener) {
		}

		/**
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#dispose()
		 */
		public void dispose() {
			Iterator images = fImages.values().iterator();
			while (images.hasNext()) {
				Image image = (Image)images.next();
				image.dispose();
			}
		}

		/**
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#isLabelProperty(java.lang.Object, java.lang.String)
		 */
		public boolean isLabelProperty(Object element, String property) {
			return false;
		}

		/**
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#removeListener(org.eclipse.jface.viewers.ILabelProviderListener)
		 */
		public void removeListener(ILabelProviderListener listener) {
		}
		
	}
	
	/**
	 * Convenience accessor
	 */
	protected LaunchConfigurationManager getLaunchConfigurationManager() {
		return DebugUIPlugin.getDefault().getLaunchConfigurationManager();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#activated(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
	 */
	public void activated(ILaunchConfigurationWorkingCopy workingCopy) {
		// do nothing when activated
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#deactivated(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
	 */
	public void deactivated(ILaunchConfigurationWorkingCopy workingCopy) {
		// do nothing when deactivated
	}

}

