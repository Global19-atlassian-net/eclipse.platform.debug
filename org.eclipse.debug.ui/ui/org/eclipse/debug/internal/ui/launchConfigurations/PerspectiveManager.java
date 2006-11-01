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


import java.io.IOException;
import java.io.StringReader;
import com.ibm.icu.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.eclipse.core.commands.contexts.Context;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchListener;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.internal.ui.views.ViewContextManager;
import org.eclipse.debug.internal.ui.views.ViewContextService;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.contexts.ISuspendTrigger;
import org.eclipse.debug.ui.contexts.ISuspendTriggerListener;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * The perspective manager manages the 'perspective' settings
 * defined by launch configurations. Specifically it: <ul>
 * <li>changes perspectives as launches are registered</li>
 * <li>change perspective when a thread suspends</li>
 * </ul>
 * 
 * @see IDebugUIContants.ATTR_RUN_PERSPECTIVE
 * @see IDebugUIContants.ATTR_DEBUG_PERSPECTIVE
 */
public class PerspectiveManager implements ILaunchListener, ISuspendTriggerListener {
		
	/**
	 * Table of config types to tables of user specified perspective settings (mode ids
	 * to perspective ids).
	 */	
	private Map fPreferenceMap;
	
	// XML tags
	public static final String ELEMENT_PERSPECTIVES = "launchPerspectives"; //$NON-NLS-1$
	public static final String ELEMENT_PERSPECTIVE = "launchPerspective"; //$NON-NLS-1$
	public static final String ATTR_TYPE_ID = "configurationType"; //$NON-NLS-1$
	public static final String ATTR_MODE_ID = "mode"; //$NON-NLS-1$
	public static final String ATTR_PERSPECTIVE_ID = "perspective";  //$NON-NLS-1$

	/**
	 * Flag used to indicate that the user is already being prompted to
	 * switch perspectives. This flag allows us to not open multiple
	 * prompts at the same time.
	 */
	private boolean fPrompting;
	private PerspectiveSwitchLock fPerspectiveSwitchLock = new PerspectiveSwitchLock();
    
    /**
     * Maps each launch to its perspective context activation. These
     * are disabled when a launch terminates.
     */
    private Map fLaunchToContextActivations = new HashMap();
	
	/**
	 * Lock used to synchronize perspective switching with view activation.
	 * Clients wanting to perform an action after a perspective switch should
	 * schedule jobs with the perspective manager via #schedulePostSwitch(..)
	 */
	public class PerspectiveSwitchLock
	{

		private int fSwitch = 0;
		private List fJobs = new ArrayList();
		
		public synchronized void startSwitch() {
			fSwitch++;
		}
		
		public synchronized void endSwitch() {
			fSwitch--;
			if (fSwitch == 0) {
				Iterator jobs = fJobs.iterator();
				while (jobs.hasNext()) {
					((Job)jobs.next()).schedule();
				}
				fJobs.clear();
			}
		}
				
		public synchronized void schedulePostSwitch(Job job) {
			if (fSwitch > 0) {
				fJobs.add(job);	
			} else {
				job.schedule();
			}
		}
	}
	
	
	/**
	 * Called by the debug ui plug-in on startup.
	 * The perspective manager starts listening for
	 * launches to be registered.
	 */
	public void startup() {
		DebugPlugin plugin = DebugPlugin.getDefault();
		plugin.getLaunchManager().addLaunchListener(this);
	}

	/**
	 * Called by the debug ui plug-in on shutdown.
	 * The perspective manager de-registers as a 
	 * launch listener.
	 */
	public void shutdown() {
		DebugPlugin plugin = DebugPlugin.getDefault();
		plugin.getLaunchManager().removeLaunchListener(this);
	}

	/**
	 * If there are no launches, remove the Suspend Trigger Listener
	 * 
	 * @see ILaunchListener#launchRemoved(ILaunch)
	 */
	public synchronized void launchRemoved(final ILaunch launch) {
        ISuspendTrigger trigger = (ISuspendTrigger) launch.getAdapter(ISuspendTrigger.class);
        if (trigger != null) {
            trigger.removeSuspendTriggerListener(this);
        }
        Runnable r= new Runnable() {
			public void run() {
		        IContextActivation[] activations = (IContextActivation[]) fLaunchToContextActivations.remove(launch);
		        if (activations != null) {
		        	for (int i = 0; i < activations.length; i++) {
						IContextActivation activation = activations[i];
						activation.getContextService().deactivateContext(activation);
					}
		        }
			}
		};
		async(r);
	}
	
	/**
	 * Do nothing.
	 * 
	 * @see ILaunchListener#launchChanged(ILaunch)
	 */
	public void launchChanged(ILaunch launch) {
	}	

	/** 
	 * Switch to the perspective specified by the
	 * launch configuration.
	 * 
	 * @see ILaunchListener#launchAdded(ILaunch)
	 */
	public synchronized void launchAdded(ILaunch launch) {
        ISuspendTrigger trigger = (ISuspendTrigger) launch.getAdapter(ISuspendTrigger.class);
        if (trigger != null) {
            trigger.addSuspendTriggerListener(this);
        }
	    
	    fPerspectiveSwitchLock.startSwitch();
	    
		String perspectiveId = null;
		// check event filters
		try {
			perspectiveId = getPerspectiveId(launch);
		} catch (CoreException e) {
			String name = DebugUIPlugin.getModelPresentation().getText(launch);
			switchFailed(e, name);
		}
		
		// don't switch if a private config
		ILaunchConfiguration configuration = launch.getLaunchConfiguration();
		if (configuration != null) {
			if (!LaunchConfigurationManager.isVisible(configuration)) {
				perspectiveId = null;
			}
		}
		
		final String id= perspectiveId;
		// switch
		async(new Runnable() {
			public void run() {
				try
				{
					IWorkbenchWindow window = getWindowForPerspective(id);
					if (id != null && window != null && shouldSwitchPerspectiveForLaunch(window, id)) {
						switchToPerspective(window, id);
					}
				}
				finally
				{
					fPerspectiveSwitchLock.endSwitch();
				}
			}
		});
	}


	/**
	 * Switches to the specified perspective
	 * 
	 * @param id perspective identifier
	 */
	protected void switchToPerspective(IWorkbenchWindow window, String id) {
		try {
			window.getWorkbench().showPerspective(id, window);
		} catch (WorkbenchException e) {
			DebugUIPlugin.errorDialog(DebugUIPlugin.getShell(),
			LaunchConfigurationsMessages.PerspectiveManager_Error_1,  
			MessageFormat.format(LaunchConfigurationsMessages.PerspectiveManager_Unable_to_switch_to_perspective___0__2, new String[]{id}), 
			e);
		}
	}
	
	/**
	 * Utility method to submit an asnychronous runnable to the UI
	 */
	protected void async(Runnable r) {
		Display d = DebugUIPlugin.getStandardDisplay();
		if (d != null && !d.isDisposed()) {
			d.asyncExec(r);
		}
	}
	
	/**
	 * Utility method to submit a synchronous runnable to the UI
	 */
	protected void sync(Runnable r) {
		Display d = DebugUIPlugin.getStandardDisplay();
		if (d != null && !d.isDisposed()) {
			d.syncExec(r);
		}
	}	

	/**
	 * Reports failure to switch perspectives to the user
	 * 
	 * @param status exception status describing failure
	 * @param launchName the name of the launch that the
	 *  failure is associated with
	 */
	protected void switchFailed(final Throwable t, final String launchName) {
		sync(new Runnable() {
			public void run() {
				DebugUIPlugin.errorDialog(DebugUIPlugin.getShell(), LaunchConfigurationsMessages.PerspectiveManager_Error_1,  
				 MessageFormat.format(LaunchConfigurationsMessages.PerspectiveManager_Unable_to_switch_perpsectives_as_specified_by_launch___0__4, new String[] {launchName}), 
				 t);
			}});
	}
	
	/**
	 * A breakpoint has been hit. Carry out perspective switching
	 * as appropriate for the given debug event. 
	 * 
	 * @param event the suspend event
	 */
	private void handleBreakpointHit(final ILaunch launch) {
		
		// Must be called here to indicate that the perspective
		// may be switching.
		// Putting this in the async UI call will cause the Perspective
		// Manager to turn on the lock too late.  Consequently, LaunchViewContextListener
		// may not know that the perspective will switch and will open view before
		// the perspective switch.
		fPerspectiveSwitchLock.startSwitch();
		
		String perspectiveId = null;
		try {
			perspectiveId = getPerspectiveId(launch);
		} catch (CoreException e) {
			DebugUIPlugin.log(e);
		}
		// if no perspective specified, always switch to debug
		// perspective 

		// this has to be done in an asynch, such that the workbench
		// window can be accessed
		final String targetId = perspectiveId;
		Runnable r = new Runnable() {
			public void run() {
				IWorkbenchWindow window = null;
				try{
				if (targetId != null) {
						window = getWindowForPerspective(targetId);
						if (window == null) {
							return;
						}
						
						if (shouldSwitchPerspectiveForSuspend(window, targetId)) {
							switchToPerspective(window, targetId);
							// Showing the perspective can open a new window
							// (based on user prefs). So check again in case a
							// new window has been opened.
							window = getWindowForPerspective(targetId);
							if (window == null) {
								return;
							}
						}
						// re-open the window if minimized 
						Shell shell= window.getShell();
						if (shell != null) {
							if (DebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IDebugUIConstants.PREF_ACTIVATE_WORKBENCH)) {
								if (shell.getMinimized()) {
									shell.setMinimized(false);
								}
								shell.forceActive();
							}
						}
					}
					if (targetId != null) {
						// enable the 'perspective contexts' for the suspended launch type
						Object ca = fLaunchToContextActivations.get(launch);
						if (ca == null) {
							ILaunchConfiguration launchConfiguration = launch.getLaunchConfiguration();
							if (launchConfiguration != null) {
								try {
									String type = launchConfiguration.getType().getIdentifier();
									ViewContextService service = ViewContextManager.getDefault().getService(window);
									if (service != null) {
										IContextService contextServce = (IContextService) PlatformUI.getWorkbench().getAdapter(IContextService.class);
										String[] ids = service.getEnabledPerspectives();
										IContextActivation[] activations = new IContextActivation[ids.length];
										for (int i = 0; i < ids.length; i++) {
											Context context = contextServce.getContext(type + "." + ids[i]); //$NON-NLS-1$
											if (!context.isDefined()) {
												context.define(context.getId(), null, null);
											}
											IContextActivation activation = contextServce.activateContext(context.getId());
											activations[i] = activation;
										}
										fLaunchToContextActivations.put(launch, activations);
									}
								} catch (CoreException e) {
									DebugUIPlugin.log(e);
								}
							}
						}

					}
					if (window != null && DebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IInternalDebugUIConstants.PREF_ACTIVATE_DEBUG_VIEW)) {
						ViewContextService service = ViewContextManager.getDefault().getService(window);
						service.showViewQuiet(IDebugUIConstants.ID_DEBUG_VIEW);
					}
				}
				finally
				{
					fPerspectiveSwitchLock.endSwitch();
				}
			}
		};
		async(r);
	}
	
	/**
	 * Returns the workbench window in which the given perspective
	 * should be shown. First, check the current window to see if it
	 * is already showing the perspective. Then check any other windows.
	 * 
	 * @param perspectiveId the perspective identifier
	 * @return which window the given perspective should be shown in
	 *  or <code>null</code> if there are no windows available
	 */
	private IWorkbenchWindow getWindowForPerspective(String perspectiveId) {
		// Check the active window first
		IWorkbenchWindow window = DebugUIPlugin.getActiveWorkbenchWindow();
		if (isWindowShowingPerspective(window, perspectiveId)) {
			return window;
		}
		// Then check all other windows
		IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
		for (int i = 0; i < windows.length; i++) {
			window = windows[i];
			if (isWindowShowingPerspective(window, perspectiveId)) {
				return window;
			}
		}
		// Finally, just return a window in which the perspective
		// should be created
		window = DebugUIPlugin.getActiveWorkbenchWindow();
		if (window != null) {
			return window;
		}
		if (windows.length > 0) {
			return windows[0];
		}
		return null;
	}
	
	private boolean isWindowShowingPerspective(IWorkbenchWindow window, String perspectiveId) {
		if (window != null) {
			IWorkbenchPage page = window.getActivePage();
			if (page != null) {
				IPerspectiveDescriptor perspectiveDescriptor = page.getPerspective();
				if (perspectiveDescriptor != null && perspectiveDescriptor.getId().equals(perspectiveId)) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Returns whether or not the user wishes to switch to the specified
	 * perspective when a suspend occurs.
	 * 
	 * @param perspectiveName the name of the perspective that will be presented
	 *  to the user for confirmation if they've asked to be prompted about
	 *  perspective switching for suspension
	 * @return whether or not the user wishes to switch to the specified perspective
	 *  automatically when the given launch suspends
	 */
	protected boolean shouldSwitchPerspectiveForSuspend(IWorkbenchWindow window, String perspectiveId) {
		String launchMessage;
		if (getPerspectiveDescription(perspectiveId) != null)
			launchMessage = LaunchConfigurationsMessages.PerspectiveManager_suspend_description;
		else
			launchMessage = LaunchConfigurationsMessages.PerspectiveManager_13;
			
		return shouldSwitchPerspective(window, perspectiveId, launchMessage, IInternalDebugUIConstants.PREF_SWITCH_PERSPECTIVE_ON_SUSPEND); 
	}

	/**
	 * Returns whether or not the user wishes to switch to the specified
	 * perspective when a launch occurs.
	 * 
	 * @param perspectiveName the name of the perspective that will be presented
	 *  to the user for confirmation if they've asked to be prompted about
	 *  perspective switching
	 * @return whether or not the user wishes to switch to the specified perspective
	 *  automatically when a launch occurs
	 */
	protected boolean shouldSwitchPerspectiveForLaunch(IWorkbenchWindow window, String perspectiveId) {
		String launchMessage;
		if (getPerspectiveDescription(perspectiveId) != null)
			launchMessage = LaunchConfigurationsMessages.PerspectiveManager_launch_description;
		else
			launchMessage = LaunchConfigurationsMessages.PerspectiveManager_15;
			

		return shouldSwitchPerspective(window, perspectiveId, launchMessage, IInternalDebugUIConstants.PREF_SWITCH_TO_PERSPECTIVE); 
	}
	
	/**
	 * Returns whether or not the user wishes to switch to the specified
	 * perspective when a launch occurs.
	 * 
	 * @param perspectiveName the name of the perspective that will be presented
	 *  to the user for confirmation if they've asked to be prompted about
	 *  perspective switching
	 * @param message a message to be presented to the user. This message is expected to
	 *  contain a slot for the perspective name to be inserted ("{0}").
	 * @param preferenceKey the preference key of the perspective switching preference
	 * @return whether or not the user wishes to switch to the specified perspective
	 *  automatically
	 */
	private boolean shouldSwitchPerspective(IWorkbenchWindow window, String perspectiveId, String message, String preferenceKey) {
		if (isCurrentPerspective(window, perspectiveId)) {
			return false;
		}
		String perspectiveName= getPerspectiveLabel(perspectiveId);
		if (perspectiveName == null) {
			return false;
		}
		String perspectiveDesc = getPerspectiveDescription(perspectiveId);
		String[] args;
		if (perspectiveDesc != null)
			args = new String[] { perspectiveName , perspectiveDesc };
		else 
			args = new String[] { perspectiveName };
		String switchPerspective= DebugUIPlugin.getDefault().getPreferenceStore().getString(preferenceKey);
		if (MessageDialogWithToggle.ALWAYS.equals(switchPerspective)) {
			return true;
		} else if (MessageDialogWithToggle.NEVER.equals(switchPerspective)) {
			return false;
		}
		
		Shell shell= window.getShell();
		if (shell == null || fPrompting) {
			return false;
		}
		fPrompting= true;
		// Activate the shell if necessary so the prompt is visible
		if (shell.getMinimized()) {
			shell.setMinimized(false);
		}
		if (DebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IDebugUIConstants.PREF_ACTIVATE_WORKBENCH)) {
			shell.forceActive();
		}
		MessageDialogWithToggle dialog = MessageDialogWithToggle.openYesNoQuestion(shell, LaunchConfigurationsMessages.PerspectiveManager_12, MessageFormat.format(message, args), null, false, DebugUIPlugin.getDefault().getPreferenceStore(), preferenceKey); 
		boolean answer = (dialog.getReturnCode() == IDialogConstants.YES_ID);
		synchronized (this) {
			fPrompting= false;
			notifyAll();
		}
		if (isCurrentPerspective(window, perspectiveId)) {
			// While prompting in response to one event (say, a launch),
			// another event can occur which changes the perspective.
			// Double-check that we're not in the right perspective.
			answer= false;
		}
		return answer;
	}
	
	/**
	 * Returns whether the given perspective identifier matches the
	 * identifier of the current perspective.
	 * 
	 * @param perspectiveId the identifier
	 * @return whether the given perspective identifier matches the
	 *  identifier of the current perspective
	 */
	protected boolean isCurrentPerspective(IWorkbenchWindow window, String perspectiveId) {
		boolean isCurrent= false;
		if (window != null) {
			IWorkbenchPage page = window.getActivePage();
			if (page != null) {
				IPerspectiveDescriptor perspectiveDescriptor = page.getPerspective();
				if (perspectiveDescriptor != null) {
					isCurrent= perspectiveId.equals(perspectiveDescriptor.getId());
				}
			}
		}
		return isCurrent;
	}
	
	/**
	 * Returns the label of the perspective with the given identifier or
	 * <code>null</code> if no such perspective exists.
	 * 
	 * @param perspectiveId the identifier
	 * @return the label of the perspective with the given identifier or
	 *  <code>null</code> if no such perspective exists 
	 */
	protected String getPerspectiveLabel(String perspectiveId) {
		IPerspectiveDescriptor newPerspective = PlatformUI.getWorkbench().getPerspectiveRegistry().findPerspectiveWithId(perspectiveId);
		if (newPerspective == null) {
			return null;
		}
		return newPerspective.getLabel();
	}

	
	/**
	 * Returns the label of the perspective with the given identifier or
	 * <code>null</code> if no such perspective exists.
	 * 
	 * @param perspectiveId the identifier
	 * @return the label of the perspective with the given identifier or
	 *  <code>null</code> if no such perspective exists 
	 */
	protected String getPerspectiveDescription(String perspectiveId) {
		IPerspectiveDescriptor newPerspective = PlatformUI.getWorkbench().getPerspectiveRegistry().findPerspectiveWithId(perspectiveId);
		if (newPerspective == null) {
			return null;
		}
		return newPerspective.getDescription();
	}
	
	/** 
	 * Returns the perspective associated with the
	 * given launch, or <code>null</code> if none.
	 * 
	 * @param launch a launch
	 * @return the perspective associated with the launch,
	 * 	or <code>null</code>
	 * @exception CoreException if unable to retrieve a required
	 *  launch configuration attribute
	 */
	protected String getPerspectiveId(ILaunch launch) throws CoreException {
		if (launch == null) {
			return null;
		}
		ILaunchConfiguration config = launch.getLaunchConfiguration();
		if (config == null) {
			return null;
		}
		String perspectiveId = null;
		perspectiveId = DebugUITools.getLaunchPerspective(config.getType(), launch.getLaunchMode());
		if (perspectiveId != null && perspectiveId.equals(IDebugUIConstants.PERSPECTIVE_NONE)) {
			perspectiveId = null;
		}
		return perspectiveId;
	}
	
	/**
	 * Returns the perspective to switch to when a configuration of the given type
	 * is launched in the given mode, or <code>null</code> if no switch should take
	 * place.
	 * 
	 * @param type launch configuration type
	 * @param mode launch mode identifier
	 * @return perspective identifier or <code>null</code>
	 * @since 3.0
	 */
	public String getLaunchPerspective(ILaunchConfigurationType type, String mode) {
		String id = getUserSpecifiedLaunchPerspective(type, mode);
		if (id == null) {
			// get the default
			id = getDefaultLaunchPerspective(type, mode);
		} else if (id.equals(IDebugUIConstants.PERSPECTIVE_NONE)) {
			// translate NONE to null
			id = null;
		}
		return id;
	}
	
	/**
	 * Sets the perspective to switch to when a configuration of the given type
	 * is launched in the given mode. <code>PERSPECTIVE_NONE</code> indicates no
	 * perspective switch should take place. <code>PERSPECTIVE_DEFAULT</code> indicates
	 * a default perspective switch should take place, as defined by the associated
	 * launch tab group extension.
	 * 
	 * @param type launch configuration type
	 * @param mode launch mode identifier
	 * @param perspective identifier, <code>PERSPECTIVE_NONE</code>, or
	 *   <code>PERSPECTIVE_DEFAULT</code>
	 * @since 3.0
	 */
	public void setLaunchPerspective(ILaunchConfigurationType type, String mode, String perspective) {
		internalSetLaunchPerspective(type.getIdentifier(), mode, perspective);
		// update preference
		String xml;
		try {
			xml = generatePerspectiveXML();
			DebugUIPlugin.getDefault().getPreferenceStore().putValue(IInternalDebugUIConstants.PREF_LAUNCH_PERSPECTIVES, xml);			
		} catch (IOException e) {
			DebugUIPlugin.log(DebugUIPlugin.newErrorStatus("Exception occurred while generating launch perspectives preference XML", e));  //$NON-NLS-1$
		} catch (ParserConfigurationException e) {
			DebugUIPlugin.log(DebugUIPlugin.newErrorStatus("Exception occurred while generating launch perspectives preference XML", e));  //$NON-NLS-1$
		} catch (TransformerException e) {
			DebugUIPlugin.log(DebugUIPlugin.newErrorStatus("Exception occurred while generating launch perspectives preference XML", e));  //$NON-NLS-1$
		}
	}
	
	/**
	 * Sets the perspective to switch to when a configuration of the given type
	 * is launched in the given mode. <code>PERSPECTIVE_NONE</code> indicates no
	 * perspective switch should take place. <code>PERSPECTIVE_DEFAULT</code> indicates
	 * a default perspective switch should take place, as defined by the associated
	 * launch tab group extension.
	 * 
	 * @param type launch configuration type identifier
	 * @param mode launch mode identifier
	 * @param perspective identifier, <code>PERSPECTIVE_NONE</code>, or
	 *   <code>PERSPECTIVE_DEFAULT</code>
	 * @since 3.0
	 */
	private void internalSetLaunchPerspective(String type, String mode, String perspective) {
		if (fPreferenceMap == null) {
			initPerspectives();
		}		
		Map modeMap = (Map)fPreferenceMap.get(type);
		if (modeMap == null) {
			modeMap = new HashMap();
			fPreferenceMap.put(type, modeMap);
		}
		if (perspective.equals(IDebugUIConstants.PERSPECTIVE_DEFAULT)) {
			// remove user preference setting
			modeMap.remove(mode);
		} else {
			// override default setting
			modeMap.put(mode, perspective);
		}
	}
	
	/**
	 * Generates XML for the user specified perspective settings.
	 *  
	 * @return XML
	 * @exception IOException if unable to generate the XML
     * @exception TransformerException if unable to generate the XML
     * @exception ParserConfigurationException if unable to generate the XML
	 */
	private String generatePerspectiveXML() throws ParserConfigurationException, TransformerException, IOException {
		
		Document doc = DebugUIPlugin.getDocument();
		Element configRootElement = doc.createElement(ELEMENT_PERSPECTIVES);
		doc.appendChild(configRootElement);
		
		Iterator configTypes = fPreferenceMap.keySet().iterator();
		while (configTypes.hasNext()) {
			String type = (String)configTypes.next();
			Map modeMap = (Map)fPreferenceMap.get(type);
			if (modeMap != null && !modeMap.isEmpty()) {
				Iterator modes = modeMap.keySet().iterator();
				while (modes.hasNext()) {
					String mode = (String)modes.next();
					String perspective = (String)modeMap.get(mode);
					Element element = doc.createElement(ELEMENT_PERSPECTIVE);
					element.setAttribute(ATTR_TYPE_ID, type);
					element.setAttribute(ATTR_MODE_ID, mode);
					element.setAttribute(ATTR_PERSPECTIVE_ID, perspective);
					configRootElement.appendChild(element);
				}
			}			
		}

		return DebugUIPlugin.serializeDocument(doc);		
	}

	/**
	 * Returns the default perspective to switch to when a configuration of the given
	 * type is launched in the given mode, or <code>null</code> if none.
	 * 
	 * @param type launch configuration type
	 * @param mode launch mode
	 * @return perspective identifier, or <code>null</code>
	 */
	public String getDefaultLaunchPerspective(ILaunchConfigurationType type, String mode) {
		LaunchConfigurationTabGroupExtension extension = LaunchConfigurationPresentationManager.getDefault().getExtension(type.getIdentifier(), mode);
		if (extension != null) {
			String id = extension.getPerspective(mode);
			if (id == null) {
				// revert to hard coded default (for backwards compatibility)
				// since nothing is specified in XML
				if (mode.equals(ILaunchManager.DEBUG_MODE)) {
					return IDebugUIConstants.ID_DEBUG_PERSPECTIVE;
				}	
			} else {
				return id;
			}
		}
		return null;
	}
	
	/**
	 * Returns the user specified perspective to switch to when a configuration of the
	 * given type is launched in the given mode, or <code>null</code> if unspecified.
	 * Returns <code>PERSPECTIVE_NONE</code> to indicate no switch
	 * 
	 * @param type launch configuration type
	 * @param mode launch mode
	 * @return perspective identifier, <code>PERSPECTIVE_NONE</code>, or <code>null</code>
	 */
	protected String getUserSpecifiedLaunchPerspective(ILaunchConfigurationType type, String mode) {
		String id = null;
		if (fPreferenceMap == null) {
			initPerspectives();
		}
		Map modeMap = (Map)fPreferenceMap.get(type.getIdentifier());
		if (modeMap != null) {
			id = (String)modeMap.get(mode);
		}
		return id;
	}

	/**
	 * Initialize the preference map with settings from user preference
	 */
	private void initPerspectives() {
		fPreferenceMap = new HashMap();
		String xml = DebugUIPlugin.getDefault().getPreferenceStore().getString(IInternalDebugUIConstants.PREF_LAUNCH_PERSPECTIVES);
		if (xml != null && xml.length() > 0) {
			try {
				Element root = null;
				DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
				parser.setErrorHandler(new DefaultHandler());
				StringReader reader = new StringReader(xml);
				InputSource source = new InputSource(reader);
				root = parser.parse(source).getDocumentElement();
				
				NodeList list = root.getChildNodes();
				int length = list.getLength();
				for (int i = 0; i < length; ++i) {
					Node node = list.item(i);
					short nt = node.getNodeType();
					if (nt == Node.ELEMENT_NODE) {
						Element element = (Element) node;
						String nodeName = element.getNodeName();
						if (nodeName.equalsIgnoreCase(ELEMENT_PERSPECTIVE)) {
							String type = element.getAttribute(ATTR_TYPE_ID);
							String mode = element.getAttribute(ATTR_MODE_ID);
							String perpsective = element.getAttribute(ATTR_PERSPECTIVE_ID);
							internalSetLaunchPerspective(type, mode, perpsective);
						}
					}
				}				
			} catch (ParserConfigurationException e) {
				DebugUIPlugin.log(e);			
			} catch (SAXException e) {
				DebugUIPlugin.log(e);
			} catch (IOException e) {
				DebugUIPlugin.log(e);
			}
		}
		
	}
	
	/**
	 * Schedules the given job after perspective switching is complete, or
	 * immediately if a perspective switch is not in progress.
	 * 
	 * @param job job to run after perspective switching
	 */
	public void schedulePostSwitch(Job job) {
		fPerspectiveSwitchLock.schedulePostSwitch(job);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.ISuspendTriggerListener#suspended(org.eclipse.debug.core.ILaunch, java.lang.Object)
	 */
	public void suspended(ILaunch launch, Object context) {
		handleBreakpointHit(launch);
	}
}
