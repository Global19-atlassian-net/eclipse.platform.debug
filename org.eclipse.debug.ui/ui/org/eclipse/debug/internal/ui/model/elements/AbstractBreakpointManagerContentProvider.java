/*****************************************************************
 * Copyright (c) 2009 Texas Instruments and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Patrick Chuong (Texas Instruments) - Initial API and implementation (Bug 238956)
 *****************************************************************/
package org.eclipse.debug.internal.ui.model.elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.IBreakpointsListener;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.breakpoints.provisional.IBreakpointOrganizer;
import org.eclipse.debug.internal.ui.breakpoints.provisional.IBreakpointUIConstants;
import org.eclipse.debug.internal.ui.elements.adapters.AbstractBreakpointManagerInput;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDelta;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IPresentationContext;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ModelDelta;
import org.eclipse.debug.internal.ui.viewers.provisional.AbstractModelProxy;
import org.eclipse.debug.internal.ui.views.breakpoints.BreakpointContainer;
import org.eclipse.debug.internal.ui.views.breakpoints.ElementComparator;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IStructuredSelection;

/**
 * This class provides breakpoint content for the breakpoint manager.
 * 
 * @since 3.6
 */
public abstract class AbstractBreakpointManagerContentProvider extends ElementContentProvider 
		implements IBreakpointsListener {
	
	/**
	 * Breakpoint input data. Contains all input specific data.
	 * 
	 * @since 3.6
	 */
	private class InputData {
		/**
		 * Breakpoint manager input
		 */
		private AbstractBreakpointManagerInput fInput;
		
		/**
		 * Model proxy of the input
		 */
		private List/*<AbstractModelProxy>*/ fProxies = new ArrayList(1);
		
		/**
		 * Element comparator, use to compare the ordering of elements for the model
		 * <br/> Note: We assume that the comparator does not change.  
		 */
		private ElementComparator fComparator;
		
		/**
		 * The breakpoint root container.<br/>
		 * Note: The final qualifier guarantees that fContainer will be 
		 * initialized before the class is accessed on other threads.
		 */
		final private BreakpointContainer fContainer;
		
		/**
		 * Constructor
		 *  
		 * @param input the breakpoint manager input
		 * @param proxy the model proxy 
		 * @param filter the debug context selection 
		 * @param comparator the element comparator 
		 */
		InputData(AbstractBreakpointManagerInput input) {
			fInput = input;
			fProxies = new ArrayList(1);
			fComparator = (ElementComparator)
			    input.getContext().getProperty(IBreakpointUIConstants.PROP_BREAKPOINTS_ELEMENT_COMPARATOR);
			
            IBreakpointOrganizer[] organizers = (IBreakpointOrganizer[])
                input.getContext().getProperty(IBreakpointUIConstants.PROP_BREAKPOINTS_ORGANIZERS);

            // Create the initial container.
            ModelDelta initialDelta = new ModelDelta(fInput, 0, IModelDelta.NO_CHANGE, -1);
            fContainer = createRootContainer(initialDelta, fInput, organizers, fBpManager);
		}
		
		synchronized void proxyInstalled(AbstractModelProxy proxy) {
		    fProxies.add(proxy);
		    
		    // Generate an install delta
		    
            ModelDelta rootDelta = new ModelDelta(fInput, 0, IModelDelta.NO_CHANGE, -1);
            buildInstallDelta(rootDelta, fContainer);
            proxy.fireModelChanged(rootDelta);
		}

		synchronized void proxyDisposed(AbstractModelProxy proxy) {
		    fProxies.remove(proxy);
		}

		/**
		 * Change the breakpoint organizers for the root container.
		 * 
		 * @param organizers the new organizers.
		 */
		synchronized void setOrganizers(IBreakpointOrganizer[] organizers) {
			// create a reference container, use for deleting elements and adding elements
			ModelDelta dummyDelta = new ModelDelta(null, IModelDelta.NO_CHANGE);				
			BreakpointContainer refContainer = createRootContainer(dummyDelta, fInput, organizers, fBpManager);

			// delete the removed elements
			ModelDelta deletedDelta = new ModelDelta(fInput, IModelDelta.NO_CHANGE);
			deleteRemovedElements(fContainer, refContainer, deletedDelta);
			fireModelChanged(fInput, deletedDelta, "setOrganizers - Delete removed elements"); //$NON-NLS-1$
			
			// adjust the old organizer with the reference organizer
			BreakpointContainer.copyOrganizers(fContainer, refContainer);
			
			// insert the added elements
			ModelDelta addedDelta = new ModelDelta(fInput, 0, IModelDelta.NO_CHANGE, -1);
			IBreakpoint newBreakpoint = insertAddedElements(fContainer, refContainer, addedDelta);
			addedDelta.setChildCount(fContainer.getChildren().length);
			
			// select the new breakpoint
			if (newBreakpoint != null)
				appendModelDeltaToElement(addedDelta, newBreakpoint, IModelDelta.SELECT);
			
			fireModelChanged(fInput, addedDelta, "setOrganizers - Insert added elements"); //$NON-NLS-1$
		}

		  /*
	     * (non-Javadoc)
	     * @see org.eclipse.debug.internal.ui.actions.breakpoints.IBreakpointFilterContentProvider#setFilterSelection(java.lang.Object, org.eclipse.debug.internal.ui.viewers.model.provisional.IPresentationContext, org.eclipse.jface.viewers.IStructuredSelection)
	     */
	    synchronized public void setFilterSelection(IStructuredSelection ss) {
            ModelDelta delta = new ModelDelta(fInput, IModelDelta.NO_CHANGE);
            
            Set existingBreakpoints = new HashSet(Arrays.asList(fContainer.getBreakpoints()));
            IBreakpoint[] allBreakpoints = fBpManager.getBreakpoints();
            
            for (int i = 0; i < allBreakpoints.length; ++i) {
                boolean supported = supportsBreakpoint(ss, allBreakpoints[i]);
                boolean contain = existingBreakpoints.contains(allBreakpoints[i]);                                  
                    
                if (supported) {
                    if (!contain)
                        fContainer.addBreakpoint(allBreakpoints[i], delta);
                } else {
                    if (contain)
                        fContainer.removeBreakpoint(allBreakpoints[i], delta);
                }
                
            }

            fireModelChanged(fInput, delta, "setFilterSelection"); //$NON-NLS-1$
	    }

	    /**
	     * Helper method to add breakpoints to the given input.
	     * 
	     * @param data the input to add the breakpoints
	     * @param breakpoints the breakpoints
	     */
	    synchronized void breakpointsAdded(IBreakpoint[] breakpoints) {
	        IBreakpoint[] filteredBreakpoints = filterBreakpoints(fInput, breakpoints);
	        ModelDelta delta = new ModelDelta(fInput, 0, IModelDelta.NO_CHANGE, -1);
	        for (int i = 0; i < filteredBreakpoints.length; ++i) {
	            fContainer.addBreakpoint(filteredBreakpoints[i], delta);
	        }
	        delta.setChildCount(fContainer.getChildren().length);
	        
	        // select the breakpoint
	        if (filteredBreakpoints.length > 0) {
	            appendModelDeltaToElement(delta, filteredBreakpoints[0], IModelDelta.SELECT);
	        }
	        
	        fireModelChanged(fInput, delta, "breakpointsAddedInput"); //$NON-NLS-1$
	    }

	    /**
	     * Helper method to remove breakpoints from a given input.
	     * 
	     * @param data the input to add the breakpoints
	     * @param breakpoints the breakpoints
	     */
	    synchronized void breakpointsRemoved(IBreakpoint[] breakpoints) {
	        IBreakpoint[] filteredBreakpoints = filterBreakpoints(fInput, breakpoints);
	        ModelDelta delta = new ModelDelta(fInput, IModelDelta.NO_CHANGE);
	        for (int i = 0; i < filteredBreakpoints.length; ++i) {
	            fContainer.removeBreakpoint(filteredBreakpoints[i], delta);
	        }
	        fireModelChanged(fInput, delta, "breakpointsRemovedInput"); //$NON-NLS-1$
	    }
	    
	    synchronized void breakpointsChanged(IBreakpoint[] breakpoints) {
            IBreakpoint[] filteredBreakpoints = filterBreakpoints(fInput, breakpoints);
            
            // If the change caused a breakpoint to be added (installed) or remove (un-installed) update accordingly.
            List removed = new ArrayList();
            List added = new ArrayList();
            List filteredAsList = Arrays.asList(filteredBreakpoints);
            for (int i = 0; i < breakpoints.length; i++) {
                IBreakpoint bp = breakpoints[i];
                boolean oldContainedBp = fContainer.contains(bp);
                boolean newContained = filteredAsList.contains(bp);
                if (oldContainedBp && !newContained) {
                    removed.add(bp);
                } else if (!oldContainedBp && newContained) {
                    added.add(bp);
                }                   
            }
            if (!added.isEmpty()) {
                breakpointsAdded((IBreakpoint[]) added.toArray(new IBreakpoint[added.size()]));
            }
            if (!removed.isEmpty()) {
                breakpointsRemoved((IBreakpoint[]) removed.toArray(new IBreakpoint[removed.size()]));
            }                       
            
            ModelDelta delta = new ModelDelta(fInput, IModelDelta.NO_CHANGE);
            for (int i = 0; i < filteredBreakpoints.length; ++i)
                appendModelDelta(fContainer, delta, IModelDelta.STATE, filteredBreakpoints[i]);
            fireModelChanged(fInput, delta, "breakpointsChanged");        //$NON-NLS-1$
	    }

	    
		private void buildInstallDelta(ModelDelta delta, BreakpointContainer container) {
            Object[] children = container.getChildren();
            delta.setChildCount(children.length);
            for (int i = 0; i < children.length; i++) {
                ModelDelta childDelta = delta.addNode(children[i], i, IModelDelta.NO_CHANGE);
                if (children[i] instanceof BreakpointContainer) {
                    buildInstallDelta(childDelta, (BreakpointContainer)children[i]);
                } else if (children[i] instanceof IBreakpoint) {
                    childDelta.setFlags(IModelDelta.INSTALL);
                }
            }
        }
        
		/**
		 * Insert elements from the reference container to an existing container.
		 * 
		 * @param container the existing  container to insert the new elements.
		 * @param refContainer the reference container to compare elements that are added.
		 * @param containerDelta the delta of the existing container.
		 */
		private IBreakpoint insertAddedElements(BreakpointContainer container, BreakpointContainer refContainer, ModelDelta containerDelta) {
			IBreakpoint newBreakpoint = null;
			
			Object[] children = container.getChildren();
			Object[] refChildren = refContainer.getChildren();
			

			for (int i = 0; i < refChildren.length; ++i) {
				Object element = getElement(children, refChildren[i]);

				// if a child of refContainer doesn't exist in container, than insert it to container
				//		- if the reference child is a container, than copy the reference child container to container
				//		- otherwise (Breakpoint), add the breakpoint to container
				if (element == null) {
					if (refChildren[i] instanceof BreakpointContainer) {
						BreakpointContainer.addChildContainer(container, (BreakpointContainer) refChildren[i], containerDelta);
					} else {
						BreakpointContainer.addBreakpoint(container, (IBreakpoint) refChildren[i], containerDelta);
						if (newBreakpoint == null)
							newBreakpoint = (IBreakpoint) refChildren[i];
					}
					
				// if a child exist in container, than recursively search into container. And also update the organizer of
				// of container to the one in the refContainer's child.
				} else if (element instanceof BreakpointContainer) {
					int index = Arrays.asList(children).indexOf(element);  
					ModelDelta childDelta = containerDelta.addNode(element, index, IModelDelta.STATE, -1);
					BreakpointContainer.copyOrganizers((BreakpointContainer) element, (BreakpointContainer) refChildren[i]);
					newBreakpoint = insertAddedElements((BreakpointContainer) element, (BreakpointContainer) refChildren[i], childDelta);
					childDelta.setChildCount(((BreakpointContainer) element).getChildren().length);
				}
			}
			
			return newBreakpoint;
		}
		
		/**
		 * Delete elements from existing container that doesn't exist in the reference container.
		 * 
		 * @param container the existing container to delete the removed elements.
		 * @param refContainer the reference container to compare elements that are removed.
		 * @param containerDelta the delta of the existing container.
		 */
		private void deleteRemovedElements(BreakpointContainer container, BreakpointContainer refContainer, ModelDelta containerDelta) {
			Object[] children = container.getChildren();
			Object[] refChildren = refContainer.getChildren();
			
			// if a child of container doesn't exist in refContainer, than remove it from container
			for (int i = 0; i < children.length; ++i) {
				Object element = getElement(refChildren, children[i]);
				
				if (element == null) {
					if (children[i] instanceof BreakpointContainer) {
						BreakpointContainer.removeAll((BreakpointContainer) children[i], containerDelta);
					} else {
						BreakpointContainer.removeBreakpoint(container, (IBreakpoint) children[i], containerDelta);
					}
				} else if (element instanceof BreakpointContainer){

					ModelDelta childDelta = containerDelta.addNode(children[i], IModelDelta.STATE);						
					deleteRemovedElements((BreakpointContainer) children[i], (BreakpointContainer) element, childDelta);
				}
			}
		}
		
		/**
		 * Get the element that is in the collection.
		 * 
		 * @param collection the collection of elements.
		 * @param element the element to search.
		 * @return if element exist in collection, than it is returned, otherwise <code>null</code> is returned.
		 * @see insertAddedElements
		 * @see deleteRemovedElements
		 */
		private Object getElement(Object[] collection, Object element) {
			for (int i = 0; i < collection.length; ++i)
				if (collection[i] instanceof BreakpointContainer && element instanceof BreakpointContainer) {				
					if (collection[i].equals(element))
						return collection[i];
				} else {
					if (collection[i].equals(element))
						return collection[i];
				}
			return null;
		}
		
		/**
		 * Create a root container.
		 * 
		 * @param rootDelta the root delta.
		 * @param input the view input.
		 * @param organizers the breakpoint organizers.
		 * @param oldContainer the old container, use to determine whether a new breakpoint should be expanded.
		 * @param the breakpoint manager.
		 */
		private BreakpointContainer createRootContainer(ModelDelta rootDelta, AbstractBreakpointManagerInput input, 
				IBreakpointOrganizer[] organizers, IBreakpointManager bpManager) {
			
			IBreakpoint[] breakpoints = filterBreakpoints(input, bpManager.getBreakpoints());			
			BreakpointContainer container = new BreakpointContainer(organizers, fComparator);
			container.initDefaultContainers(rootDelta);
			
			for (int i = 0; i < breakpoints.length; ++i) {
				container.addBreakpoint(breakpoints[i], rootDelta);				
			}
			
			return container;
		}		
	}
	
	private class InputDataMap extends LinkedHashMap {
        private static final long serialVersionUID = 1L;

        public InputDataMap() {
	        super(1, (float)0.75, true);
        }
	    
	    protected boolean removeEldestEntry(java.util.Map.Entry arg0) {
	        InputData data = (InputData)arg0.getValue();
	        return size() > getMaxInputsCache() && data.fProxies.isEmpty(); 
	    }
	}
	
	private class PresentationContextListener implements IPropertyChangeListener {
	    final private IPresentationContext fContext;
	    
	    PresentationContextListener(IPresentationContext context) {
	        fContext = context;
	        fContext.addPropertyChangeListener(this);
	    }
	    
	    public void propertyChange(PropertyChangeEvent event) {
	        contextPropertyChanged(fContext, event);
	    }
	}
	
	// debug flags
	public static boolean DEBUG_BREAKPOINT_DELTAS = false;
	
	static {
		DEBUG_BREAKPOINT_DELTAS = DebugUIPlugin.DEBUG && "true".equals( 					//$NON-NLS-1$
		 Platform.getDebugOption("org.eclipse.debug.ui/debug/viewers/breakpointDeltas")); 	//$NON-NLS-1$
	} 
		
	/**
	 * A map of input to info data cache
	 */
	final private Map fInputToData = Collections.synchronizedMap(new InputDataMap());
	
	/**
	 * A map of presetnation context listeners.
	 */
	final private Map fContextListeners = Collections.synchronizedMap(new HashMap());
	
	/**
	 * The breakpoint manager.
	 */
	final private IBreakpointManager fBpManager;
	
	/**
	 * Constructor.
	 */
	protected AbstractBreakpointManagerContentProvider() {
		fBpManager = DebugPlugin.getDefault().getBreakpointManager();	
	}
	
	/**
	 * Sub-classes is required to implements this method to filter the breakpoints.
	 * 
	 * @param input the breakpoint manager input.
	 * @param breakpoints the list of breakpoint to filter.
	 * @return the filtered list of breakpoint based on the input.
	 */
	protected abstract IBreakpoint[] filterBreakpoints(AbstractBreakpointManagerInput input, IBreakpoint[] breakpoints);
	
	/**
	 * Sub-classes is required to implements this method, to determine whether the breakpoint is supported by the selection.
	 * 
	 * @param ss the selection of the debug elements.
	 * @param breakpoint the breakpoint.
	 * @return true if supported.
	 */
	protected abstract boolean supportsBreakpoint(IStructuredSelection ss, IBreakpoint breakpoint);
	
	/**
	 * Maximum number of breakpoint manager input objects that this provider 
	 * will cache data for.  This method is called once upon class creation 
	 * when setting up the data cache.  Sub-classes may override to provide
	 * a custom setting.
	 * 
	 * @return Maximum data cache size
	 */
	protected int getMaxInputsCache() {
	    return 2;
	}
	
    /**
     * Handles the propety changed events in presentation contexts.
     * Sub-classes may override to perform additional handling.
     * 
     * @param context Presetnation context that was disposed.
     */
	protected void contextPropertyChanged(IPresentationContext context, PropertyChangeEvent event) {
	    if (IBreakpointUIConstants.PROP_BREAKPOINTS_ORGANIZERS.equals(event.getProperty())) {
	        IBreakpointOrganizer[] organizers = (IBreakpointOrganizer[])event.getNewValue();
	        InputData[] contextDatas = getContextInputDatas(context);
	        for (int i = 0; i < contextDatas.length; i++) {
	            contextDatas[i].setOrganizers(organizers);
	        }
	    }
	    else if (IBreakpointUIConstants.PROP_BREAKPOINTS_FILTER_SELECTION.equals(event.getProperty())) {
            IStructuredSelection selection = (IStructuredSelection)event.getNewValue();
            InputData[] contextDatas = getContextInputDatas(context);
            for (int i = 0; i < contextDatas.length; i++) {
                contextDatas[i].setFilterSelection(selection);
            }
        }
	}
	
	private InputData[] getContextInputDatas(IPresentationContext context) {
	    List list = new ArrayList(fInputToData.size());
	    synchronized (fInputToData) {
            for (Iterator itr = fInputToData.values().iterator(); itr.hasNext();) {
                InputData data = (InputData)itr.next();
                if (context.equals( data.fInput.getContext() )) {
                    list.add(data);
                }
            }
	    }
	    return (InputData[]) list.toArray(new InputData[list.size()]);
	}
	
	/**
	 * Handles the event when a presentation context is dispoed.
	 * Sub-classes may override to perform additional cleanup.
	 * 
	 * @param context Presetnation context that was disposed.
	 */
	protected void contextDisposed(IPresentationContext context) {
        synchronized (fInputToData) {
            for (Iterator itr = fInputToData.entrySet().iterator(); itr.hasNext();) {
                Map.Entry entry = (Map.Entry)itr.next();
                IPresentationContext entryContext = ((AbstractBreakpointManagerInput)entry.getKey()).getContext();
                if (context.equals(entryContext)) {
                    itr.remove();
                }
            }
        }
        
        // Remove the context listener.
        PresentationContextListener listener = (PresentationContextListener)fContextListeners.remove(context);
        if (listener != null) {
            context.removePropertyChangeListener(listener);
        }
	}
	
	/**
	 * Register the breakpoint manager input with this content provider.
	 * 
	 * @param input the breakpoint manager input to register.
	 * @param proxy the model proxy of the input.
	 * @param organizers the breakpoint organizer, can be <code>null</code>.
	 * @param selectionFilter the selection filter, can be <code>null</code>.
	 * @param comparator the element comparator.
	 */
	public void registerModelProxy(AbstractBreakpointManagerInput input, AbstractModelProxy proxy) {
		if (fInputToData.isEmpty()) {
			fBpManager.addBreakpointListener(this);
		}
		
		getInputData(input).proxyInstalled(proxy);
	}
	
	/**
	 * Unregister the breakpoint manager input with this content provider.
	 * 
	 * @param input the breakpoint manager input to unregister.
	 */
	public void unregisterModelProxy(AbstractBreakpointManagerInput input, AbstractModelProxy proxy) {
        getInputData(input).proxyDisposed(proxy);
		
		if (fInputToData.isEmpty()) {
			fBpManager.removeBreakpointListener(this);
		}
	}	
	
	private InputData getInputData(AbstractBreakpointManagerInput input) {
	    InputData data = null;
	    synchronized (fInputToData) {
    	    data = (InputData)fInputToData.get(input); 
    	    if (data == null) {
    	        data = new InputData(input);
    	        fInputToData.put(input, data);
    	    }
	    }
	    
	    // Also make sure that we're listening to the presentation context properties
	    synchronized (fContextListeners) {
	        IPresentationContext context = input.getContext();
	        if ( !Boolean.TRUE.equals(context.getProperty(IPresentationContext.PROPERTY_DISPOSED)) &&
	             !fContextListeners.containsKey(context) )
	        {
	            fContextListeners.put(context, new PresentationContextListener(context));
	        }
	    }
	    
	    return data;
	}
	
	/**
	 * Returns the model proxy for the input.
	 * 
	 * @param input the input.
	 * @return the model proxy.
	 */
	private List getModelProxies(AbstractBreakpointManagerInput input) {
		InputData data = getInputData(input);
		return data != null ? data.fProxies : null;
	}	
	
	/**
	 * Returns the selection filter for the input.
	 * 
	 * @param input the selection.
	 */
	protected IStructuredSelection getSelectionFilter(Object input) {
	    if (input instanceof AbstractBreakpointManagerInput) {
	        return (IStructuredSelection) ((AbstractBreakpointManagerInput)input).getContext().getProperty(IBreakpointUIConstants.PROP_BREAKPOINTS_FILTER_SELECTION);
	    }
	    return null;
	}
	
	/**
	 * Fire model change event for the input.
	 * 
	 * @param input the input.
	 * @param delta the model delta.
	 * @param debugReason the debug string.
	 */
	protected void fireModelChanged(AbstractBreakpointManagerInput input, IModelDelta delta, String debugReason) {
	    List proxies = getModelProxies(input);
	    
        if (DEBUG_BREAKPOINT_DELTAS && proxies.size() > 0) {
            System.out.println("FIRE BREAKPOINT DELTA (" + debugReason + ")\n" + delta.toString()); //$NON-NLS-1$ //$NON-NLS-2$
        }

	    for (int i = 0; i < proxies.size(); i++) {
	        ((AbstractModelProxy)proxies.get(i)).fireModelChanged(delta);
		}		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.model.elements.ElementContentProvider#supportsContextId(java.lang.String)
	 */
	protected boolean supportsContextId(String id) {
		return id.equals(IDebugUIConstants.ID_BREAKPOINT_VIEW);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.model.elements.ElementContentProvider#getChildCount(java.lang.Object, org.eclipse.debug.internal.ui.viewers.model.provisional.IPresentationContext, org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdate)
	 */
	protected int getChildCount(Object element, IPresentationContext context, IViewerUpdate monitor) throws CoreException {
	    Object input = monitor.getViewerInput();
	    if (input instanceof AbstractBreakpointManagerInput) {
	        AbstractBreakpointManagerInput bpManagerInput = (AbstractBreakpointManagerInput)input;
    		return getInputData(bpManagerInput).fContainer.getChildren().length;
	    }		
		return 0;		
	}	

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.model.elements.ElementContentProvider#getChildren(java.lang.Object, int, int, org.eclipse.debug.internal.ui.viewers.model.provisional.IPresentationContext, org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdate)
	 */
	protected Object[] getChildren(Object parent, int index, int length, IPresentationContext context, IViewerUpdate monitor) throws CoreException {
        Object input = monitor.getViewerInput();
        if (input instanceof AbstractBreakpointManagerInput) {
            AbstractBreakpointManagerInput bpManagerInput = (AbstractBreakpointManagerInput)input;
            Object[] children =  getInputData(bpManagerInput).fContainer.getChildren();
            return getElements(children, index, length);
        }       
				
		return EMPTY;
	}	

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.debug.core.IBreakpointsListener#breakpointsAdded(org.eclipse.debug.core.model.IBreakpoint[])
	 */
	public void breakpointsAdded(IBreakpoint[] breakpoints) {
	    InputData[] datas = (InputData[])fInputToData.values().toArray(new InputData[0]);
	    for (int i = 0; i < datas.length; i++) {
			datas[i].breakpointsAdded(breakpoints);
		}				
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.debug.core.IBreakpointsListener#breakpointsRemoved(org.eclipse.debug.core.model.IBreakpoint[], org.eclipse.core.resources.IMarkerDelta[])
	 */
	public void breakpointsRemoved(final IBreakpoint[] breakpoints, IMarkerDelta[] deltas) {
        InputData[] datas = (InputData[])fInputToData.values().toArray(new InputData[0]);
        for (int i = 0; i < datas.length; i++) {
            datas[i].breakpointsRemoved(breakpoints);
        }               
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.debug.core.IBreakpointsListener#breakpointsChanged(org.eclipse.debug.core.model.IBreakpoint[], org.eclipse.core.resources.IMarkerDelta[])
	 */
	public void breakpointsChanged(final IBreakpoint[] breakpoints, IMarkerDelta[] deltas) {
        InputData[] datas = (InputData[])fInputToData.values().toArray(new InputData[0]);
        for (int i = 0; i < datas.length; i++) {
            datas[i].breakpointsChanged(breakpoints);
        }               
	}	
	/**
	 * Appends the model delta flags to child containers that contains the breakpoint.
	 * 
	 * @param parent the parent container.
	 * @param parentDelta the parent model delta.
	 * @param flags the model delta flags.
	 * @param breakpoint the breakpoint to search in the children containers.
	 */
	private void appendModelDelta(BreakpointContainer parent, ModelDelta parentDelta, int flags, IBreakpoint breakpoint) {
		BreakpointContainer[] containers = parent.getContainers();
		
		if (parent.contains(breakpoint)) {
			if ((containers.length != 0)) {
				for (int i = 0; i < containers.length; ++i) {
					ModelDelta nodeDelta = parentDelta.addNode(containers[i], flags);
					appendModelDelta(containers[i], nodeDelta, flags, breakpoint);
				}			
			} else {
				parentDelta.addNode(breakpoint, flags);
			}
		}			
	}
	
	/**
	 * Appends the model delta to the first found element in the model delta tree.
	 * 
	 * @param parentDelta the parent delta
	 * @param element the element to search
	 * @param flags the delta flags
	 */
	private void appendModelDeltaToElement(IModelDelta parentDelta, Object element, int flags) {
		if (element.equals(parentDelta.getElement())) {
			((ModelDelta) parentDelta).setFlags(parentDelta.getFlags() | flags);
			return;
		}
		
		IModelDelta[] childDeltas = parentDelta.getChildDeltas();
		for (int i = 0; i < childDeltas.length; ++i) {
			if (element.equals(childDeltas[i].getElement())) {
				((ModelDelta) childDeltas[i]).setFlags(childDeltas[i].getFlags() | flags);
				return;
			}
			
			appendModelDeltaToElement(childDeltas[i], element, flags);
		}
	}
}
