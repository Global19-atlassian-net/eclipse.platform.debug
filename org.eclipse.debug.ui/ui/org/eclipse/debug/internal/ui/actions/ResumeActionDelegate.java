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
package org.eclipse.debug.internal.ui.actions;


import java.util.Iterator;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.ISuspendResume;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.jface.viewers.IStructuredSelection;

public class ResumeActionDelegate extends AbstractListenerActionDelegate {

	/**
	 * @see AbstractDebugActionDelegate#doAction(Object)
	 */
	protected void doAction(Object object) throws DebugException {
	    if (isEnabledFor(object)) {
	        ISuspendResume resume = (ISuspendResume)object;
			resume.resume();
	    } else {
	        doActionForAllThreads(object);
	    }
	}
	
	/**
	 * Resumes all threads in the target associated with the given element
	 * 
	 * @param object debug element
	 * @throws DebugException on failure
	 */
	protected void doActionForAllThreads(Object object) throws DebugException {
	    if (isEnabledForAllThreads(object)) {
	        IDebugElement debugElement = (IDebugElement) object;
	        IThread[] threads = debugElement.getDebugTarget().getThreads();
	        for (int i = 0; i < threads.length; i++) {
	            IThread thread = threads[i];
	            if (thread.canResume()) {
	                thread.resume();
	            }
	        }
	    }
	}
	
	/**
	 * @see AbstractDebugActionDelegate#isRunInBackground()
	 */
	protected boolean isRunInBackground() {
		return true;
	}

	/**
	 * @see AbstractDebugActionDelegate#isEnabledFor(Object)
	 */
	protected boolean isEnabledFor(Object element) {
		return element instanceof ISuspendResume && ((ISuspendResume) element).canResume();
	}

	/**
	 * @see AbstractDebugActionDelegate#getEnableStateForSelection(IStructuredSelection)
	 */
	protected boolean getEnableStateForSelection(IStructuredSelection selection) {
	    if (selection.isEmpty()) {
	        return false;
	    }
		for (Iterator i = selection.iterator(); i.hasNext(); ) {
			Object element = i.next();
			if (!(isEnabledFor(element) || isEnabledForAllThreads(element))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns whether 'resume all threads' should be enabled for the given element.
	 */
	protected boolean isEnabledForAllThreads(Object element) {
		if (element instanceof IDebugElement) {
            IDebugElement debugElement = (IDebugElement) element;
            Object lock = null;
            try {
            	lock = DebugPlugin.getAccessLock(debugElement);
                IThread[] threads = debugElement.getDebugTarget().getThreads();
                for (int i = 0; i < threads.length; i++) {
                    if (threads[i].canResume()) {
                        return true;
                    }
                }
            } catch (DebugException e) {
            } finally {
            	DebugPlugin.releaseLock(lock);
            }
        }
		return false;
	}
	
	/**
	 * @see AbstractDebugActionDelegate#getStatusMessage()
	 */
	protected String getStatusMessage() {
		return ActionMessages.getString("ResumeActionDelegate.Exceptions_occurred_attempting_to_resume._2"); //$NON-NLS-1$
	}

	/**
	 * @see AbstractDebugActionDelegate#getErrorDialogMessage()
	 */
	protected String getErrorDialogMessage() {
		return ActionMessages.getString("ResumeActionDelegate.Resume_failed._1"); //$NON-NLS-1$
	}

	/**
	 * @see AbstractDebugActionDelegate#getErrorDialogTitle()
	 */
	protected String getErrorDialogTitle() {
		return ActionMessages.getString("ResumeActionDelegate.Resume_3"); //$NON-NLS-1$
	}
}
