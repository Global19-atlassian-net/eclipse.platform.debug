/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.viewers;

/**
 * A model changed listener is notified of changes in a model.
 *
 * @since 3.2
 */
public interface IModelChangedListener {
	
	/**
	 * Notification a model has changed as described by the given delta.
	 * 
	 * @param delta model delta
	 */
	public void modelChanged(IModelDelta delta);

}
