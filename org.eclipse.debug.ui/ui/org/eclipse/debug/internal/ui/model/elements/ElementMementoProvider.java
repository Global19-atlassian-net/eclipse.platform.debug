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
package org.eclipse.debug.internal.ui.model.elements;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.internal.ui.model.IElementCompareRequest;
import org.eclipse.debug.internal.ui.model.IElementMementoProvider;
import org.eclipse.debug.internal.ui.model.IElementMementoRequest;
import org.eclipse.ui.IMemento;

/**
 * @since 3.3
 */
public abstract class ElementMementoProvider implements IElementMementoProvider {

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.model.IElementMementoProvider#compareElement(org.eclipse.debug.internal.ui.model.IElementCompareRequest)
	 */
	public void compareElement(final IElementCompareRequest request) {
		Job job = new Job("compare element") { //$NON-NLS-1$
			protected IStatus run(IProgressMonitor monitor) {
				Object element = request.getElement();
				IMemento memento = request.getMemento();
				try {
					request.setEqual(isEqual(element, memento));
				} catch (CoreException e) {
					request.setStatus(e.getStatus());
				}
				request.done();
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		// TODO: rule
		job.schedule();
	}

	/**
	 * Returns whether the memento represents the given element.
	 * 
	 * @param element
	 * @param memento
	 * @return whether the memento represents the given element
	 */
	protected abstract boolean isEqual(Object element, IMemento memento) throws CoreException;

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.model.IElementMementoProvider#encodeElement(org.eclipse.debug.internal.ui.model.IElementMementoRequest)
	 */
	public void encodeElement(final IElementMementoRequest request) {
		Job job = new Job("encode element") { //$NON-NLS-1$
			protected IStatus run(IProgressMonitor monitor) {
				Object element = request.getElement();
				IMemento memento = request.getMemento();
				try {
					if (!encodeElement(element, memento)) {
						request.setCanceled(true);
					}
				} catch (CoreException e) {
					request.setStatus(e.getStatus());
				}
				request.done();
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		// TODO: rule
		job.schedule();
	}
	
	/**
	 * Encodes the specified element into the given memento.
	 * Returns whether the element could be encoded
	 * 
	 * @param element
	 * @param memento
	 * @return false if cancelled/not supported
	 * @throws CoreException
	 */
	protected abstract boolean encodeElement(Object element, IMemento memento) throws CoreException;

}
