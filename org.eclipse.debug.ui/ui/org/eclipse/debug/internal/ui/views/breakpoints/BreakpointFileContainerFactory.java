/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.views.breakpoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/**
 * 
 */
public class BreakpointFileContainerFactory implements IBreakpointContainerFactory {

	private ILabelProvider fImageProvider= new WorkbenchLabelProvider();
	
	public BreakpointFileContainerFactory() {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.breakpoints.IBreakpointContainerFactory#getContainers(org.eclipse.debug.core.model.IBreakpoint[])
	 */
	public IBreakpointContainer[] getContainers(IBreakpoint[] breakpoints, String parentId) {
		HashMap map= new HashMap();
		List other= new ArrayList();
		for (int i = 0; i < breakpoints.length; i++) {
			IBreakpoint breakpoint = breakpoints[i];
			IMarker marker = breakpoint.getMarker();
			if (marker != null) {
				IResource resource = marker.getResource();
				if (resource != null) {
					List list = (List) map.get(resource);
					if (list == null) {
						list= new ArrayList();
						map.put(resource, list);
					}
					list.add(breakpoint);
					continue;
				}
			}
			// No resource available
			other.add(breakpoint);
		}
		List containers= new ArrayList(map.size());
		Set resources = map.keySet();
		Iterator breakpointIter= resources.iterator();
		while (breakpointIter.hasNext()) {
			IResource resource= (IResource) breakpointIter.next();
			List breakpointsForFile= (List) map.get(resource);
			StringBuffer name= new StringBuffer(resource.getName());
			if (name.length() < 1) {
				// If the name's length is 0 (e.g. workspace root),
				// move breakpoints to "Other"
				Iterator iter = breakpointsForFile.iterator();
				while (iter.hasNext()) {
					other.add(iter.next());
				}
				continue;
			}
			IContainer parent = resource.getParent();
            if (parent != null) {
            	String parentPath= parent.getFullPath().toString().substring(1);
            	if (parentPath.length() > 0) {
            		name.append(" ["); //$NON-NLS-1$
            		name.append(parentPath);
            		name.append(']');
            	}
            }
			BreakpointContainer container= new BreakpointContainer(
					(IBreakpoint[]) breakpointsForFile.toArray(new IBreakpoint[0]),
					this,
					name.toString(),
					parentId);
			container.setImage(fImageProvider.getImage(resource));
			containers.add(container);
		}
		if (other.size() > 0) {
			BreakpointContainer container= new BreakpointContainer(
					(IBreakpoint[]) other.toArray(new IBreakpoint[0]),
					this,
					"(no file)",
					parentId);
			containers.add(container);
		}
		return (IBreakpointContainer[]) containers.toArray(new IBreakpointContainer[containers.size()]);
	}
	
	public void dispose() {
		fImageProvider.dispose();
	}

}
