/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.viewers.update;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelDelta;
import org.eclipse.debug.internal.ui.viewers.provisional.ModelDelta;

public class ProcessProxy extends EventHandlerModelProxy {

    private IProcess fProcess;

    private DebugEventHandler fProcessEventHandler = new DebugEventHandler(this) {
        protected boolean handlesEvent(DebugEvent event) {
            return event.getSource().equals(fProcess);
        }

		protected void handleChange(DebugEvent event) {
			ModelDelta delta = null;
        	synchronized (ProcessProxy.this) {
        		if (!isDisposed()) {
                    delta = new ModelDelta(DebugPlugin.getDefault().getLaunchManager(), IModelDelta.NO_CHANGE);
                    ModelDelta node = delta;
                    node = node.addNode(fProcess.getLaunch(), IModelDelta.NO_CHANGE);
                    node.addNode(fProcess, IModelDelta.STATE);        			
        		}
			}
        	if (delta != null && !isDisposed()) {
        		fireModelChanged(delta);
        	}
        }

        protected void handleCreate(DebugEvent event) {
        	// do nothing - Launch change notification handles this
        }

		protected void handleTerminate(DebugEvent event) {
			handleChange(event);
		}
        
        
        
    };

    /* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.update.EventHandlerModelProxy#dispose()
	 */
	public synchronized void dispose() {
		super.dispose();
		fProcess = null;
	}

	public ProcessProxy(IProcess process) {
        fProcess = process;
    }

    protected synchronized boolean containsEvent(DebugEvent event) {
        return event.getSource().equals(fProcess);
    }

    protected DebugEventHandler[] createEventHandlers() {
        return new DebugEventHandler[] {fProcessEventHandler};
    }

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.AbstractModelProxy#installed()
	 */
	public void installed() {
		// select process if in run mode
		IProcess process = fProcess;
		if (process != null) {
			ILaunch launch = process.getLaunch();
			if (launch != null && ILaunchManager.RUN_MODE.equals(launch.getLaunchMode())) {
				// select the process
				ModelDelta delta = new ModelDelta(DebugPlugin.getDefault().getLaunchManager(), IModelDelta.NO_CHANGE);
				ModelDelta node = delta.addNode(process.getLaunch(), IModelDelta.NO_CHANGE);
				node = node.addNode(process, IModelDelta.SELECT);
				fireModelChanged(delta);					
			}
		}
	}
}
