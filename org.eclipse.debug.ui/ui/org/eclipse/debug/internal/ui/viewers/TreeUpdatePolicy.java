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
package org.eclipse.debug.internal.ui.viewers;

import java.util.ArrayList;

import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelChangedListener;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDelta;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelProxy;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;

/**
 * Default update policy updates a viewer based on model deltas.
 * 
 * @since 3.2
 */
public class TreeUpdatePolicy extends AbstractUpdatePolicy implements IModelChangedListener {
	
	// cache of latest tree path for a node
	private TreePath fTreePath;
	private IModelDelta fNode;

    public void modelChanged(IModelDelta delta, IModelProxy proxy) {
        updateNodes(new IModelDelta[] { delta });
        fTreePath = null;
        fNode = null;
    }

    protected void updateNodes(IModelDelta[] nodes) {
        AsynchronousTreeViewer viewer = (AsynchronousTreeViewer) getViewer();
        if (viewer == null) {
            return;
        }

        for (int i = 0; i < nodes.length; i++) {
            IModelDelta node = nodes[i];
            int flags = node.getFlags();

            if ((flags & IModelDelta.ADDED) != 0) {
                handleAdd(viewer, node);
            }
            if ((flags & IModelDelta.REMOVED) != 0) {
                handleRemove(viewer, node);
            }
            if ((flags & IModelDelta.CONTENT) != 0) {
                handleContent(viewer, node);
            }
            if ((flags & IModelDelta.EXPAND) != 0) {
                handleExpand(viewer, node);
            }
            if ((flags & IModelDelta.SELECT) != 0) {
                handleSelect(viewer, node);
            }
            if ((flags & IModelDelta.STATE) != 0) {
                handleState(viewer, node);
            }
            if ((flags & IModelDelta.INSERTED) != 0) {
                // TODO
            }
            if ((flags & IModelDelta.REPLACED) != 0) {
                // TODO
            }

            updateNodes(node.getChildDeltas());
        }
    }

    protected void handleState(AsynchronousTreeViewer viewer, IModelDelta delta) {
        viewer.update(delta.getElement());
    }

    protected void handleSelect(AsynchronousTreeViewer viewer, IModelDelta delta) {
        viewer.setSelection(new TreeSelection(getTreePath(delta)));
    }

    protected void handleExpand(AsynchronousTreeViewer viewer, IModelDelta delta) {
        viewer.expand(new TreeSelection(getTreePath(delta)));
    }

    protected void handleContent(AsynchronousTreeViewer viewer, IModelDelta delta) {
        viewer.refresh(delta.getElement());
    }

    protected void handleRemove(AsynchronousTreeViewer viewer, IModelDelta delta) {
        viewer.remove(getTreePath(delta));
    }

    protected void handleAdd(AsynchronousTreeViewer viewer, IModelDelta delta) {
        viewer.add(getTreePath(delta));
    }

    protected TreePath getTreePath(IModelDelta node) {
    	if (node != fNode) {
            ArrayList list = new ArrayList();
            list.add(0, node.getElement());
            while (node.getParentDelta() != null) {
                node = node.getParentDelta();
                list.add(0, node.getElement());
            }

            fTreePath = new TreePath(list.toArray());
            fNode = node;
    	}
        return fTreePath;
    }

}
