/*******************************************************************************
 * Copyright (c) 2011 Wind River Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.viewers.model.provisional;

import org.eclipse.debug.internal.ui.viewers.provisional.AbstractModelProxy;
import org.eclipse.jface.viewers.Viewer;

/**
 * Extension to the model proxy interface which allows the proxy to be initialized
 * on the viewer's Display thread
 * <p>
 * Clients may implement this interface. Implementations of this interface
 * must subclass {@link AbstractModelProxy}.
 * </p>
 * @since 3.8
 */
public interface IModelProxy2 extends IModelProxy {
    
    /**
     * Initialize model proxy with given tree model viewer.  This method is 
     * called on the viewer's Display thread and is guaranteed to be called 
     * before the dispose() method is called on the same proxy.  The default 
     * implementation of this method calls {@link #init(IPresentationContext)} 
     * and {@link #installed(Viewer)} asynchornously and not in the Display 
     * thread.
     * <p>
     * This method is called by the asynchronous viewer framework and should 
     * not be called by clients.
     * </p>
     * @param viewer Viewer that is installing this model proxy.
     * 
     */
    public void initialize(ITreeModelViewer viewer);

}