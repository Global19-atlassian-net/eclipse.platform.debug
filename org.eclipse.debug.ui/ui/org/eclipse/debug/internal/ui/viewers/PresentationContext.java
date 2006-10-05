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

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.ui.IWorkbenchPart;

/**
 * Presentation context.
 * <p>
 * Clients may instantiate and subclass this class.
 * </p>
 * @since 3.2
 */
public class PresentationContext implements IPresentationContext {
    
    private IWorkbenchPart fPart;
    private String fId;
    private String[] fColumns;
    private ListenerList fListeners = new ListenerList();
    
    /**
     * Constructs a presentation context for the given part.
     * 
     * @param part workbench part
     */
    public PresentationContext(IWorkbenchPart part) {
        fPart = part;
        if (part != null) {
        	fId = part.getSite().getId();
        }
    }
    
    /**
     * Constructs a presentation context for the given id.
     * 
     * @param id presentation context id
     */
    public PresentationContext(String id) {
    	fId = id;
    }

    /* (non-Javadoc)
     * @see org.eclipse.debug.ui.viewers.IPresentationContext#getPart()
     */
    public IWorkbenchPart getPart() {
        return fPart;
    }

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext#getColumns()
	 */
	public String[] getColumns() {
		return fColumns;
	}
	
	/**
	 * Fires a property change event to all registered listeners
	 * 
	 * @param property property name
	 * @param oldValue old value or <code>null</code>
	 * @param newValue new value or <code>null</code>
	 */
	protected void firePropertyChange(String property, Object oldValue, Object newValue) {
		if (!fListeners.isEmpty()) {
			final PropertyChangeEvent event = new PropertyChangeEvent(this, property, oldValue, newValue);
			Object[] listeners = fListeners.getListeners();
			for (int i = 0; i < listeners.length; i++) {
				final IPropertyChangeListener listener = (IPropertyChangeListener) listeners[i];
				SafeRunner.run(new SafeRunnable() {
					public void run() throws Exception {
						listener.propertyChange(event);
					}
				});
			}
		}
	}
	
	/**
	 * Sets the visible column ids.
	 * 
	 * @param ids column identifiers
	 */
	public void setColumns(String[] ids) {
		String[] oldValue = fColumns;
		fColumns = ids;
		firePropertyChange(IPresentationContext.PROPERTY_COLUMNS, oldValue, ids);
	}
	
	/**
	 * Disposes this presentation context.
	 */
	protected void dispose() {
		fListeners.clear();
		fPart = null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext#addPropertyChangeListener(org.eclipse.jface.util.IPropertyChangeListener)
	 */
	public void addPropertyChangeListener(IPropertyChangeListener listener) {
		fListeners.add(listener);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext#removePropertyChangeListener(org.eclipse.jface.util.IPropertyChangeListener)
	 */
	public void removePropertyChangeListener(IPropertyChangeListener listener) {
		fListeners.remove(listener);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext#getId()
	 */
	public String getId() {
		return fId;
	}

}
