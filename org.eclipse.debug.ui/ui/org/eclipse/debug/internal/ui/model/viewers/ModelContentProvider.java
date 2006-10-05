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
package org.eclipse.debug.internal.ui.model.viewers;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.model.IElementContentProvider;
import org.eclipse.debug.internal.ui.model.IElementMementoProvider;
import org.eclipse.debug.internal.ui.model.IElementMementoRequest;
import org.eclipse.debug.internal.ui.viewers.provisional.IAsynchronousRequestMonitor;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelChangedListener;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelDelta;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelDeltaVisitor;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelProxy;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelProxyFactoryAdapter;
import org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext;
import org.eclipse.debug.internal.ui.viewers.provisional.ModelDelta;
import org.eclipse.debug.ui.AbstractDebugView;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.XMLMemento;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.progress.WorkbenchJob;

/**
 * Content provider for a virtual viewer.
 * 
 * @since 3.3
 */
abstract class ModelContentProvider implements IContentProvider,
		IModelChangedListener {

	private Viewer fViewer;

	private Map fModelProxies = new HashMap(); // model proxy by element
	
	/**
	 * Map of nodes that have been filtered from the viewer.
	 */
	private FilterTransform fTransform = new FilterTransform();
	
	/**
	 * Model listeners
	 */
	private ListenerList fModelListeners = new ListenerList();
	
	/**
	 * Update listeners
	 */
	private ListenerList fUpdateListeners = new ListenerList();
	
	/**
	 * Nesting count of updates
	 */
	private int fUpdateNestingCount = 0;
	
	/**
	 * Map of viewer states keyed by viewer input mementos
	 */
	private Map fViewerStates = new LRUMap(20);
	
	/**
	 * Pending viewer state to be restored
	 */
	private ModelDelta fPendingState = null;
	
	/**
	 * Used to determine when restoration delta has been processed
	 */
	class CheckState implements IModelDeltaVisitor {
		private boolean complete = true;
		/* (non-Javadoc)
		 * @see org.eclipse.debug.internal.ui.viewers.provisional.IModelDeltaVisitor#visit(org.eclipse.debug.internal.ui.viewers.provisional.IModelDelta, int)
		 */
		public boolean visit(IModelDelta delta, int depth) {
			if (delta.getFlags() != IModelDelta.NO_CHANGE) {
				complete = false;
				return false;
			}
			return true;
		}
		
		public boolean isComplete() {
			return complete;
		}
	}
	
	/**
	 * LRU cache for viewer states 
	 */
	class LRUMap extends LinkedHashMap {
		private static final long serialVersionUID= 1L;
		private int fMaxSize;
		LRUMap(int maxSize) {
			super();
			fMaxSize = maxSize;
		}
		protected boolean removeEldestEntry(Entry eldest) {
			return size() > fMaxSize;
		}	
	}
	
	/**
	 * Update type constants
	 */
	private static final int UPDATE_SEQUENCE_BEGINS = 0;
	private static final int UPDATE_SEQUENCE_COMPLETE = 1;
	private static final int UPDATE_BEGINS = 2;
	private static final int UPDATE_COMPLETE = 3;
	
	/**
	 * Constant for an empty tree path.
	 */
	protected static final TreePath EMPTY_TREE_PATH = new TreePath(new Object[]{});

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
	 */
	public synchronized void dispose() {
		fModelListeners.clear();
		fUpdateListeners.clear();
		disposeAllModelProxies();
		fViewer = null;
	}
	
	public synchronized boolean isDisposed() {
		return fViewer == null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer,
	 *      java.lang.Object, java.lang.Object)
	 */
	public synchronized void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		fViewer = viewer;
		if (oldInput != null) {
			saveViewerState(oldInput);
		}
		if (newInput != oldInput) {
			disposeAllModelProxies();
			fTransform.clear();
			if (newInput != null) {
				installModelProxy(newInput);
				restoreViewerState(newInput);
			}
		}
	}

	/**
	 * Restores viewer state for the new input
	 * 
	 * @param newInput
	 */
	protected void restoreViewerState(final Object input) {
		fPendingState = null;
		final IElementMementoProvider stateProvider = getViewerStateAdapter(input);
		if (stateProvider != null) {
			// build a model delta representing expansion and selection state
			final ModelDelta delta = new ModelDelta(input, IModelDelta.NO_CHANGE);
			final XMLMemento inputMemento = XMLMemento.createWriteRoot("VIEWER_INPUT_MEMENTO"); //$NON-NLS-1$
			final IMementoManager manager = new IMementoManager() {
			
				private IElementMementoRequest fRequest;
				
				/* (non-Javadoc)
				 * @see org.eclipse.debug.internal.ui.model.viewers.IMementoManager#requestComplete(org.eclipse.debug.internal.ui.model.IElementMementoRequest)
				 */
				public synchronized void requestComplete(IElementMementoRequest request) {
					if (!request.isCanceled() && (request.getStatus() == null || request.getStatus().isOK())) {
						XMLMemento keyMemento = (XMLMemento) delta.getElement();
						StringWriter writer = new StringWriter();
						try {
							keyMemento.save(writer);
							final ModelDelta stateDelta = (ModelDelta) fViewerStates.remove(writer.toString());
							if (stateDelta != null) {
								//System.out.println("RESTORE: " + stateDelta.toString());
								stateDelta.setElement(input);
								// begin restoration
								UIJob job = new UIJob("restore state") {
									public IStatus runInUIThread(IProgressMonitor monitor) {
										if (input.equals(getViewer().getInput())) {
											fPendingState = stateDelta;
											doInitialRestore();
										}
										return Status.OK_STATUS;
									}
								
								};
								job.setSystem(true);
								job.schedule();
							}
						} catch (IOException e) {
							// TODO log
						}
					}
				}
			
				/* (non-Javadoc)
				 * @see org.eclipse.debug.internal.ui.model.viewers.IMementoManager#processReqeusts()
				 */
				public void processReqeusts() {
					stateProvider.encodeElement(fRequest);
				}
			
				/* (non-Javadoc)
				 * @see org.eclipse.debug.internal.ui.model.viewers.IMementoManager#addRequest(org.eclipse.debug.internal.ui.model.IElementMementoRequest)
				 */
				public synchronized void addRequest(IElementMementoRequest req) {
					fRequest = req;
				}
			
			};
			manager.addRequest(new ElementMementoRequest(manager, getPresentationContext(),
									delta.getElement(), inputMemento, delta));
			manager.processReqeusts();
		}
	}
	
	/**
	 * Restore selection/expansion based on items already in the tree
	 */
	abstract protected void doInitialRestore();
	
	/**
	 * Perform any restoration required for the given tree path.
	 * 
	 * @param path
	 */
	protected void doRestore(final TreePath path) {
		if (fPendingState == null) { 
			return;
		}
		IModelDeltaVisitor visitor = new IModelDeltaVisitor() {
			public boolean visit(IModelDelta delta, int depth) {
				if (delta.getParentDelta() == null) {
					return true;
				}
				Object element = delta.getElement();
				Object potentialMatch = path.getSegment(depth - 1);
				if (element instanceof IMemento) {
					IElementMementoProvider provider = getViewerStateAdapter(getViewer().getInput());
					if (provider != null) {
						provider.compareElement(
								new ElementCompareRequest(ModelContentProvider.this,
										potentialMatch, (IMemento) element, (ModelDelta)delta));
					}
				} else {
					if (element.equals(potentialMatch)) {
						// already processed - visit children
						return true;
					}
				}
				return false;
			}
		};
		fPendingState.accept(visitor);
	}

	/**
	 * Saves the viewer's state for the previous input.
	 * 
	 * @param oldInput
	 */
	protected void saveViewerState(Object input) {
		IElementMementoProvider stateProvider = getViewerStateAdapter(input);
		if (stateProvider != null) {
			// build a model delta representing expansion and selection state
			ModelDelta delta = new ModelDelta(input, IModelDelta.NO_CHANGE);
			buildViewerState(delta);
			if (delta.getChildDeltas().length > 0) {
				// encode delta with mementos in place of elements, in non-UI thread
				encodeDelta(delta, stateProvider);
			}
		}
	}

	/**
	 * Encodes delta elements into mementos using the given provider.
	 *  
	 * @param delta
	 * @param stateProvider
	 */
	protected void encodeDelta(final ModelDelta rootDelta, final IElementMementoProvider stateProvider) {
		final XMLMemento inputMemento = XMLMemento.createWriteRoot("VIEWER_INPUT_MEMENTO"); //$NON-NLS-1$
		final XMLMemento childrenMemento = XMLMemento.createWriteRoot("CHILDREN_MEMENTO"); //$NON-NLS-1$
		final IMementoManager manager = new IMementoManager() {
		
			private Set requests = new HashSet();
			private boolean abort = false; 
			
			/* (non-Javadoc)
			 * @see org.eclipse.debug.internal.ui.model.viewers.IMementoManager#requestComplete(org.eclipse.debug.internal.ui.model.IElementMementoRequest)
			 */
			public synchronized void requestComplete(IElementMementoRequest request) {
				if (!abort) {
					if (!request.isCanceled() && (request.getStatus() == null || request.getStatus().isOK())) {
						requests.remove(request);
						if (requests.isEmpty()) {
							XMLMemento keyMemento = (XMLMemento) rootDelta.getElement();
							StringWriter writer = new StringWriter();
							try {
								keyMemento.save(writer);
								fViewerStates.put(writer.toString(), rootDelta);
							} catch (IOException e) {
								// TODO log
							}
						}
					} else {
						abort = true;
						Iterator iterator = requests.iterator();
						while (iterator.hasNext()) {
							IElementMementoRequest req = (IElementMementoRequest) iterator.next();
							req.setCanceled(true);
						}
						requests.clear();
					}
				}
			}
		
			/* (non-Javadoc)
			 * @see org.eclipse.debug.internal.ui.model.viewers.IMementoManager#processReqeusts()
			 */
			public void processReqeusts() {
				IElementMementoRequest[] req = (IElementMementoRequest[]) requests.toArray(new IElementMementoRequest[requests.size()]);
				for (int i = 0; i < req.length; i++) {
					stateProvider.encodeElement(req[i]);
				}
			}
		
			/* (non-Javadoc)
			 * @see org.eclipse.debug.internal.ui.model.viewers.IMementoManager#addRequest(org.eclipse.debug.internal.ui.model.IElementMementoRequest)
			 */
			public synchronized void addRequest(IElementMementoRequest request) {
				requests.add(request);
			}
		
		};
		IModelDeltaVisitor visitor = new IModelDeltaVisitor() {
			public boolean visit(IModelDelta delta, int depth) {
				if (delta.getParentDelta() == null) {
					manager.addRequest(
						new ElementMementoRequest(manager, getPresentationContext(),
								delta.getElement(), inputMemento, (ModelDelta)delta));
				} else {
					manager.addRequest(
						new ElementMementoRequest(manager, getPresentationContext(),
								delta.getElement(), childrenMemento.createChild("CHILD_ELEMENT"), (ModelDelta)delta)); //$NON-NLS-1$
				}
				return true;
			}
		};
		rootDelta.accept(visitor);
		manager.processReqeusts();
	}

	/**
	 * Builds a delta with the given root delta for expansion/selection state.
	 * 
	 * @param delta root delta
	 */
	protected abstract void buildViewerState(ModelDelta delta);

	/**
	 * Uninstalls the model proxy installed for the given element, if any.
	 * 
	 * @param element
	 */
	protected synchronized void disposeModelProxy(Object element) {
		IModelProxy proxy = (IModelProxy) fModelProxies.remove(element);
		if (proxy != null) {
			proxy.dispose();
		}
	}

	/**
	 * Uninstalls each model proxy
	 */
	protected synchronized void disposeAllModelProxies() {
		Iterator updatePolicies = fModelProxies.values().iterator();
		while (updatePolicies.hasNext()) {
			IModelProxy proxy = (IModelProxy) updatePolicies.next();
			proxy.dispose();
		}
		fModelProxies.clear();
	}

	/**
	 * Installs the model proxy for the given element into this content provider
	 * if not already installed.
	 * 
	 * @param element
	 *            element to install an update policy for
	 */
	protected synchronized void installModelProxy(Object element) {
		if (!fModelProxies.containsKey(element)) {
			IModelProxyFactoryAdapter modelProxyFactory = getModelProxyFactoryAdapter(element);
			if (modelProxyFactory != null) {
				final IModelProxy proxy = modelProxyFactory.createModelProxy(
						element, getPresentationContext());
				if (proxy != null) {
					fModelProxies.put(element, proxy);
					Job job = new Job("Model Proxy installed notification job") {//$NON-NLS-1$
						protected IStatus run(IProgressMonitor monitor) {
							if (!monitor.isCanceled()) {
								proxy.init(getPresentationContext());
								Object[] mcls = fModelListeners.getListeners();
								for (int i = 0; i < mcls.length; i++) {
									proxy.addModelChangedListener((IModelChangedListener) mcls[i]);
								}
								proxy
										.addModelChangedListener(ModelContentProvider.this);
								proxy.installed();
							}
							return Status.OK_STATUS;
						}
					};
					job.setSystem(true);
					job.schedule();
				}
			}
		}
	}

	/**
	 * Returns the model proxy factory for the given element or
	 * <code>null</code> if none.
	 * 
	 * @param element
	 *            element to retrieve adapter for
	 * @return model proxy factory adapter or <code>null</code>
	 */
	protected IModelProxyFactoryAdapter getModelProxyFactoryAdapter(Object element) {
		IModelProxyFactoryAdapter adapter = null;
		if (element instanceof IModelProxyFactoryAdapter) {
			adapter = (IModelProxyFactoryAdapter) element;
		} else if (element instanceof IAdaptable) {
			IAdaptable adaptable = (IAdaptable) element;
			adapter = (IModelProxyFactoryAdapter) adaptable.getAdapter(IModelProxyFactoryAdapter.class);
		}
		return adapter;
	}
	
	/**
	 * Returns the viewer state adapter for the given element or
	 * <code>null</code> if none.
	 * 
	 * @param element
	 *            element to retrieve adapter for
	 * @return viewer state adapter or <code>null</code>
	 */
	protected IElementMementoProvider getViewerStateAdapter(Object element) {
		IElementMementoProvider adapter = null;
		if (element instanceof IElementMementoProvider) {
			adapter = (IElementMementoProvider) element;
		} else if (element instanceof IAdaptable) {
			IAdaptable adaptable = (IAdaptable) element;
			adapter = (IElementMementoProvider) adaptable.getAdapter(IElementMementoProvider.class);
		}
		return adapter;
	}	

	/**
	 * Returns the presentation context for this content provider.
	 * 
	 * @return presentation context
	 */
	protected abstract IPresentationContext getPresentationContext();

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.debug.internal.ui.viewers.provisional.IModelChangedListener#modelChanged(org.eclipse.debug.internal.ui.viewers.provisional.IModelDelta)
	 */
	public void modelChanged(final IModelDelta delta) {
		WorkbenchJob job = new WorkbenchJob("process model delta") { //$NON-NLS-1$
			public IStatus runInUIThread(IProgressMonitor monitor) {
				updateNodes(new IModelDelta[] { delta });
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.schedule();
		
	}

	protected void updateNodes(IModelDelta[] nodes) {
		for (int i = 0; i < nodes.length; i++) {
			IModelDelta node = nodes[i];
			int flags = node.getFlags();

			if ((flags & IModelDelta.ADDED) != 0) {
				handleAdd(node);
			}
			if ((flags & IModelDelta.REMOVED) != 0) {
				handleRemove(node);
			}
			if ((flags & IModelDelta.CONTENT) != 0) {
				handleContent(node);
			}
			if ((flags & IModelDelta.EXPAND) != 0) {
				handleExpand(node);
			}
			if ((flags & IModelDelta.SELECT) != 0) {
				handleSelect(node);
			}
			if ((flags & IModelDelta.STATE) != 0) {
				handleState(node);
			}
			if ((flags & IModelDelta.INSERTED) != 0) {
				handleInsert(node);
			}
			if ((flags & IModelDelta.REPLACED) != 0) {
				handleReplace(node);
			}
			if ((flags & IModelDelta.INSTALL) != 0) {
				handleInstall(node);
			}
			if ((flags & IModelDelta.UNINSTALL) != 0) {
				handleUninstall(node);
			}
			updateNodes(node.getChildDeltas());
		}
	}
	
    /**
     * Returns the content adapter for the given element or
     * <code>null</code> if none.
     * 
     * @param element
     *            element to retrieve adapter for
     * @return content adapter or <code>null</code>
     */
    protected IElementContentProvider getContentAdapter(Object element) {        
        IElementContentProvider adapter = null;
        if (element instanceof IElementContentProvider) {
            adapter = (IElementContentProvider) element;
        } else if (element instanceof IAdaptable) {
            IAdaptable adaptable = (IAdaptable) element;
            adapter = (IElementContentProvider) adaptable.getAdapter(IElementContentProvider.class);
        }
        return adapter;
    }		

	protected abstract void handleState(IModelDelta delta);

	protected abstract void handleSelect(IModelDelta delta);

	protected abstract void handleExpand(IModelDelta delta);

	protected abstract void handleContent(IModelDelta delta);

	protected abstract void handleRemove(IModelDelta delta);

	protected abstract void handleAdd(IModelDelta delta);

	protected abstract void handleInsert(IModelDelta delta);

	protected abstract void handleReplace(IModelDelta delta);
	
	protected void handleInstall(IModelDelta delta) {
		installModelProxy(delta.getElement());
	}
	
	protected void handleUninstall(IModelDelta delta) {
		disposeModelProxy(delta.getElement());
	}	

	/**
	 * Returns a tree path for the node, *not* including the root element.
	 * 
	 * @param node
	 *            model delta
	 * @return corresponding tree path
	 */
	protected TreePath getTreePath(IModelDelta node) {
		ArrayList list = new ArrayList();
		IModelDelta parentDelta = node.getParentDelta();
		while (parentDelta != null) {
			list.add(0, node.getElement());
			node = parentDelta;
			parentDelta = node.getParentDelta();
		}
		return new TreePath(list.toArray());
	}

	/**
	 * Returns the viewer this content provider is working for.
	 * 
	 * @return viewer
	 */
	protected Viewer getViewer() {
		return fViewer;
	}
	
	protected void handlePresentationFailure(IAsynchronousRequestMonitor request, IStatus status) {
		IWorkbenchPart part = getPresentationContext().getPart();
		if (part instanceof AbstractDebugView) {
			AbstractDebugView view = (AbstractDebugView) part;
			view.showMessage(status.getMessage());
		}
	}
	
	
	/**
	 * Translates and returns the given child index from the viewer coordinate
	 * space to the model coordinate space.
	 *  
	 * @param parentPath path to parent element
	 * @param index index of child element in viewer (filtered) space
	 * @return index of child element in model (raw) space
	 */
	public /* protected */ int viewToModelIndex(TreePath parentPath, int index) {
		return fTransform.viewToModelIndex(parentPath, index);
	}
	
	/**
	 * Translates and returns the given child count from the viewer coordinate
	 * space to the model coordinate space.
	 *  
	 * @param parentPath path to parent element
	 * @param count number of child elements in viewer (filtered) space
	 * @return number of child elements in model (raw) space
	 */
	public /* protected */ int viewToModelCount(TreePath parentPath, int count) {
		return fTransform.viewToModelCount(parentPath, count);
	}	
	
	/**
	 * Translates and returns the given child index from the model coordinate
	 * space to the viewer coordinate space.
	 *  
	 * @param parentPath path to parent element
	 * @param index index of child element in model (raw) space
	 * @return index of child element in viewer (filtered) space
	 */
	protected int modelToViewIndex(TreePath parentPath, int index) {
		return fTransform.modelToViewIndex(parentPath, index);
	}	
	
	/**
	 * Translates and returns the given child count from the model coordinate
	 * space to the viewer coordinate space.
	 *  
	 * @param parentPath path to parent element
	 * @param count child count element in model (raw) space
	 * @return child count in viewer (filtered) space
	 */
	protected int modelToViewChildCount(TreePath parentPath, int count) {
		return fTransform.modelToViewCount(parentPath, count);
	}	
	
	/**
	 * Notes that the child at the specified index of the given parent element
	 * has been filtered from the viewer. Returns whether the child at the given
	 * index was already filtered.
	 * 
	 * @param parentPath path to parent element
	 * @param index index of child element to be filtered
	 * @return whether the child was already filtered
	 */
	protected boolean addFilteredIndex(TreePath parentPath, int index) {
		return fTransform.addFilteredIndex(parentPath, index);
	}
	
	/**
	 * Returns whether the given element is filtered.
	 * 
	 * @param parentElementOrTreePath
	 *            the parent element or path
	 * @param element
	 *            the child element
	 * @return whether to filter the element
	 */
	protected boolean shouldFilter(Object parentElementOrTreePath, Object element) {
		ViewerFilter[] filters = ((StructuredViewer)fViewer).getFilters();
		if (filters.length > 0) {
			for (int j = 0; j < filters.length; j++) {
				if (!(filters[j].select(fViewer, parentElementOrTreePath, element))) {
					return true;
				}
			}
		}
		return false;
	}	
	
	/**
	 * Returns whether the given index of the specified parent was previously filtered.
	 * 
	 * @param parentPath
	 * @param index
	 * @return whether the element at the given index was filtered
	 */
	protected boolean isFiltered(TreePath parentPath, int index) {
		return fTransform.isFiltered(parentPath, index);
	}
	
	/**
	 * Notification that a structural refresh is occurring at the specified path
	 * 
	 * @param path
	 */
	protected void refreshingStructure(TreePath path) {
	}
	
	/**
	 * Notification the given element is being unmapped.
	 * 
	 * @param path
	 */
	protected void unmapPath(TreePath path) {
		//System.out.println("Unmap " + path.getLastSegment());
		fTransform.clear(path);
	}
	
	/**
	 * Return tree paths to the given element in the viewer or <code>null</code>
	 * 
	 * @param element
	 * @return tree paths or <code>null</code>
	 */
	protected TreePath[] getTreePaths(Object element) {
		if (fViewer instanceof TreeModelViewer) {
			TreeModelViewer tmv = (TreeModelViewer) fViewer;
			return tmv.getTreePaths(element);
		}
		return null;
	}

	/**
	 * Returns filtered children or <code>null</code>
	 * @param parent
	 * @return filtered children or <code>null</code>
	 */
	protected int[] getFilteredChildren(TreePath parent) {
		return fTransform.getFilteredChildren(parent);
	}
	
	protected void clearFilteredChild(TreePath parent, int modelIndex) {
		fTransform.clear(parent, modelIndex);
	}
	
	protected void clearFilters(TreePath parent) {
		fTransform.clear(parent);
	}

	/**
	 * @param delta
	 */
	void doRestore(final ModelDelta delta) {
		if (delta.getFlags() != IModelDelta.NO_CHANGE) {
			UIJob job = new UIJob("restore delta") { //$NON-NLS-1$
				public IStatus runInUIThread(IProgressMonitor monitor) {
					TreePath treePath = getTreePath(delta);
					TreeModelViewer viewer = (TreeModelViewer)getViewer();
					if ((delta.getFlags() & IModelDelta.EXPAND) != 0) {
						viewer.expandToLevel(treePath, 1);
					}
					if ((delta.getFlags() & IModelDelta.SELECT) != 0) {
						viewer.setSelection(new TreeSelection(treePath));
					}
					delta.setFlags(IModelDelta.NO_CHANGE);
					checkIfRestoreComplete();
					return Status.OK_STATUS;
				}
			};
			job.setSystem(true);
			job.schedule();
		}
	}
	
	protected void checkIfRestoreComplete() {
		CheckState state = new CheckState();
		fPendingState.accept(state);
		if (state.isComplete()) {
			fPendingState = null;
			//System.out.println("RESTORE COMPELTE");
		}
	}
	
	void addViewerUpdateListener(IViewerUpdateListener listener) {
		fUpdateListeners.add(listener);
	}
	
	void removeViewerUpdateListener(IViewerUpdateListener listener) {
		fUpdateListeners.remove(listener);
	}
	
	/**
	 * Notification an update request has started
	 * 
	 * @param update
	 */
	void updateStarted(IAsynchronousRequestMonitor update) {
		boolean begin = false;
		synchronized (this) {
			begin = fUpdateNestingCount == 0;
			fUpdateNestingCount++;
		}
		if (begin) {
			notifyUpdate(UPDATE_SEQUENCE_BEGINS, null);
		}
		notifyUpdate(UPDATE_BEGINS, update);
	}
	
	/**
	 * Notification an update request has completed
	 * 
	 * @param update
	 */
	void updateComplete(IAsynchronousRequestMonitor update) {
		boolean end = false;
		synchronized (this) {
			fUpdateNestingCount--;
			end = fUpdateNestingCount == 0;
		}
		notifyUpdate(UPDATE_COMPLETE, update);
		if (end) {
			notifyUpdate(UPDATE_SEQUENCE_COMPLETE, null);
		}
	}
	
	protected void notifyUpdate(final int type, final IAsynchronousRequestMonitor update) {
		if (!fUpdateListeners.isEmpty()) {
			Object[] listeners = fUpdateListeners.getListeners();
			for (int i = 0; i < listeners.length; i++) {
				final IViewerUpdateListener listener = (IViewerUpdateListener) listeners[i];
				SafeRunner.run(new ISafeRunnable() {
					public void run() throws Exception {
						switch (type) {
							case UPDATE_SEQUENCE_BEGINS:
								listener.viewerUpdatesBegin();
								break;
							case UPDATE_SEQUENCE_COMPLETE:
								listener.viewerUpdatesComplete();
								break;
							case UPDATE_BEGINS:
								listener.updateStarted(update);
								break;
							case UPDATE_COMPLETE:
								listener.updateComplete(update);
								break;
						}
					}
					public void handleException(Throwable exception) {
						DebugUIPlugin.log(exception);
					}
				});
			}
		}
	}	
	
	/**
	 * Registers the given listener for model delta notification.
	 * 
	 * @param listener model delta listener
	 */
	void addModelChangedListener(IModelChangedListener listener) {
		fModelListeners.add(listener); 
		Iterator proxies = fModelProxies.values().iterator();
		while (proxies.hasNext()) {
			IModelProxy proxy = (IModelProxy) proxies.next();
			proxy.addModelChangedListener(listener);
		}
	}
	
	/**
	 * Unregisters the given listener from model delta notification.
	 * 
	 * @param listener model delta listener
	 */
	void removeModelChangedListener(IModelChangedListener listener) {
		fModelListeners.remove(listener);
		Iterator proxies = fModelProxies.values().iterator();
		while (proxies.hasNext()) {
			IModelProxy proxy = (IModelProxy) proxies.next();
			proxy.removeModelChangedListener(listener);
		}
	}	
}
