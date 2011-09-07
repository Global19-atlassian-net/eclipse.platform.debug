/*******************************************************************************
 * Copyright (c) 2009, 2011 Wind River Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *     Dorin Ciuca - Top index fix (Bug 324100)
 *******************************************************************************/
package org.eclipe.debug.tests.viewer.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import junit.framework.Assert;

import org.eclipe.debug.tests.viewer.model.TestModel.TestElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.internal.ui.viewers.model.ElementCompareRequest;
import org.eclipse.debug.internal.ui.viewers.model.IInternalTreeModelViewer;
import org.eclipse.debug.internal.ui.viewers.model.ILabelUpdateListener;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IChildrenCountUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IChildrenUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IHasChildrenUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ILabelUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelChangedListener;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDelta;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelProxy;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IStateUpdateListener;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ITreeModelViewer;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdateListener;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.ViewerFilter;

public class TestModelUpdatesListener 
    implements IViewerUpdateListener, ILabelUpdateListener, IModelChangedListener, ITestModelUpdatesListenerConstants,
        IStateUpdateListener, IJobChangeListener 
{
    public static final ViewerFilter[] EMPTY_FILTER_ARRAY = new ViewerFilter[0]; 
	
    private final ITreeModelViewer fViewer;
    
    private IStatus fJobError;
    
    private boolean fFailOnRedundantUpdates;
    private boolean fFailOnRedundantLabelUpdates;
    private Set fRedundantUpdates = new HashSet();
    private Set fRedundantLabelUpdates = new HashSet();
    private Set fRedundantHasChildrenUpdateExceptions = new HashSet();
    private Set fRedundantChildCountUpdateExceptions = new HashSet();
    private Set fRedundantChildrenUpdateExceptions = new HashSet();
    private Set fRedundantLabelUpdateExceptions = new HashSet();
    
    private boolean fFailOnMultipleModelUpdateSequences;
    private boolean fUnmatchedModelUpdatesObserved;
    private boolean fFailOnMultipleLabelUpdateSequences;
    private boolean fUnmatchedLabelUpdatesObserved;
    
    private Set fHasChildrenUpdatesScheduled = new HashSet();
    private Set fHasChildrenUpdatesRunning = new HashSet();
    private Set fHasChildrenUpdatesCompleted = new HashSet();
    private Map fChildrenUpdatesScheduled = new HashMap();
    private Set fChildrenUpdatesRunning = new HashSet();
    private Set fChildrenUpdatesCompleted = new HashSet();
    private Set fChildCountUpdatesScheduled = new HashSet();
    private Set fChildCountUpdatesRunning = new HashSet();
    private Set fChildCountUpdatesCompleted = new HashSet();
    private Set fLabelUpdates = new HashSet();
    private Set fLabelUpdatesRunning = new HashSet();
    private Set fLabelUpdatesCompleted = new HashSet();
    private Set fProxyModels = new HashSet();
    private Set fStateUpdates = new HashSet();
    private int fViewerUpdatesStarted = 0;
    private int fViewerUpdatesComplete = 0;
    private int fViewerUpdatesStartedAtReset;
    private int fViewerUpdatesCompleteAtReset;
    private int fLabelUpdatesStarted = 0;
    private int fLabelUpdatesComplete = 0;
    private int fLabelUpdatesStartedAtReset;
    private int fLabelUpdatesCompleteAtReset;
    private boolean fModelChangedComplete;
    private boolean fStateSaveStarted;
    private boolean fStateSaveComplete;
    private boolean fStateRestoreStarted;
    private boolean fStateRestoreComplete;
    private int fViewerUpdatesCounter;
    private int fLabelUpdatesCounter;
    private int fTimeoutInterval = 60000;
	private long fTimeoutTime;
	
	
    public TestModelUpdatesListener(ITreeModelViewer viewer, boolean failOnRedundantUpdates, boolean failOnMultipleModelUpdateSequences) {
        this(viewer);
        setFailOnRedundantUpdates(failOnRedundantUpdates);
        setFailOnMultipleModelUpdateSequences(failOnMultipleModelUpdateSequences);
    }

	public TestModelUpdatesListener(ITreeModelViewer viewer) {
	    fViewer = viewer;
        Job.getJobManager().addJobChangeListener(this);
        fViewer.addLabelUpdateListener(this);
        fViewer.addModelChangedListener(this);
        fViewer.addStateUpdateListener(this);
        fViewer.addViewerUpdateListener(this);
    }

	public void dispose() {
        Job.getJobManager().removeJobChangeListener(this);
        fViewer.removeLabelUpdateListener(this);
        fViewer.removeModelChangedListener(this);
        fViewer.removeStateUpdateListener(this);
        fViewer.removeViewerUpdateListener(this);
	}
	
    public void aboutToRun(IJobChangeEvent event) {}
    public void awake(IJobChangeEvent event) {}
    public void running(IJobChangeEvent event) {}
    public void scheduled(IJobChangeEvent event) {}
    public void sleeping(IJobChangeEvent event) {}
    public void done(IJobChangeEvent event) {
        IStatus result = event.getJob().getResult(); 
        if (result != null && result.getSeverity() == IStatus.ERROR) {
            fJobError = result;   
        }
    }
    
    public void setFailOnRedundantUpdates(boolean failOnRedundantUpdates) {
        fFailOnRedundantUpdates = failOnRedundantUpdates;
    }

    public void setFailOnRedundantLabelUpdates(boolean failOnRedundantLabelUpdates) {
        fFailOnRedundantLabelUpdates = failOnRedundantLabelUpdates;
    }

    public void setFailOnMultipleModelUpdateSequences(boolean failOnMultipleLabelUpdateSequences) {
        fFailOnMultipleModelUpdateSequences = failOnMultipleLabelUpdateSequences;
    }

    public void setFailOnMultipleLabelUpdateSequences(boolean failOnMultipleLabelUpdateSequences) {
        fFailOnMultipleLabelUpdateSequences = failOnMultipleLabelUpdateSequences;
    }

    /**
     * Sets the the maximum amount of time (in milliseconds) that the update listener 
     * is going to wait. If set to -1, the listener will wait indefinitely. 
     */
    public void setTimeoutInterval(int milis) {
        fTimeoutInterval = milis;
    }
    
    public void reset(TreePath path, TestElement element, int levels, boolean failOnRedundantUpdates, boolean failOnMultipleUpdateSequences) {
        reset(path, element, EMPTY_FILTER_ARRAY, levels, failOnRedundantUpdates, failOnMultipleUpdateSequences);
    }

    public void reset(TreePath path, TestElement element, ViewerFilter[] filters, int levels, boolean failOnRedundantUpdates, boolean failOnMultipleUpdateSequences) {
        reset();
        addUpdates(path, element, filters, levels);
        addProxies(element);
        setFailOnRedundantUpdates(failOnRedundantUpdates);
        setFailOnMultipleModelUpdateSequences(failOnMultipleUpdateSequences);
        setFailOnMultipleLabelUpdateSequences(false);
    }

    public void reset(boolean failOnRedundantUpdates, boolean failOnMultipleUpdateSequences) {
        reset();
        setFailOnRedundantUpdates(failOnRedundantUpdates);
        setFailOnMultipleModelUpdateSequences(failOnMultipleUpdateSequences);
        setFailOnMultipleLabelUpdateSequences(false);
    }

    public void reset() {
        fJobError = null;
        fRedundantUpdates.clear();
        fRedundantLabelUpdates.clear();
        fRedundantHasChildrenUpdateExceptions.clear();
        fRedundantChildCountUpdateExceptions.clear();
        fRedundantChildrenUpdateExceptions.clear();
        fRedundantLabelUpdateExceptions.clear();
        fHasChildrenUpdatesScheduled.clear();
        fHasChildrenUpdatesRunning.clear();
        fHasChildrenUpdatesCompleted.clear();
        fChildrenUpdatesScheduled.clear();
        fChildrenUpdatesRunning.clear();
        fChildrenUpdatesCompleted.clear();
        fChildCountUpdatesScheduled.clear();
        fChildCountUpdatesRunning.clear();
        fChildCountUpdatesCompleted.clear();
        fLabelUpdates.clear();
        fLabelUpdatesRunning.clear();
        fLabelUpdatesCompleted.clear();
        fProxyModels.clear();
        fViewerUpdatesStartedAtReset = fViewerUpdatesStarted;
        fViewerUpdatesCompleteAtReset = fViewerUpdatesComplete;
        fLabelUpdatesStartedAtReset = fLabelUpdatesStarted;
        fLabelUpdatesCompleteAtReset = fLabelUpdatesComplete;
        fStateUpdates.clear();
        fStateSaveStarted = false;
        fStateSaveComplete = false;
        fStateRestoreStarted = false;
        fStateRestoreComplete = false;
        fTimeoutTime = System.currentTimeMillis() + fTimeoutInterval;
        resetModelChanged();
    }
    
    public void resetModelChanged() {
        fModelChangedComplete = false;
    }
    
    public void addHasChildrenUpdate(TreePath path) {
        fHasChildrenUpdatesScheduled.add(path);
    }

    public void removeHasChildrenUpdate(TreePath path) {
        fHasChildrenUpdatesScheduled.remove(path);
    }

    public void addChildreCountUpdate(TreePath path) {
        fChildCountUpdatesScheduled.add(path);
    }

    public void removeChildreCountUpdate(TreePath path) {
        fChildCountUpdatesScheduled.remove(path);
    }

    public void addChildreUpdate(TreePath path, int index) {
        Set childrenIndexes = (Set)fChildrenUpdatesScheduled.get(path);
        if (childrenIndexes == null) {
            childrenIndexes = new TreeSet();
            fChildrenUpdatesScheduled.put(path, childrenIndexes);
        }
        childrenIndexes.add(new Integer(index));
    }

    public void removeChildrenUpdate(TreePath path, int index) {
        Set childrenIndexes = (Set)fChildrenUpdatesScheduled.get(path);
        if (childrenIndexes != null) {
            childrenIndexes.remove(new Integer(index));
            if (childrenIndexes.isEmpty()) {
                fChildrenUpdatesScheduled.remove(path);
            }
        }
    }

    public void addLabelUpdate(TreePath path) {
        fLabelUpdates.add(path);
    }

    public void removeLabelUpdate(TreePath path) {
        fLabelUpdates.remove(path);
    }

    public void addUpdates(TreePath path, TestElement element, int levels) {
        addUpdates(null, path, element, EMPTY_FILTER_ARRAY, levels, ALL_UPDATES_COMPLETE );
    }
    
    public void addUpdates(TreePath path, TestElement element, ViewerFilter[] filters, int levels) {
        addUpdates(null, path, element, filters, levels, ALL_UPDATES_COMPLETE );
    }

    public void addStateUpdates(IInternalTreeModelViewer viewer, TreePath path, TestElement element) {
        addUpdates(viewer, path, element, -1, STATE_UPDATES);
    }
    
    public void addStateUpdates(IInternalTreeModelViewer viewer, IModelDelta pendingDelta, int deltaFlags) {
    	TreePath treePath = getViewerTreePath(pendingDelta);
    	if ( !TreePath.EMPTY.equals(treePath) && (pendingDelta.getFlags() & deltaFlags) != 0 ) {
    		addUpdates(viewer, treePath, (TestElement)treePath.getLastSegment(), 0, STATE_UPDATES);
    	}
    	IModelDelta[] childDeltas = pendingDelta.getChildDeltas();
        for (int i = 0; i < childDeltas.length; i++) {
            addStateUpdates(viewer, childDeltas[i], deltaFlags);
        }  
    }
    
    public void addRedundantExceptionHasChildren(TreePath path) {
        fRedundantHasChildrenUpdateExceptions.add(path);
    }

    public void addRedundantExceptionChildCount(TreePath path) {
        fRedundantChildCountUpdateExceptions.add(path);
    }

    public void addRedundantExceptionChildren(TreePath path) {
        fRedundantChildrenUpdateExceptions.add(path);
    }

    public void addRedundantExceptionLabel(TreePath path) {
        fRedundantLabelUpdateExceptions.add(path);
    }
    
    public boolean checkCoalesced(TreePath path, int offset, int length) {
        for (Iterator itr = fChildrenUpdatesCompleted.iterator(); itr.hasNext();) {
            IChildrenUpdate update = (IChildrenUpdate)itr.next();
            if (path.equals( update.getElementPath() ) &&
                offset == update.getOffset() &&
                length == update.getLength()) 
            {
                return true;
            }
        }
        return false;
    }
    

    
    public Set getHasChildrenUpdatesCompleted() {
        return fHasChildrenUpdatesCompleted;
    }
    
    public Set getChildCountUpdatesCompleted() {
        return fChildCountUpdatesCompleted;
    }
    
    public Set getChildrenUpdatesCompleted() {
        return fChildrenUpdatesCompleted;
    }
    
    /**
     * Returns a tree path for the node, *not* including the root element.
     * 
     * @param node
     *            model delta
     * @return corresponding tree path
     */
    private TreePath getViewerTreePath(IModelDelta node) {
        ArrayList list = new ArrayList();
        IModelDelta parentDelta = node.getParentDelta();
        while (parentDelta != null) {
            list.add(0, node.getElement());
            node = parentDelta;
            parentDelta = node.getParentDelta();
        }
        return new TreePath(list.toArray());
    }

    public void addUpdates(TreePath path, TestElement element, int levels, int flags) {
        addUpdates(null, path, element, levels, flags);
    }

    public void addUpdates(IInternalTreeModelViewer viewer, TreePath path, TestElement element, int levels, int flags) {
    	addUpdates(viewer, path, element, EMPTY_FILTER_ARRAY,  levels, flags);
    }
    
    public static boolean isFiltered(Object element, ViewerFilter[] filters) {
    	for (int i = 0; i < filters.length; i++) {
    		if (!filters[i].select(null, null, element)) {
    			return true;
    		}
    	}
    	return false;
    }
    
    public void addUpdates(IInternalTreeModelViewer viewer, TreePath path, TestElement element, ViewerFilter[] filters, int levels, int flags) {
    	if (isFiltered(path.getLastSegment(), filters)) {
    		return;
    	}
    	
        if (!path.equals(TreePath.EMPTY)) {
            if ((flags & LABEL_UPDATES) != 0) {
                fLabelUpdates.add(path);
            }
            if ((flags & HAS_CHILDREN_UPDATES) != 0) {
                fHasChildrenUpdatesScheduled.add(path);
            }
            
            if ((flags & STATE_UPDATES) != 0) {
                fStateUpdates.add(path);
            }
        }

        if (levels-- != 0) {
        	TestElement[] children = element.getChildren();
            if (children.length > 0 && (viewer == null || path.getSegmentCount() == 0 || viewer.getExpandedState(path))) {
                if ((flags & CHILD_COUNT_UPDATES) != 0) {
                    fChildCountUpdatesScheduled.add(path);
                }
                if ((flags & CHILDREN_UPDATES) != 0) {
                    Set childrenIndexes = new HashSet();
                    for (int i = 0; i < children.length; i++) {
                    	if (!isFiltered(children[i], filters)) {
                    		childrenIndexes.add(new Integer(i));
                    	}
                    }
                    fChildrenUpdatesScheduled.put(path, childrenIndexes);
                }

                for (int i = 0; i < children.length; i++) {
                    addUpdates(viewer, path.createChildPath(children[i]), children[i], filters, levels, flags);
                }
            }
        
        }
    }

    private void addProxies(TestElement element) {
        TestModel model = element.getModel();
        if (model.getModelProxy() == null) {
            fProxyModels.add(element.getModel());
        }
        TestElement[] children = element.getChildren();
        for (int i = 0; i < children.length; i++) {
            addProxies(children[i]);
        }
    }
    
    public boolean isFinished() {
        return isFinished(ALL_UPDATES_COMPLETE);
    }
    
    public boolean isTimedOut() {
        return fTimeoutInterval > 0 && fTimeoutTime < System.currentTimeMillis();
    }
    
    public boolean isFinished(int flags) {
        if (isTimedOut()) {
            throw new RuntimeException("Timed Out: " + toString(flags));
        }
        
        if (fJobError != null) {
            throw new RuntimeException("Job Error: " + fJobError);
        }
        
        if (fFailOnRedundantUpdates && !fRedundantUpdates.isEmpty()) {
            Assert.fail("Redundant Updates: " + fRedundantUpdates.toString());
        }
        if (fFailOnRedundantLabelUpdates && !fRedundantLabelUpdates.isEmpty()) {
            Assert.fail("Redundant Label Updates: " + fRedundantLabelUpdates.toString());
        }        
        if (fFailOnMultipleLabelUpdateSequences && fLabelUpdatesComplete > (fLabelUpdatesCompleteAtReset + 1)) {
            Assert.fail("Multiple label update sequences detected");
        }
        if (fFailOnMultipleModelUpdateSequences && fViewerUpdatesComplete > (fViewerUpdatesCompleteAtReset + 1)) {
            Assert.fail("Multiple viewer update sequences detected");
        }

        if ( (flags & LABEL_UPDATES_COMPLETE) != 0) {
            if (fUnmatchedLabelUpdatesObserved) {
                throw new RuntimeException("Unmatches labelUpdatesStarted/labelUpdateCompleted notifications observed.");
            }
            
            if (fLabelUpdatesComplete == fLabelUpdatesCompleteAtReset) return false;
        }
        if ( (flags & LABEL_UPDATES_STARTED) != 0) {
            if (fLabelUpdatesStarted == fLabelUpdatesStartedAtReset) return false;
        }
        if ( (flags & LABEL_UPDATES) != 0) {
            if (!fLabelUpdates.isEmpty()) return false;
        }
        if ( (flags & CONTENT_UPDATES_STARTED) != 0) {
            if (fViewerUpdatesStarted == fViewerUpdatesStartedAtReset) return false;
        }
        if ( (flags & CONTENT_UPDATES_COMPLETE) != 0) {
            if (fUnmatchedModelUpdatesObserved) {
                throw new RuntimeException("Unmatches updatesStarted/updateCompleted notifications observed.");
            }
            
            if (fViewerUpdatesComplete == fViewerUpdatesCompleteAtReset) return false;
        }
        if ( (flags & HAS_CHILDREN_UPDATES_STARTED) != 0) {
            if (fHasChildrenUpdatesRunning.isEmpty() && fHasChildrenUpdatesCompleted.isEmpty()) return false;
        }
        if ( (flags & HAS_CHILDREN_UPDATES) != 0) {
            if (!fHasChildrenUpdatesScheduled.isEmpty()) return false;
        }
        if ( (flags & CHILD_COUNT_UPDATES_STARTED) != 0) {
            if (fChildCountUpdatesRunning.isEmpty() && fChildCountUpdatesCompleted.isEmpty()) return false;
        }
        if ( (flags & CHILD_COUNT_UPDATES) != 0) {
            if (!fChildCountUpdatesScheduled.isEmpty()) return false;
        }
        if ( (flags & CHILDREN_UPDATES_STARTED) != 0) {
            if (fChildrenUpdatesRunning.isEmpty() && fChildrenUpdatesCompleted.isEmpty()) return false;
        }
        if ( (flags & CHILDREN_UPDATES) != 0) {
            if (!fChildrenUpdatesScheduled.isEmpty()) return false;
        }
        if ( (flags & MODEL_CHANGED_COMPLETE) != 0) {
            if (!fModelChangedComplete) return false;
        }
        if ( (flags & STATE_SAVE_COMPLETE) != 0) {
            if (!fStateSaveComplete) return false;
        }
        if ( (flags & STATE_SAVE_STARTED) != 0) {
            if (!fStateSaveStarted) return false;
        }
        if ( (flags & STATE_RESTORE_COMPLETE) != 0) {
            if (!fStateRestoreComplete) return false;
        }
        if ( (flags & STATE_RESTORE_STARTED) != 0) {
            if (!fStateRestoreStarted) return false;
        }
        if ( (flags & STATE_UPDATES) != 0) {
            if (!fStateUpdates.isEmpty()) {
                return false;
            }
        }
        if ( (flags & MODEL_PROXIES_INSTALLED) != 0) {
            if (fProxyModels.size() != 0) return false;
        }
        if ( (flags & VIEWER_UPDATES_RUNNING) != 0) {
            if (fViewerUpdatesCounter != 0) {
            	return false;
            }
        }
        if ( (flags & LABEL_UPDATES_RUNNING) != 0) {
            if (fLabelUpdatesCounter != 0) {
            	return false;
            }
        }

        
        return true;
    }
    
    public void updateStarted(IViewerUpdate update) {
        synchronized (this) {
        	fViewerUpdatesCounter++;
            if (update instanceof IHasChildrenUpdate) {
                fHasChildrenUpdatesRunning.add(update);
            } if (update instanceof IChildrenCountUpdate) {
                fChildCountUpdatesRunning.add(update);
            } else if (update instanceof IChildrenUpdate) {
                fChildrenUpdatesRunning.add(update);
            } 
        }
    }
    
    public void updateComplete(IViewerUpdate update) {
        synchronized (this) {
        	fViewerUpdatesCounter--;
        }

        if (!update.isCanceled()) {
            TreePath updatePath = update.getElementPath();
            if (update instanceof IHasChildrenUpdate) {
                fHasChildrenUpdatesRunning.remove(update);
                fHasChildrenUpdatesCompleted.add(update);                
                if (!fHasChildrenUpdatesScheduled.remove(updatePath) &&
                    fFailOnRedundantUpdates && 
                    fRedundantHasChildrenUpdateExceptions.contains(updatePath)) 
                {
                    fRedundantUpdates.add(update);
                }
            } if (update instanceof IChildrenCountUpdate) {
                fChildCountUpdatesRunning.remove(update);
                fChildCountUpdatesCompleted.add(update);                
                if (!fChildCountUpdatesScheduled.remove(updatePath) &&
                    fFailOnRedundantUpdates &&
                    !fRedundantChildCountUpdateExceptions.contains(updatePath)) 
                {
                    fRedundantUpdates.add(update);
                }
            } else if (update instanceof IChildrenUpdate) {
                fChildrenUpdatesRunning.remove(update);
                fChildrenUpdatesCompleted.add(update);                
                
                int start = ((IChildrenUpdate)update).getOffset();
                int end = start + ((IChildrenUpdate)update).getLength();
                
                Set childrenIndexes = (Set)fChildrenUpdatesScheduled.get(updatePath);
                if (childrenIndexes != null) {
                    for (int i = start; i < end; i++) {
                        childrenIndexes.remove(new Integer(i));
                    }
                    if (childrenIndexes.isEmpty()) {
                        fChildrenUpdatesScheduled.remove(updatePath);
                    }
                } else if (fFailOnRedundantUpdates && fRedundantChildrenUpdateExceptions.contains(updatePath)) {
                    fRedundantUpdates.add(update);
                }
            } 
        }
    }
    
    public void viewerUpdatesBegin() {
        if (fViewerUpdatesStarted > fViewerUpdatesComplete) {
            fUnmatchedModelUpdatesObserved = true;
        }
        fViewerUpdatesStarted++;
    }
    
    public void viewerUpdatesComplete() {
        if (fViewerUpdatesStarted <= fViewerUpdatesComplete) {
            fUnmatchedModelUpdatesObserved = true;
        }
        fViewerUpdatesComplete++;
    }

    public void labelUpdateComplete(ILabelUpdate update) {
        fLabelUpdatesRunning.remove(update);
        fLabelUpdatesCompleted.add(update);
    	fLabelUpdatesCounter--;
        if (!fLabelUpdates.remove(update.getElementPath()) && 
            fFailOnRedundantLabelUpdates && 
            !fRedundantLabelUpdateExceptions.contains(update.getElementPath())) 
        {
        	fRedundantLabelUpdates.add(update);
            Assert.fail("Redundant update: " + update);
        }
    }

    public void labelUpdateStarted(ILabelUpdate update) {
        fLabelUpdatesRunning.add(update);
    	fLabelUpdatesCounter++;
    }

    public void labelUpdatesBegin() {
        if (fLabelUpdatesStarted > fLabelUpdatesComplete) {
            fUnmatchedLabelUpdatesObserved = true;
        }
        fLabelUpdatesStarted++;
    }

    public void labelUpdatesComplete() {
        if (fLabelUpdatesStarted <= fLabelUpdatesComplete) {
            fUnmatchedLabelUpdatesObserved = true;
        }
        fLabelUpdatesComplete++;
    }
    
    public void modelChanged(IModelDelta delta, IModelProxy proxy) {
        fModelChangedComplete = true;

        for (Iterator itr = fProxyModels.iterator(); itr.hasNext();) {
            TestModel model = (TestModel)itr.next();
            if (model.getModelProxy() == proxy) {
                itr.remove();
                break;
            }
        }
    }
    
    public void stateRestoreUpdatesBegin(Object input) {
        fStateRestoreStarted = true;
    }
    
    public void stateRestoreUpdatesComplete(Object input) {
    	Assert.assertFalse("RESTORE STATE already complete!", fStateRestoreComplete);
        fStateRestoreComplete = true;
    }
    
    public void stateSaveUpdatesBegin(Object input) {
        fStateSaveStarted = true;
    }

    public void stateSaveUpdatesComplete(Object input) {
        fStateSaveComplete = true;
    }
    
    public void stateUpdateComplete(Object input, IViewerUpdate update) {
        if ( !(update instanceof ElementCompareRequest) || ((ElementCompareRequest)update).isEqual()) {
            fStateUpdates.remove(update.getElementPath());
        } 
    }
    
    public void stateUpdateStarted(Object input, IViewerUpdate update) {
    }
    
    private String toString(int flags) {
        StringBuffer buf = new StringBuffer("Viewer Update Listener");

        if (fJobError != null) {
            buf.append("\n\t");
            buf.append("fJobError = " + fJobError);
            if (fJobError.getException() != null) {
                StackTraceElement[] trace = fJobError.getException().getStackTrace();
                for (int i = 0; i < trace.length; i++) {
                    buf.append("\n\t\t");    
                    buf.append(trace[i]);
                }
            }
        }
       
        if (fFailOnRedundantUpdates) {
            buf.append("\n\t");
            buf.append("fRedundantUpdates = " + fRedundantUpdates);
        }
        if ( (flags & LABEL_UPDATES_COMPLETE) != 0) {
            buf.append("\n\t");
            buf.append("fLabelUpdatesComplete = " + fLabelUpdatesComplete);
        }
        if ( (flags & LABEL_UPDATES_RUNNING) != 0) {
            buf.append("\n\t");
            buf.append("fLabelUpdatesRunning = " + fLabelUpdatesCounter);
        }
        if ( (flags & LABEL_UPDATES_STARTED) != 0) {
            buf.append("\n\t");
            buf.append("fLabelUpdatesStarted = ");
            buf.append( fLabelUpdatesStarted );
            buf.append("\n\t");
            buf.append("fLabelUpdatesCompleted = ");
            buf.append( fLabelUpdatesCompleted );
        }
        if ( (flags & LABEL_UPDATES) != 0) {
            buf.append("\n\t");
            buf.append("fLabelUpdates = ");
            buf.append( toString(fLabelUpdates) );
        }
        if ( (flags & VIEWER_UPDATES_RUNNING) != 0) {
            buf.append("\n\t");
            buf.append("fViewerUpdatesStarted = " + fViewerUpdatesStarted);
            buf.append("\n\t");
            buf.append("fViewerUpdatesRunning = " + fViewerUpdatesCounter);
        }
        if ( (flags & CONTENT_UPDATES_COMPLETE) != 0) {
            buf.append("\n\t");
            buf.append("fViewerUpdatesComplete = " + fViewerUpdatesComplete);
        }
        if ( (flags & HAS_CHILDREN_UPDATES_STARTED) != 0) {
            buf.append("\n\t");
            buf.append("fHasChildrenUpdatesRunning = ");
            buf.append( fHasChildrenUpdatesRunning );
            buf.append("\n\t");
            buf.append("fHasChildrenUpdatesCompleted = ");
            buf.append( fHasChildrenUpdatesCompleted );
        }
        if ( (flags & HAS_CHILDREN_UPDATES) != 0) {
            buf.append("\n\t");
            buf.append("fHasChildrenUpdates = ");
            buf.append( toString(fHasChildrenUpdatesScheduled) );
        }
        if ( (flags & CHILD_COUNT_UPDATES_STARTED) != 0) {
            buf.append("\n\t");
            buf.append("fChildCountUpdatesRunning = ");
            buf.append( fChildCountUpdatesRunning );
            buf.append("\n\t");
            buf.append("fChildCountUpdatesCompleted = ");
            buf.append( fChildCountUpdatesCompleted );
        }
        if ( (flags & CHILD_COUNT_UPDATES) != 0) {
            buf.append("\n\t");
            buf.append("fChildCountUpdates = ");
            buf.append( toString(fChildCountUpdatesScheduled) );
        }
        if ( (flags & CHILDREN_UPDATES_STARTED) != 0) {
            buf.append("\n\t");
            buf.append("fChildrenUpdatesRunning = ");
            buf.append( fChildrenUpdatesRunning );
            buf.append("\n\t");
            buf.append("fChildrenUpdatesCompleted = ");
            buf.append( fChildrenUpdatesCompleted );
        }
        if ( (flags & CHILDREN_UPDATES) != 0) {
            buf.append("\n\t");
            buf.append("fChildrenUpdates = ");
            buf.append( toString(fChildrenUpdatesScheduled) );
        }
        if ( (flags & MODEL_CHANGED_COMPLETE) != 0) {
            buf.append("\n\t");
            buf.append("fModelChangedComplete = " + fModelChangedComplete);
        }
        if ( (flags & STATE_SAVE_COMPLETE) != 0) {
            buf.append("\n\t");
            buf.append("fStateSaveComplete = " + fStateSaveComplete);
        }
        if ( (flags & STATE_RESTORE_COMPLETE) != 0) {
            buf.append("\n\t");
            buf.append("fStateRestoreComplete = " + fStateRestoreComplete);
        }
        if ( (flags & MODEL_PROXIES_INSTALLED) != 0) {
            buf.append("\n\t");
            buf.append("fProxyModels = " + fProxyModels);
        }
        if ( (flags & STATE_UPDATES) != 0) {
        	buf.append("\n\t");
        	buf.append("fStateUpdates = " + toString(fStateUpdates));
        }
        if (fTimeoutInterval > 0) {
            buf.append("\n\t");
            buf.append("fTimeoutInterval = " + fTimeoutInterval);
        }
        return buf.toString();
    }

    private String toString(Set set) {
        if (set.isEmpty()) {
            return "(EMPTY)";
        }
        StringBuffer buf = new StringBuffer();
        for (Iterator itr = set.iterator(); itr.hasNext(); ) {
            buf.append("\n\t\t");
            buf.append(toString((TreePath)itr.next()));
        }
        return buf.toString();
    }
    
    private String toString(Map map) {
        if (map.isEmpty()) {
            return "(EMPTY)";
        }
        StringBuffer buf = new StringBuffer();
        for (Iterator itr = map.keySet().iterator(); itr.hasNext(); ) {
            buf.append("\n\t\t");
            TreePath path = (TreePath)itr.next();
            buf.append(toString(path));
            Set updates = (Set)map.get(path);
            buf.append(" = ");
            buf.append(updates.toString());
        }
        return buf.toString();
    }
    
    private String toString(TreePath path) {
        if (path.getSegmentCount() == 0) {
            return "/";
        }
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < path.getSegmentCount(); i++) {
            buf.append("/");
            buf.append(path.getSegment(i));
        }
        return buf.toString();
    }
    
    public String toString() {
        return toString(ALL_UPDATES_COMPLETE | MODEL_CHANGED_COMPLETE | STATE_RESTORE_COMPLETE | VIEWER_UPDATES_STARTED | LABEL_UPDATES_STARTED | STATE_UPDATES);
    }
    
    
}


