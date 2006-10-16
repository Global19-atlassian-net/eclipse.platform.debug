/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.commands.actions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.internal.ui.commands.provisional.IBooleanRequestMonitor;
import org.eclipse.debug.internal.ui.commands.provisional.IDebugCommand;
import org.eclipse.debug.internal.ui.contexts.DebugContextManager;
import org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextListener;
import org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextService;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Updates commands for a window. Coalesces update requests by command type.
 * 
 * @since 3.3
 */
public class DebugCommandService implements IDebugContextListener {
	
	/**
	 * Maps command types to update monitors
	 */
	private Map fCommandUpdates = new HashMap();
	
	/**
	 * Window this service is for.
	 */
	private IWorkbenchWindow fWindow = null;
	
	/**
	 * The context service for this command service.
	 */
	private IDebugContextService fContextService = null;
	
	/**
	 * Service per window
	 */
	private static Map fgServices = new HashMap();
		
	/**
	 * Returns the service for a window.
	 * 
	 * @param window
	 * @return service
	 */
	public synchronized static DebugCommandService getService(IWorkbenchWindow window) {
		DebugCommandService service = (DebugCommandService) fgServices.get(window);
		if (service == null) {
			service = new DebugCommandService(window);
			fgServices.put(window, service);
		}
		return service;
	}
	
	public DebugCommandService(IWorkbenchWindow window) {
		fWindow = window;
		fContextService = DebugContextManager.getDefault().getContextService(window);
		fContextService.addPostDebugContextListener(this);
		PlatformUI.getWorkbench().addWindowListener(new IWindowListener() {
		
			public void windowOpened(IWorkbenchWindow window) {
			}
		
			public void windowDeactivated(IWorkbenchWindow window) {
			}
		
			public void windowClosed(IWorkbenchWindow window) {
				if (fWindow == window) {
					dispose();
				}
			}
		
			public void windowActivated(IWorkbenchWindow window) {
			}
		
		});
	}
	
	private void dispose() {
		fContextService.removeDebugContextListener(this);
		fgServices.remove(fWindow);
		fCommandUpdates.clear();
		fWindow = null;
	}
	
	/**
	 * Updates the given command type after the next context change.
	 * 
	 * @param commandType
	 * @param monitor
	 */
	public void postUpdateCommand(Class commandType, IBooleanRequestMonitor monitor) {
		ProxyBooleanRequestMonitor proxy = (ProxyBooleanRequestMonitor) fCommandUpdates.get(commandType);
		if (proxy == null) {
			proxy = new ProxyBooleanRequestMonitor();
			fCommandUpdates.put(commandType, proxy);
		}
		proxy.addMonitor(monitor);		
	}
	
	/**
	 * Updates the given command type based on the active context.
	 * 
	 * @param commandType
	 * @param requestMonitor
	 */
	public void updateCommand(Class commandType, IBooleanRequestMonitor requestMonitor) {
		ISelection context = fContextService.getActiveContext();
		if (context instanceof IStructuredSelection && !context.isEmpty()) {
			Object[] elements = ((IStructuredSelection)context).toArray();
			ProxyBooleanRequestMonitor monitor = new ProxyBooleanRequestMonitor();
			monitor.addMonitor(requestMonitor);
			updateCommand(commandType, elements, monitor);
		} else {
			requestMonitor.setCanceled(true);
			requestMonitor.done();
		}
	}	
	
	private void postUpdate(ISelection context) {
		if (context instanceof IStructuredSelection && !context.isEmpty()) {
			Object[] elements = ((IStructuredSelection)context).toArray();
			Iterator iterator = fCommandUpdates.entrySet().iterator();
			while (iterator.hasNext()) {
				Entry entry = (Entry) iterator.next();
				Class commandType = (Class)entry.getKey();
				ProxyBooleanRequestMonitor monitor = (ProxyBooleanRequestMonitor) entry.getValue();
				updateCommand(commandType, elements, monitor);
			}
		} else {
			Iterator iterator = fCommandUpdates.values().iterator();
			while (iterator.hasNext()) {
				ProxyBooleanRequestMonitor monitor = (ProxyBooleanRequestMonitor) iterator.next();
				monitor.setCanceled(true);
				monitor.done();
			}
		}
		fCommandUpdates.clear();		
	}
	
	/**
	 * Updates the given command type for the specified elements.
	 * 
	 * @param commandType command class to update
	 * @param elements elements to update for
	 * @param monitor status monitor
	 */
	private void updateCommand(Class commandType, Object[] elements, ProxyBooleanRequestMonitor monitor) {
		IDebugCommand[] commands = new IDebugCommand[elements.length];
		int numVoters = 0;
		for (int i = 0; i < elements.length; i++) {
			Object element = elements[i];
			if (element instanceof IAdaptable) {
				IDebugCommand command = (IDebugCommand) ((IAdaptable)element).getAdapter(commandType);
				if (command != null) {
					commands[i] = command;
					numVoters++;
				} else {
					monitor.setCanceled(true);
					monitor.done();
					return;
				}
			}
		}
		if (!monitor.isCanceled()) {
			monitor.setNumVoters(numVoters);
			for (int i = 0; i < commands.length; i++) {
				IDebugCommand command = commands[i];
				command.canExecute(elements[i], monitor);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextListener#contextActivated(org.eclipse.jface.viewers.ISelection, org.eclipse.ui.IWorkbenchPart, org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextService)
	 */
	public void contextActivated(ISelection selection, IWorkbenchPart part) {
		postUpdate(selection);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextListener#contextChanged(org.eclipse.jface.viewers.ISelection, org.eclipse.ui.IWorkbenchPart, org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextService)
	 */
	public void contextChanged(ISelection selection, IWorkbenchPart part) {
		postUpdate(selection);
	}	
}
