/*******************************************************************************
 * Copyright (c) 2006, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Wind River Systems - Fix for viewer state save/restore [188704] 
 *     Pawel Piech (Wind River) - added support for a virtual tree model viewer (Bug 242489)
 *******************************************************************************/
package org.eclipse.debug.internal.ui.viewers.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.debug.internal.ui.viewers.model.provisional.IChildrenCountUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IElementContentProvider;
import org.eclipse.jface.viewers.TreePath;

/**
 * @since 3.3
 */
class ChildrenCountUpdate extends ViewerUpdateMonitor implements IChildrenCountUpdate {

	private int fCount = 0;
	
	private List fBatchedRequests = null;
	
	/**
	 * Constructor
	 * @param provider the content provider to use for the update
	 * @param viewerInput the current input
	 * @param elementPath the path of the element to update
	 * @param element the element to update
	 * @param elementContentProvider the content provider for the element
	 */
	public ChildrenCountUpdate(TreeModelContentProvider provider, Object viewerInput, TreePath elementPath, Object element, IElementContentProvider elementContentProvider) {
		super(provider, viewerInput, elementPath, element, elementContentProvider, provider.getPresentationContext());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.model.provisional.viewers.ViewerUpdateMonitor#performUpdate()
	 */
	protected void performUpdate() {
		int viewCount = fCount;
		TreePath elementPath = getElementPath();
		if (viewCount == 0) {
			getContentProvider().clearFilters(elementPath);
		} else {
			getContentProvider().setModelChildCount(elementPath, fCount);
			viewCount = getContentProvider().modelToViewChildCount(elementPath, fCount);
		}
		if (TreeModelContentProvider.DEBUG_CONTENT_PROVIDER && TreeModelContentProvider.DEBUG_TEST_PRESENTATION_ID(getPresentationContext())) {
			System.out.println("setChildCount(" + getElement() + ", modelCount: " + fCount + " viewCount: " + viewCount + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}
		getContentProvider().getViewer().setChildCount(elementPath, viewCount);
		getContentProvider().getStateTracker().restorePendingStateOnUpdate(getElementPath(), -1, true, true, false);
	}

	public void setChildCount(int numChildren) {
		fCount = numChildren;
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("IChildrenCountUpdate: "); //$NON-NLS-1$
		buf.append(getElement());
		return buf.toString();
	}	

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.model.ViewerUpdateMonitor#coalesce(org.eclipse.debug.internal.ui.viewers.model.ViewerUpdateMonitor)
	 */
	boolean coalesce(ViewerUpdateMonitor request) {
		if (request instanceof ChildrenCountUpdate) {
			if (getElementPath().equals(request.getElementPath())) {
				// duplicate request
				return true;
			} else if (getElementContentProvider().equals(request.getElementContentProvider())) {
				if (fBatchedRequests == null) {
					fBatchedRequests = new ArrayList(4);
					fBatchedRequests.add(this);
				}
				fBatchedRequests.add(request);
				return true;
			}
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.model.ViewerUpdateMonitor#startRequest()
	 */
	void startRequest() {
		if (fBatchedRequests == null) {
			getElementContentProvider().update(new IChildrenCountUpdate[]{this});
		} else {
			IChildrenCountUpdate[] updates = (IChildrenCountUpdate[]) fBatchedRequests.toArray(new IChildrenCountUpdate[fBatchedRequests.size()]);
			// notify that the other updates have also started to ensure correct sequence
			// of model updates - **** start at index 1 since the first (0) update has
			// already notified the content provider that it has started.
			for (int i = 1; i < updates.length; i++) {
				getContentProvider().updateStarted((ViewerUpdateMonitor) updates[i]);
			}
			getElementContentProvider().update(updates);
		}
	}
	
	boolean containsUpdate(TreePath path) {
	    if (getElementPath().equals(path)) {
	        return true;
	    } else if (fBatchedRequests != null) {
	        for (int i = 0; i < fBatchedRequests.size(); i++) {
	            if (((ViewerUpdateMonitor)fBatchedRequests.get(i)).getElementPath().equals(path)) {
	                return true;
	            }
	        }
	    }
	    return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.model.ViewerUpdateMonitor#getPriority()
	 */
	int getPriority() {
		return 2;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.model.ViewerUpdateMonitor#getSchedulingPath()
	 */
	TreePath getSchedulingPath() {
		TreePath path = getElementPath();
		if (path.getSegmentCount() > 0) {
			return path.getParentPath();
		}
		return path;
	}		
	
	int getCount() {
	    return fCount;
	}
	
    protected boolean doEquals(ViewerUpdateMonitor update) {
        return 
            update instanceof ChildrenCountUpdate && 
            getViewerInput().equals(update.getViewerInput()) && 
            getElementPath().equals(getElementPath());
    }
    
    protected int doHashCode() {
        return getClass().hashCode() + getViewerInput().hashCode() + getElementPath().hashCode();
    }
}
