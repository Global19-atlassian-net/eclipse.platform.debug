/*******************************************************************************
 *  Copyright (c) 2000, 2010 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     Wind River Systems - refactored on top of VirtualTreeModelViewer
 *******************************************************************************/
package org.eclipse.debug.internal.ui.viewers.model;

 
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.internal.core.IInternalDebugCoreConstants;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.actions.AbstractDebugActionDelegate;
import org.eclipse.debug.internal.ui.actions.ActionMessages;
import org.eclipse.debug.internal.ui.elements.adapters.VariableColumnPresentation;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IChildrenUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ILabelUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDelta;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDeltaVisitor;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IPresentationContext;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdateListener;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IVirtualItemValidator;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ModelDelta;
import org.eclipse.debug.internal.ui.viewers.model.provisional.PresentationContext;
import org.eclipse.debug.internal.ui.viewers.model.provisional.TreeModelViewer;
import org.eclipse.debug.internal.ui.viewers.model.provisional.VirtualItem;
import org.eclipse.debug.internal.ui.viewers.model.provisional.VirtualItem.Index;
import org.eclipse.debug.internal.ui.viewers.model.provisional.VirtualTreeModelViewer;
import org.eclipse.debug.ui.IDebugView;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public class VirtualCopyToClipboardActionDelegate extends AbstractDebugActionDelegate {
	
	private TreeModelViewer fClientViewer;
	private static final String TAB = "\t"; //$NON-NLS-1$
	private static final String SEPARATOR = "line.separator"; //$NON-NLS-1$
	
	private class VirtualViewerListener implements IViewerUpdateListener, ILabelUpdateListener {
        
	    private IProgressMonitor fProgressMonitor;
	    private int fRemainingUpdatesCount; 
        private boolean fViewerUpdatesComplete;
        private boolean fLabelUpdatesComplete;
        private int fSelectionRootDepth;
        
        public void labelUpdateStarted(ILabelUpdate update) {}
        public void labelUpdateComplete(ILabelUpdate update) {
            incrementProgress(1);            
        }
        public void labelUpdatesBegin() {
            fLabelUpdatesComplete = false;          
        }
        public void labelUpdatesComplete() {
            fLabelUpdatesComplete = true;
            completeProgress();
        }
        
        public void updateStarted(IViewerUpdate update) {}
        public void updateComplete(IViewerUpdate update) {
            if (update instanceof IChildrenUpdate) {
                incrementProgress(((IChildrenUpdate)update).getLength());
            }
        }
        public void viewerUpdatesBegin() {
            fViewerUpdatesComplete = false;            
        }
        public void viewerUpdatesComplete() {
            fViewerUpdatesComplete = true;
            completeProgress();
        }
        
        private void completeProgress() {
            IProgressMonitor pm;
            synchronized (VirtualCopyToClipboardActionDelegate.this) {
                pm = fProgressMonitor;
            }
            if (pm != null && fLabelUpdatesComplete && fViewerUpdatesComplete) {
                pm.done();
            }            
        }
        
        private void incrementProgress(int count) {
            IProgressMonitor pm;
            synchronized (VirtualCopyToClipboardActionDelegate.this) {
                pm = fProgressMonitor;
                fRemainingUpdatesCount -= count;
            }
            if (pm != null && fLabelUpdatesComplete && fViewerUpdatesComplete) {
                pm.worked(count);
            }                        
        }

    }

	
	/**
	 * @see AbstractDebugActionDelegate#initialize(IAction, ISelection)
	 */
	protected boolean initialize(IAction action, ISelection selection) {
		if (!isInitialized()) {
			IDebugView adapter= (IDebugView)getView().getAdapter(IDebugView.class);
			if (adapter != null) {
				if (adapter.getViewer() instanceof TreeModelViewer) {
					setViewer((TreeModelViewer) adapter.getViewer());
				}
				adapter.setAction(getActionId(), action);
			}
			return super.initialize(action, selection);
		} 
		return false;
	}

	protected String getActionId() {
		return IDebugView.COPY_ACTION;
	}

	/** 
	 * Appends the representation of the specified element (using the label provider and indent)
	 * to the buffer.  For elements down to stack frames, children representations
	 * are append to the buffer as well.
	 * @param item Item to append to string
	 * @param buffer String buffer for copy text.
	 * @param indent Current indentation in tree text.
	 */
	protected void append(VirtualItem item, StringBuffer buffer, int indent) {
		for (int i= 0; i < indent; i++) {
			buffer.append(TAB);
		}
		String[] labels = (String[]) item.getData(VirtualItem.LABEL_KEY);
		if(labels != null && labels.length > 0) {
			for (int i = 0; i < labels.length; i++) {
				String text = labels[i];
				if(text != null && !text.trim().equals(IInternalDebugCoreConstants.EMPTY_STRING)) {
					buffer.append(text+TAB);
				}
			}
			buffer.append(System.getProperty(SEPARATOR));
		}
	}
	
    private IPresentationContext makeVirtualPresentationContext(final IPresentationContext clientViewerContext) {
        return new PresentationContext(clientViewerContext.getId()) {
            
            {
                String[] clientProperties = clientViewerContext.getProperties();
                for (int i = 0; i < clientProperties.length; i++) {
                    setProperty(clientProperties[i], clientViewerContext.getProperty(clientProperties[i]));
                }
                    
            }
            
            public String[] getColumns() {
                String[] clientColumns = super.getColumns();
                
                if (clientColumns == null || clientColumns.length == 0) {
                    // No columns are used.
                    return null;
                }
                
                // Try to find the name column.
                for (int i = 0; i < clientColumns.length; i++) {
                    if (VariableColumnPresentation.COLUMN_VARIABLE_NAME.equals(clientColumns[i])) {
                        return new String[] { VariableColumnPresentation.COLUMN_VARIABLE_NAME }; 
                    }
                }
                
                return new String[] { clientColumns[0] };
            }
        };
    }
	
	private int calcUpdatesCount(IModelDelta stateDelta) {
        final int[] count = new int[] {0};
        stateDelta.accept( new IModelDeltaVisitor() {
            public boolean visit(IModelDelta delta, int depth) {
                if ((delta.getFlags() & (IModelDelta.EXPAND | IModelDelta.SELECT)) != 0) {
                    count[0]++;
                    return true;
                }
                return false;
            }
        });
        
        // Double it to account for separate element and label update ticks.
        return count[0] * 2;
    }
	
	private class ItemsToCopyVirtualItemValidator implements IVirtualItemValidator {
	    
	    Set fItemsToCopy = Collections.EMPTY_SET;
	    
	    public boolean isItemVisible(VirtualItem item) {
	        return fItemsToCopy.contains(item);
	    }
	    
	    public void showItem(VirtualItem item) {
	    }
	}
	
	private VirtualTreeModelViewer initVirtualViewer(TreeModelViewer clientViewer, VirtualViewerListener listener, ItemsToCopyVirtualItemValidator validator) {
        Object input = clientViewer.getInput();
        ModelDelta stateDelta = new ModelDelta(input, IModelDelta.NO_CHANGE);
        clientViewer.saveElementState(TreePath.EMPTY, stateDelta, IModelDelta.EXPAND);
        listener.fRemainingUpdatesCount = calcUpdatesCount(stateDelta);
        VirtualTreeModelViewer virtualViewer = new VirtualTreeModelViewer(
            clientViewer.getDisplay(), 
            SWT.VIRTUAL, 
            makeVirtualPresentationContext(clientViewer.getPresentationContext()), 
            validator); 
        virtualViewer.addViewerUpdateListener(listener);
        virtualViewer.addLabelUpdateListener(listener);
        virtualViewer.setInput(input);
        virtualViewer.updateViewer(stateDelta);
        
        // Parse selected items from client viewer and add them to the virtual viewer selection.
        listener.fSelectionRootDepth = Integer.MAX_VALUE;
        TreeItem[] selection = clientViewer.getTree().getSelection();
        Set vSelection = new HashSet(selection.length * 4/3);
        for (int i = 0; i < selection.length; i++) {
            TreePath parentPath = fClientViewer.getTreePathFromItem(selection[i].getParentItem());
            listener.fSelectionRootDepth = Math.min(parentPath.getSegmentCount() + 1, listener.fSelectionRootDepth);
            VirtualItem parentVItem = virtualViewer.findItem(parentPath);
            if (parentVItem != null) {
                int index = -1;
                TreeItem parentItem = selection[i].getParentItem();
                if (parentItem != null) {
                    index = parentItem.indexOf(selection[i]);
                } else {
                    Tree parentTree = selection[i].getParent();
                    index = parentTree.indexOf(selection[i]);
                }
                vSelection.add( parentVItem.getItem(new Index(index)) );
                if (!selection[i].getExpanded()) {
                    listener.fRemainingUpdatesCount += 2;
                }
            }
        }
        validator.fItemsToCopy = vSelection;
        virtualViewer.getTree().validate();
        return virtualViewer;
	}
	
	protected TreeItem[] getSelectedItems(TreeModelViewer clientViewer) {
	    return clientViewer.getTree().getSelection();
	}
	
	/**
	 * Do the specific action using the current selection.
	 * @param action Action that is running.
	 */
	public void run(final IAction action) {
	    if (fClientViewer.getSelection().isEmpty()) {
	        writeBufferToClipboard(new StringBuffer(""));
	        return;
	    }
	    
		final VirtualViewerListener listener = new VirtualViewerListener();
		ItemsToCopyVirtualItemValidator validator = new ItemsToCopyVirtualItemValidator();
		VirtualTreeModelViewer virtualViewer = initVirtualViewer(fClientViewer, listener, validator);
		
		ProgressMonitorDialog dialog = new TimeTriggeredProgressMonitorDialog(fClientViewer.getControl().getShell(), 500);
		final IProgressMonitor monitor = dialog.getProgressMonitor();
		dialog.setCancelable(true);
				 
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			public void run(final IProgressMonitor m) throws InvocationTargetException, InterruptedException {
	            synchronized(listener) {
	                listener.fProgressMonitor = m;
	                listener.fProgressMonitor.beginTask(DebugUIPlugin.removeAccelerators(getAction().getText()), listener.fRemainingUpdatesCount);
	            }
	            
	            while ((!listener.fLabelUpdatesComplete || !listener.fViewerUpdatesComplete) && !listener.fProgressMonitor.isCanceled()) {
	                Thread.sleep(1);
	            } 
	            synchronized(listener) {
	                listener.fProgressMonitor = null;
	            }
			}
		};
		try {
			dialog.run(true, true, runnable);
		} catch (InvocationTargetException e) {
			DebugUIPlugin.log(e);
			return;
		} catch (InterruptedException e) {
			return;
		}

		if (!monitor.isCanceled()) {
		    copySelectionToClipboard(virtualViewer, validator.fItemsToCopy, listener.fSelectionRootDepth);
		}
		
        virtualViewer.removeLabelUpdateListener(listener);
        virtualViewer.removeViewerUpdateListener(listener);
		virtualViewer.dispose();
	}

	private void copySelectionToClipboard(VirtualTreeModelViewer virtualViewer, Set itemsToCopy, int selectionRootDepth) {
        StringBuffer buffer = new StringBuffer();
        writeItemToBuffer (virtualViewer.getTree(), itemsToCopy, buffer, -selectionRootDepth);
        writeBufferToClipboard(buffer);
	}
	
	protected void writeItemToBuffer(VirtualItem item, Set itemsToCopy, StringBuffer buffer, int indent) {
	    if (itemsToCopy.contains(item)) {
	        append(item, buffer, indent);
	    }
		VirtualItem[] children = item.getItems();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				writeItemToBuffer(children[i], itemsToCopy, buffer, indent + 1);
			}
		}
	}

	protected void writeBufferToClipboard(StringBuffer buffer) {
        TextTransfer plainTextTransfer = TextTransfer.getInstance();
        Clipboard clipboard= new Clipboard(fClientViewer.getControl().getDisplay());        
		try {
			clipboard.setContents(
					new String[]{buffer.toString()}, 
					new Transfer[]{plainTextTransfer});
		} catch (SWTError e){
			if (e.code != DND.ERROR_CANNOT_SET_CLIPBOARD) {
				throw e;
			}
			if (MessageDialog.openQuestion(fClientViewer.getControl().getShell(), ActionMessages.CopyToClipboardActionDelegate_Problem_Copying_to_Clipboard_1, ActionMessages.CopyToClipboardActionDelegate_There_was_a_problem_when_accessing_the_system_clipboard__Retry__2)) { // 
				writeBufferToClipboard(buffer);
			}
		} finally {
		    clipboard.dispose();
		}
	}
	
	protected TreeModelViewer getViewer() {
		return fClientViewer;
	}

	protected void setViewer(TreeModelViewer viewer) {
		fClientViewer = viewer;
	}
	/**
	 * @see AbstractDebugActionDelegate#doAction(Object)
	 */
	protected void doAction(Object element) {
		//not used
	}
	
	protected boolean getEnableStateForSelection(IStructuredSelection selection) {
	    if (selection.isEmpty()) {
	        return true;
	    } else {
	        return super.getEnableStateForSelection(selection);
	    }
	}
}
