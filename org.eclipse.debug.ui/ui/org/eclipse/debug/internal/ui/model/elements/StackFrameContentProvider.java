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
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IPresentationContext;
import org.eclipse.debug.ui.IDebugUIConstants;

/**
 * @since 3.3
 */
public class StackFrameContentProvider extends ElementContentProvider {

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.model.provisional.elements.ElementContentProvider#getChildCount(java.lang.Object, org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext)
	 */
	protected int getChildCount(Object element, IPresentationContext context) throws CoreException {
		return getAllChildren(element, context).length;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.model.provisional.elements.ElementContentProvider#getChildren(java.lang.Object, int, int, org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext)
	 */
	protected Object[] getChildren(Object parent, int index, int length, IPresentationContext context) throws CoreException {
		return getElements(getAllChildren(parent, context), index, length);
	}
	
	protected Object[] getAllChildren(Object parent, IPresentationContext context) throws CoreException {
        String id = context.getId();
        IStackFrame frame = (IStackFrame) parent;
        if (id.equals(IDebugUIConstants.ID_VARIABLE_VIEW)) {
            return frame.getVariables();
        } else if (id.equals(IDebugUIConstants.ID_REGISTER_VIEW)) {
            return frame.getRegisterGroups();
        }
        return EMPTY;		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.model.provisional.elements.ElementContentProvider#supportsContextId(java.lang.String)
	 */
	protected boolean supportsContextId(String id) {
		return id.equals(IDebugUIConstants.ID_VARIABLE_VIEW) || id.equals(IDebugUIConstants.ID_REGISTER_VIEW);
	}

}
